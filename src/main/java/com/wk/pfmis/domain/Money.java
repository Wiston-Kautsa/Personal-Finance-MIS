package com.wk.pfmis.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Money implements Comparable<Money> {
    public static final String MWK = "MWK";
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    private static final Map<String, Integer> CURRENCY_SCALE_OVERRIDES = Map.of(MWK, 2);

    private final long amountMinor;
    private final String currencyCode;

    private Money(long amountMinor, String currencyCode) {
        this.amountMinor = amountMinor;
        this.currencyCode = normalizeCurrencyCode(currencyCode);
    }

    public static Money ofMinor(long amountMinor, String currencyCode) {
        return new Money(amountMinor, currencyCode);
    }

    public static Money zero(String currencyCode) {
        return ofMinor(0, currencyCode);
    }

    public static Money parseMajor(String amount, String currencyCode) {
        return parseMajor(amount, currencyCode, DEFAULT_ROUNDING);
    }

    public static Money parseMajor(String amount, String currencyCode, RoundingMode roundingMode) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        String code = normalizeCurrencyCode(currencyCode);
        int scale = scaleFor(code);
        BigDecimal major = new BigDecimal(amount.replace(",", "").trim());
        BigDecimal minor = major
                .setScale(scale, Objects.requireNonNull(roundingMode, "roundingMode"))
                .movePointRight(scale);
        return ofMinor(minor.longValueExact(), code);
    }

    public long amountMinor() {
        return amountMinor;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public int scale() {
        return scaleFor(currencyCode);
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(amountMinor, scale());
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return ofMinor(Math.addExact(amountMinor, other.amountMinor), currencyCode);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return ofMinor(Math.subtractExact(amountMinor, other.amountMinor), currencyCode);
    }

    public Money multiply(BigDecimal multiplier) {
        return multiply(multiplier, DEFAULT_ROUNDING);
    }

    public Money multiply(BigDecimal multiplier, RoundingMode roundingMode) {
        Objects.requireNonNull(multiplier, "multiplier");
        int scale = scale();
        BigDecimal result = toMajor()
                .multiply(multiplier)
                .setScale(scale, Objects.requireNonNull(roundingMode, "roundingMode"))
                .movePointRight(scale);
        return ofMinor(result.longValueExact(), currencyCode);
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Money values use different currencies.");
        }
    }

    public static int scaleFor(String currencyCode) {
        String code = normalizeCurrencyCode(currencyCode);
        Integer override = CURRENCY_SCALE_OVERRIDES.get(code);
        if (override != null) {
            return override;
        }
        try {
            return Currency.getInstance(code).getDefaultFractionDigits();
        } catch (IllegalArgumentException exception) {
            return 2;
        }
    }

    private static String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required.");
        }
        String code = currencyCode.trim().toUpperCase(Locale.ENGLISH);
        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency code must be a three-letter ISO code.");
        }
        return code;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Money money)) {
            return false;
        }
        return amountMinor == money.amountMinor && currencyCode.equals(money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountMinor, currencyCode);
    }

    @Override
    public String toString() {
        return currencyCode + " " + toMajor().setScale(scale()).toPlainString();
    }
}
