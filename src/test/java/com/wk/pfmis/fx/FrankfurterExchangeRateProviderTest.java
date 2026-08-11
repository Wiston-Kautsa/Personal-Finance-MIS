package com.wk.pfmis.fx;

import com.wk.pfmis.config.FxConfig;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrankfurterExchangeRateProviderTest {
    @Test
    void parsesSuccessfulPairRate() {
        FakeHttpClient client = FakeHttpClient.responding(200, "{\"date\":\"2026-08-11\",\"base\":\"USD\",\"quote\":\"MWK\",\"rate\":1737.36}");
        FrankfurterExchangeRateProvider provider = new FrankfurterExchangeRateProvider(client, URI.create("https://example.test/v2"), config());

        ExchangeRateQuote quote = provider.fetchRate("USD", "MWK");

        assertEquals("USD", quote.fromCurrency());
        assertEquals("MWK", quote.toCurrency());
        assertEquals(0, new BigDecimal("1737.36").compareTo(quote.rate()));
        assertEquals(LocalDate.of(2026, 8, 11), quote.effectiveDate());
        assertEquals(ExchangeRateSource.ONLINE, quote.source());
        assertTrue(client.lastUri().toString().endsWith("/rate/USD/MWK"));
    }

    @Test
    void historicalRateUsesDateParameter() {
        FakeHttpClient client = FakeHttpClient.responding(200, "{\"date\":\"2026-07-15\",\"base\":\"USD\",\"quote\":\"MWK\",\"rate\":1740.00}");
        FrankfurterExchangeRateProvider provider = new FrankfurterExchangeRateProvider(client, URI.create("https://example.test/v2"), config());

        ExchangeRateQuote quote = provider.fetchHistoricalRate("USD", "MWK", LocalDate.of(2026, 7, 15));

        assertEquals(ExchangeRateSource.HISTORICAL, quote.source());
        assertTrue(client.lastUri().toString().contains("date=2026-07-15"));
    }

    @Test
    void rejectsHttpFailureInvalidJsonAndInvalidRate() {
        FrankfurterExchangeRateProvider failing = new FrankfurterExchangeRateProvider(
                FakeHttpClient.responding(500, "{\"message\":\"failed\"}"),
                URI.create("https://example.test/v2"),
                config()
        );
        assertThrows(ExchangeRateProviderException.class, () -> failing.fetchRate("USD", "MWK"));

        FrankfurterExchangeRateProvider invalidJson = new FrankfurterExchangeRateProvider(
                FakeHttpClient.responding(200, "not-json"),
                URI.create("https://example.test/v2"),
                config()
        );
        assertThrows(ExchangeRateProviderException.class, () -> invalidJson.fetchRate("USD", "MWK"));

        FrankfurterExchangeRateProvider invalidRate = new FrankfurterExchangeRateProvider(
                FakeHttpClient.responding(200, "{\"date\":\"2026-08-11\",\"base\":\"USD\",\"quote\":\"MWK\",\"rate\":0}"),
                URI.create("https://example.test/v2"),
                config()
        );
        assertThrows(ExchangeRateProviderException.class, () -> invalidRate.fetchRate("USD", "MWK"));
    }

    @Test
    void retriesTransientIoFailure() {
        FakeHttpClient client = FakeHttpClient.failingOnceThen(200, "{\"date\":\"2026-08-11\",\"base\":\"USD\",\"quote\":\"MWK\",\"rate\":1737.36}");
        FrankfurterExchangeRateProvider provider = new FrankfurterExchangeRateProvider(client, URI.create("https://example.test/v2"), config());

        provider.fetchRate("USD", "MWK");

        assertEquals(2, client.calls());
    }

    private FxConfig config() {
        return new FxConfig(
                true,
                ExchangeRateProviderType.FRANKFURTER,
                "https://example.test/v2",
                "",
                "RBM",
                360,
                true,
                true,
                true,
                24,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                Duration.ZERO,
                1,
                true,
                true,
                0,
                true,
                10,
                "PFMIS-Test/1.0",
                null,
                "",
                ""
        );
    }

    private static final class FakeHttpClient extends HttpClient {
        private final List<FakeResponse> responses;
        private int calls;
        private URI lastUri;

        private FakeHttpClient(List<FakeResponse> responses) {
            this.responses = responses;
        }

        static FakeHttpClient responding(int status, String body) {
            return new FakeHttpClient(List.of(new FakeResponse(status, body, false)));
        }

        static FakeHttpClient failingOnceThen(int status, String body) {
            return new FakeHttpClient(List.of(new FakeResponse(0, "", true), new FakeResponse(status, body, false)));
        }

        int calls() {
            return calls;
        }

        URI lastUri() {
            return lastUri;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            calls++;
            lastUri = request.uri();
            FakeResponse response = responses.get(Math.min(calls - 1, responses.size() - 1));
            if (response.fail()) {
                throw new IOException("simulated network failure");
            }
            @SuppressWarnings("unchecked")
            T body = (T) response.body();
            return new FakeHttpResponse<>(request, response.status(), body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeResponse(int status, String body, boolean fail) {
    }

    private record FakeHttpResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (first, second) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
