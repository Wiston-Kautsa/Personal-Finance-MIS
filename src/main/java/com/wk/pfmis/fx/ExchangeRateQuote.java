package com.wk.pfmis.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public record ExchangeRateQuote(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        LocalDate effectiveDate,
        Instant retrievedAt,
        String providerName,
        ExchangeRateSource source,
        String rateType,
        boolean manual,
        boolean stale,
        String notes
) {
    public ExchangeRateQuote {
        fromCurrency = normalizeCurrency(fromCurrency);
        toCurrency = normalizeCurrency(toCurrency);
        Objects.requireNonNull(rate, "rate");
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than zero.");
        }
        effectiveDate = effectiveDate == null ? LocalDate.now() : effectiveDate;
        retrievedAt = retrievedAt == null ? Instant.now() : retrievedAt;
        providerName = providerName == null || providerName.isBlank() ? "PFMIS" : providerName.trim();
        source = source == null ? ExchangeRateSource.CACHED : source;
        rateType = rateType == null || rateType.isBlank() ? source.name() : rateType.trim().toUpperCase(Locale.ENGLISH);
        notes = notes == null ? "" : notes.trim();
    }

    public static ExchangeRateQuote sameCurrency(String currency) {
        String code = normalizeCurrency(currency);
        return new ExchangeRateQuote(
                code,
                code,
                BigDecimal.ONE,
                LocalDate.now(),
                Instant.now(),
                "PFMIS",
                ExchangeRateSource.CACHED,
                "SAME_CURRENCY",
                false,
                false,
                "Same-currency conversion"
        );
    }

    public ExchangeRateQuote asCached(boolean stale) {
        return new ExchangeRateQuote(
                fromCurrency,
                toCurrency,
                rate,
                effectiveDate,
                retrievedAt,
                providerName,
                source == ExchangeRateSource.MANUAL ? ExchangeRateSource.MANUAL : ExchangeRateSource.CACHED,
                source == ExchangeRateSource.MANUAL ? "MANUAL" : "CACHED",
                manual,
                stale,
                notes
        );
    }

    public ExchangeRateQuote asHistorical(boolean stale) {
        return new ExchangeRateQuote(
                fromCurrency,
                toCurrency,
                rate,
                effectiveDate,
                retrievedAt,
                providerName,
                manual ? ExchangeRateSource.MANUAL : ExchangeRateSource.HISTORICAL,
                manual ? "MANUAL" : "HISTORICAL",
                manual,
                stale,
                notes
        );
    }

    public ExchangeRateQuote inverted(int scale) {
        return new ExchangeRateQuote(
                toCurrency,
                fromCurrency,
                FxMath.inverse(rate, scale),
                effectiveDate,
                retrievedAt,
                providerName,
                source,
                rateType,
                manual,
                stale,
                notes
        );
    }

    public ExchangeRateStatus status() {
        if (manual) {
            return ExchangeRateStatus.MANUAL;
        }
        if (stale) {
            return ExchangeRateStatus.STALE;
        }
        return switch (source) {
            case ONLINE -> ExchangeRateStatus.LIVE;
            case CACHED -> ExchangeRateStatus.CACHED;
            case HISTORICAL -> ExchangeRateStatus.CURRENT;
            case MANUAL -> ExchangeRateStatus.MANUAL;
        };
    }

    public static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required.");
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ENGLISH);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency code must be a three-letter ISO code.");
        }
        return normalized;
    }
}
