package com.wk.pfmis.fx;

import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.config.FxConfig;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.CurrencyRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ExchangeRateService {
    private static final ExchangeRateService INSTANCE = new ExchangeRateService(DatabaseHandler.getInstance(), AppConfig.fxConfig());

    private final DatabaseHandler database;
    private final FxConfig config;
    private final ExchangeRateProvider primaryProvider;
    private final ExchangeRateProvider fallbackProvider;
    private final ExecutorService executor;
    private final Semaphore networkSlots;

    public ExchangeRateService(DatabaseHandler database, FxConfig config) {
        this.database = database;
        this.config = config;
        this.primaryProvider = provider(config.provider(), config);
        this.fallbackProvider = config.hasFallbackProvider()
                ? provider(config.fallbackProvider(), fallbackConfig(config))
                : null;
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, config.maxConcurrentRequests()),
                runnable -> {
                    Thread thread = new Thread(runnable, "pfmis-fx-refresh");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.networkSlots = new Semaphore(Math.max(1, config.maxConcurrentRequests()));
    }

    public static ExchangeRateService getInstance() {
        return INSTANCE;
    }

    public ExchangeRateQuote getRate(String fromCurrency, String toCurrency) {
        return getLatestRate(fromCurrency, toCurrency);
    }

    public ExchangeRateQuote getLatestRate(String fromCurrency, String toCurrency) {
        String from = ExchangeRateQuote.normalizeCurrency(fromCurrency);
        String to = ExchangeRateQuote.normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return ExchangeRateQuote.sameCurrency(from);
        }
        Optional<ExchangeRateQuote> manual = database.findActiveManualExchangeRate(from, to, LocalDate.now());
        if (manual.isPresent()) {
            return manual.get();
        }
        if (config.enabled()) {
            try {
                return refreshRate(from, to);
            } catch (RuntimeException exception) {
                database.recordSystemLog("Foreign Exchange", "Provider request failed", "WARNING", userSafeMessage(exception));
            }
        }
        return getLastKnownRate(from, to)
                .orElseThrow(() -> new ExchangeRateUnavailableException("Exchange rate unavailable for " + from + " to " + to + "."));
    }

    public ExchangeRateQuote getRateForDate(String fromCurrency, String toCurrency, LocalDate date) {
        String from = ExchangeRateQuote.normalizeCurrency(fromCurrency);
        String to = ExchangeRateQuote.normalizeCurrency(toCurrency);
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        if (from.equals(to)) {
            return ExchangeRateQuote.sameCurrency(from);
        }
        Optional<ExchangeRateQuote> manual = database.findActiveManualExchangeRate(from, to, effectiveDate);
        if (manual.isPresent()) {
            return manual.get();
        }
        Optional<ExchangeRateQuote> storedHistorical = directOrInverseHistorical(from, to, effectiveDate);
        if (storedHistorical.isPresent()) {
            return storedHistorical.get().asHistorical(isStale(storedHistorical.get()));
        }
        if (config.enabled() && config.historicalEnabled()) {
            try {
                ExchangeRateQuote quote = fetchHistorical(from, to, effectiveDate);
                database.saveExchangeRate(quote);
                return quote;
            } catch (RuntimeException exception) {
                database.recordSystemLog("Foreign Exchange", "Historical rate unavailable", "WARNING", userSafeMessage(exception));
            }
        }
        if (config.historicalCacheFallback()) {
            return getLastKnownRate(from, to)
                    .orElseThrow(() -> new ExchangeRateUnavailableException("Exchange rate unavailable for " + from + " to " + to + " on " + effectiveDate + "."));
        }
        throw new ExchangeRateUnavailableException("Exchange rate unavailable for " + from + " to " + to + " on " + effectiveDate + ".");
    }

    public ExchangeRateQuote refreshRate(String fromCurrency, String toCurrency) {
        String from = ExchangeRateQuote.normalizeCurrency(fromCurrency);
        String to = ExchangeRateQuote.normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return ExchangeRateQuote.sameCurrency(from);
        }
        if (!config.enabled()) {
            throw new ExchangeRateUnavailableException("Automatic exchange rates are disabled.");
        }
        return withNetworkSlot(() -> {
            RuntimeException primaryFailure = null;
            try {
                ExchangeRateQuote quote = primaryProvider.fetchRate(from, to);
                database.saveExchangeRate(quote);
                return quote;
            } catch (RuntimeException exception) {
                primaryFailure = exception;
            }
            if (fallbackProvider != null) {
                try {
                    ExchangeRateQuote quote = fallbackProvider.fetchRate(from, to);
                    database.saveExchangeRate(quote);
                    return quote;
                } catch (RuntimeException fallbackFailure) {
                    primaryFailure.addSuppressed(fallbackFailure);
                }
            }
            throw primaryFailure;
        });
    }

    public List<ExchangeRateQuote> refreshRates() {
        if (!config.enabled()) {
            throw new ExchangeRateUnavailableException("Automatic exchange rates are disabled.");
        }
        String baseCurrency = database.getBaseCurrencyCode();
        List<ExchangeRateQuote> refreshed = new ArrayList<>();
        database.recordSystemLog("Foreign Exchange", "Refresh Started", "INFO", "Exchange-rate refresh started for base " + baseCurrency + ".");
        for (CurrencyRecord currency : database.listCurrencies()) {
            String code = currency.getCurrencyCode();
            if (!"ACTIVE".equalsIgnoreCase(currency.getStatus()) || currency.isBaseCurrency() || code == null || code.isBlank()) {
                continue;
            }
            try {
                refreshed.add(refreshRate(code, baseCurrency));
            } catch (RuntimeException exception) {
                database.recordSystemLog("Foreign Exchange", "Rate Refresh Failed", "WARNING", code + "/" + baseCurrency + ": " + userSafeMessage(exception));
            }
        }
        database.recordSystemLog("Foreign Exchange", "Refresh Completed", "INFO", refreshed.size() + " exchange rate(s) refreshed.");
        return refreshed;
    }

    public CompletableFuture<List<ExchangeRateQuote>> refreshRatesAsync() {
        return CompletableFuture.supplyAsync(this::refreshRates, executor);
    }

    public CompletableFuture<ExchangeRateQuote> refreshRateAsync(String fromCurrency, String toCurrency) {
        return CompletableFuture.supplyAsync(() -> refreshRate(fromCurrency, toCurrency), executor);
    }

    public Optional<ExchangeRateQuote> getLastKnownRate(String fromCurrency, String toCurrency) {
        String from = ExchangeRateQuote.normalizeCurrency(fromCurrency);
        String to = ExchangeRateQuote.normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return Optional.of(ExchangeRateQuote.sameCurrency(from));
        }
        Optional<ExchangeRateQuote> quote = directOrInverseLatest(from, to);
        return quote.map(stored -> stored.manual() ? stored : stored.asCached(isStale(stored)));
    }

    public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        ExchangeRateQuote quote = getRate(fromCurrency, toCurrency);
        return convert(amount, quote);
    }

    public ConversionResult convertForDate(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        ExchangeRateQuote quote = getRateForDate(fromCurrency, toCurrency, date);
        return convert(amount, quote);
    }

    public ConversionResult convertUsingLastKnown(BigDecimal amount, String fromCurrency, String toCurrency) {
        ExchangeRateQuote quote = getLastKnownRate(fromCurrency, toCurrency)
                .orElseThrow(() -> new ExchangeRateUnavailableException("Exchange rate unavailable for " + fromCurrency + " to " + toCurrency + "."));
        return convert(amount, quote);
    }

    public void saveManualRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate, LocalDate expiryDate, String notes) {
        database.saveManualExchangeRate(fromCurrency, toCurrency, rate, effectiveDate, expiryDate, notes);
    }

    public ExchangeRateSystemStatus getSystemStatus() {
        List<ExchangeRateQuote> rates = database.listLatestExchangeRatesToBase();
        Optional<Instant> latest = rates.stream().map(ExchangeRateQuote::retrievedAt).max(Instant::compareTo);
        boolean stale = rates.stream().anyMatch(this::isStale);
        ExchangeRateStatus status;
        if (!config.enabled()) {
            status = ExchangeRateStatus.DISABLED;
        } else if (rates.isEmpty()) {
            status = ExchangeRateStatus.UNAVAILABLE;
        } else if (stale) {
            status = ExchangeRateStatus.STALE;
        } else if (rates.stream().anyMatch(rate -> rate.source() == ExchangeRateSource.CACHED)) {
            status = ExchangeRateStatus.CACHED;
        } else {
            status = ExchangeRateStatus.CURRENT;
        }
        return new ExchangeRateSystemStatus(
                config.enabled(),
                primaryProvider.providerName(),
                database.getBaseCurrencyCode(),
                latest,
                status,
                rates.size(),
                statusMessage(status, latest)
        );
    }

    public boolean isOnlineRateAvailable(String fromCurrency, String toCurrency) {
        try {
            refreshRate(fromCurrency, toCurrency);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public Optional<ExchangeRateQuote> getRateMetadata(String fromCurrency, String toCurrency) {
        return getLastKnownRate(fromCurrency, toCurrency);
    }

    private ConversionResult convert(BigDecimal amount, ExchangeRateQuote quote) {
        BigDecimal original = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal converted = FxMath.convert(original, quote.rate());
        return new ConversionResult(original, quote.fromCurrency(), converted, quote.toCurrency(), quote);
    }

    private Optional<ExchangeRateQuote> directOrInverseLatest(String from, String to) {
        Optional<ExchangeRateQuote> direct = database.findLatestExchangeRate(from, to);
        if (direct.isPresent()) {
            return direct;
        }
        return database.findLatestExchangeRate(to, from).map(quote -> quote.inverted(config.rateScale()));
    }

    private Optional<ExchangeRateQuote> directOrInverseHistorical(String from, String to, LocalDate date) {
        Optional<ExchangeRateQuote> direct = database.findHistoricalExchangeRate(from, to, date);
        if (direct.isPresent()) {
            return direct;
        }
        return database.findHistoricalExchangeRate(to, from, date).map(quote -> quote.inverted(config.rateScale()));
    }

    private ExchangeRateQuote fetchHistorical(String from, String to, LocalDate date) {
        RuntimeException primaryFailure = null;
        try {
            return primaryProvider.fetchHistoricalRate(from, to, date);
        } catch (RuntimeException exception) {
            primaryFailure = exception;
        }
        if (fallbackProvider != null) {
            try {
                return fallbackProvider.fetchHistoricalRate(from, to, date);
            } catch (RuntimeException exception) {
                primaryFailure.addSuppressed(exception);
            }
        }
        throw primaryFailure;
    }

    private boolean isStale(ExchangeRateQuote quote) {
        return quote != null
                && quote.source() != ExchangeRateSource.MANUAL
                && quote.retrievedAt().isBefore(Instant.now().minus(config.staleAfterHours(), ChronoUnit.HOURS));
    }

    private <T> T withNetworkSlot(CheckedSupplier<T> supplier) {
        boolean acquired = false;
        try {
            networkSlots.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateProviderException("Exchange-rate request was interrupted.", exception);
        } finally {
            if (acquired) {
                networkSlots.release();
            }
        }
    }

    private ExchangeRateProvider provider(ExchangeRateProviderType providerType, FxConfig providerConfig) {
        ExchangeRateProviderType type = providerType == null ? ExchangeRateProviderType.FRANKFURTER : providerType;
        return switch (type) {
            case FRANKFURTER -> new FrankfurterExchangeRateProvider(providerConfig);
            case EXCHANGE_RATE_API_OPEN -> new OpenExchangeRateApiProvider(providerConfig);
        };
    }

    private FxConfig fallbackConfig(FxConfig original) {
        return new FxConfig(
                original.enabled(),
                original.fallbackProvider(),
                original.fallbackBaseUrl(),
                original.fallbackApiKey(),
                original.mwkPreferredSource(),
                original.refreshMinutes(),
                original.refreshOnStartup(),
                original.useCachedWhenOffline(),
                original.cacheEnabled(),
                original.staleAfterHours(),
                original.connectTimeout(),
                original.requestTimeout(),
                original.maxRetries(),
                original.retryDelay(),
                original.maxConcurrentRequests(),
                original.historicalEnabled(),
                original.historicalCacheFallback(),
                original.historyRetentionDays(),
                original.validateResponses(),
                original.rateScale(),
                original.userAgent(),
                null,
                "",
                ""
        );
    }

    private String statusMessage(ExchangeRateStatus status, Optional<Instant> latest) {
        return switch (status) {
            case DISABLED -> "Automatic exchange rates are disabled.";
            case UNAVAILABLE -> "No saved exchange rates are available.";
            case STALE -> "Using saved exchange rates. Last update: " + latest.map(Instant::toString).orElse("-");
            case CACHED -> "Using saved exchange rates.";
            case CURRENT, LIVE -> "Exchange rates are current.";
            case MANUAL -> "Manual exchange rates are active.";
        };
    }

    private String userSafeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }

    public record ExchangeRateSystemStatus(
            boolean enabled,
            String providerName,
            String baseCurrency,
            Optional<Instant> lastSuccessfulUpdate,
            ExchangeRateStatus status,
            int activeRateCount,
            String message
    ) {
    }
}
