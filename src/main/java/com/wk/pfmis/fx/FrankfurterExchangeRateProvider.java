package com.wk.pfmis.fx;

import com.wk.pfmis.config.FxConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {
    private final HttpClient client;
    private final URI baseUri;
    private final FxConfig config;

    public FrankfurterExchangeRateProvider(FxConfig config) {
        this.config = config;
        this.baseUri = URI.create(cleanBaseUrl(config.baseUrl(), ExchangeRateProviderType.FRANKFURTER.defaultBaseUrl()));
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public FrankfurterExchangeRateProvider(HttpClient client, URI baseUri, FxConfig config) {
        this.client = client;
        this.baseUri = baseUri;
        this.config = config;
    }

    @Override
    public Map<String, ExchangeRateQuote> fetchLatestRates(String baseCurrency) {
        String base = ExchangeRateQuote.normalizeCurrency(baseCurrency);
        String body = send("/rates?base=" + encode(base));
        LocalDate date = ProviderJson.localDateValue(body, "date");
        String responseBase = ProviderJson.stringValue(body, "base");
        if (!responseBase.isBlank() && !base.equalsIgnoreCase(responseBase)) {
            throw new ExchangeRateProviderException("Provider returned a different base currency.");
        }
        Map<String, BigDecimal> rates = ProviderJson.ratesMap(body);
        Map<String, ExchangeRateQuote> quotes = new LinkedHashMap<>();
        Instant retrievedAt = Instant.now();
        for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
            quotes.put(entry.getKey(), quote(base, entry.getKey(), entry.getValue(), date, retrievedAt));
        }
        return quotes;
    }

    @Override
    public ExchangeRateQuote fetchRate(String baseCurrency, String targetCurrency) {
        String base = ExchangeRateQuote.normalizeCurrency(baseCurrency);
        String quote = ExchangeRateQuote.normalizeCurrency(targetCurrency);
        if (base.equals(quote)) {
            return ExchangeRateQuote.sameCurrency(base);
        }
        String body = send("/rate/" + encode(base) + "/" + encode(quote));
        return parseRateBody(body, base, quote);
    }

    @Override
    public ExchangeRateQuote fetchHistoricalRate(String baseCurrency, String targetCurrency, LocalDate date) {
        String base = ExchangeRateQuote.normalizeCurrency(baseCurrency);
        String quote = ExchangeRateQuote.normalizeCurrency(targetCurrency);
        if (base.equals(quote)) {
            return ExchangeRateQuote.sameCurrency(base);
        }
        if (date == null) {
            throw new IllegalArgumentException("Historical rate date is required.");
        }
        String body = send("/rate/" + encode(base) + "/" + encode(quote) + "?date=" + encode(date.toString()));
        ExchangeRateQuote response = parseRateBody(body, base, quote);
        return new ExchangeRateQuote(
                response.fromCurrency(),
                response.toCurrency(),
                response.rate(),
                response.effectiveDate(),
                response.retrievedAt(),
                response.providerName(),
                ExchangeRateSource.HISTORICAL,
                "HISTORICAL",
                false,
                false,
                response.notes()
        );
    }

    @Override
    public String providerName() {
        return ExchangeRateProviderType.FRANKFURTER.displayName();
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    private ExchangeRateQuote parseRateBody(String body, String expectedBase, String expectedQuote) {
        String base = ProviderJson.stringValue(body, "base");
        String quote = ProviderJson.stringValue(body, "quote");
        if (!base.isBlank() && !expectedBase.equalsIgnoreCase(base)) {
            throw new ExchangeRateProviderException("Provider returned a different base currency.");
        }
        if (!quote.isBlank() && !expectedQuote.equalsIgnoreCase(quote)) {
            throw new ExchangeRateProviderException("Provider returned a different quote currency.");
        }
        return quote(expectedBase, expectedQuote, ProviderJson.numberValue(body, "rate"), ProviderJson.localDateValue(body, "date"), Instant.now());
    }

    private ExchangeRateQuote quote(String base, String quote, BigDecimal rate, LocalDate date, Instant retrievedAt) {
        return new ExchangeRateQuote(
                base,
                quote,
                validateRate(rate),
                date,
                retrievedAt,
                providerName(),
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                ""
        );
    }

    private BigDecimal validateRate(BigDecimal rate) {
        if (!config.validateResponses()) {
            return rate;
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ExchangeRateProviderException("Provider returned an invalid exchange rate.");
        }
        return rate;
    }

    private String send(String pathAndQuery) {
        ExchangeRateProviderException lastFailure = null;
        int attempts = Math.max(1, config.maxRetries() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(resolve(pathAndQuery))
                        .timeout(config.requestTimeout())
                        .header("Accept", "application/json")
                        .header("User-Agent", config.userAgent())
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ExchangeRateProviderException("Provider returned HTTP " + response.statusCode() + ".");
                }
                String body = response.body();
                if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
                    throw new ExchangeRateProviderException("Provider returned an invalid JSON response.");
                }
                return body;
            } catch (IOException exception) {
                lastFailure = new ExchangeRateProviderException("Exchange-rate provider could not be reached.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExchangeRateProviderException("Exchange-rate request was interrupted.", exception);
            } catch (RuntimeException exception) {
                lastFailure = exception instanceof ExchangeRateProviderException providerException
                        ? providerException
                        : new ExchangeRateProviderException("Exchange-rate provider response could not be used.", exception);
            }
            if (attempt < attempts && !config.retryDelay().isZero()) {
                try {
                    Thread.sleep(config.retryDelay().toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ExchangeRateProviderException("Exchange-rate retry was interrupted.", exception);
                }
            }
        }
        throw lastFailure == null ? new ExchangeRateProviderException("Exchange-rate provider failed.") : lastFailure;
    }

    private URI resolve(String pathAndQuery) {
        String base = baseUri.toString();
        if (base.endsWith("/") && pathAndQuery.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + pathAndQuery);
        }
        if (!base.endsWith("/") && !pathAndQuery.startsWith("/")) {
            return URI.create(base + "/" + pathAndQuery);
        }
        return URI.create(base + pathAndQuery);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String cleanBaseUrl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
