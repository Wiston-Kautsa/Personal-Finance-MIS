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

public class OpenExchangeRateApiProvider implements ExchangeRateProvider {
    private final HttpClient client;
    private final URI baseUri;
    private final FxConfig config;

    public OpenExchangeRateApiProvider(FxConfig config) {
        this.config = config;
        this.baseUri = URI.create(cleanBaseUrl(config.baseUrl(), ExchangeRateProviderType.EXCHANGE_RATE_API_OPEN.defaultBaseUrl()));
        this.client = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Map<String, ExchangeRateQuote> fetchLatestRates(String baseCurrency) {
        String base = ExchangeRateQuote.normalizeCurrency(baseCurrency);
        String body = send("/latest/" + encode(base));
        validateSuccess(body);
        String responseBase = ProviderJson.stringValue(body, "base_code");
        if (!responseBase.isBlank() && !base.equalsIgnoreCase(responseBase)) {
            throw new ExchangeRateProviderException("Provider returned a different base currency.");
        }
        Instant retrievedAt = ProviderJson.unixInstant(body, "time_last_update_unix");
        LocalDate date = retrievedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        Map<String, ExchangeRateQuote> quotes = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : ProviderJson.ratesMap(body).entrySet()) {
            quotes.put(entry.getKey(), quote(base, entry.getKey(), entry.getValue(), date, retrievedAt));
        }
        return quotes;
    }

    @Override
    public ExchangeRateQuote fetchRate(String baseCurrency, String targetCurrency) {
        String base = ExchangeRateQuote.normalizeCurrency(baseCurrency);
        String target = ExchangeRateQuote.normalizeCurrency(targetCurrency);
        if (base.equals(target)) {
            return ExchangeRateQuote.sameCurrency(base);
        }
        ExchangeRateQuote quote = fetchLatestRates(base).get(target);
        if (quote == null) {
            throw new ExchangeRateProviderException("Provider response did not include " + target + ".");
        }
        return quote;
    }

    @Override
    public ExchangeRateQuote fetchHistoricalRate(String baseCurrency, String targetCurrency, LocalDate date) {
        throw new ExchangeRateProviderException("ExchangeRate-API open endpoint does not provide historical rates without an API key.");
    }

    @Override
    public String providerName() {
        return ExchangeRateProviderType.EXCHANGE_RATE_API_OPEN.displayName();
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    private ExchangeRateQuote quote(String base, String quote, BigDecimal rate, LocalDate date, Instant retrievedAt) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ExchangeRateProviderException("Provider returned an invalid exchange rate.");
        }
        return new ExchangeRateQuote(
                base,
                quote,
                rate,
                date,
                retrievedAt,
                providerName(),
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                "Attribution required: https://www.exchangerate-api.com"
        );
    }

    private void validateSuccess(String body) {
        String result = ProviderJson.stringValue(body, "result");
        if (!"success".equalsIgnoreCase(result)) {
            throw new ExchangeRateProviderException("Provider did not return a successful result.");
        }
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
