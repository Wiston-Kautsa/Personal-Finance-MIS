package com.wk.pfmis.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FxMath {
    public static final int DEFAULT_RATE_SCALE = 10;
    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private FxMath() {
    }

    public static BigDecimal normalizeRate(BigDecimal rate, int scale) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than zero.");
        }
        return rate.setScale(Math.max(4, scale), ROUNDING_MODE).stripTrailingZeros();
    }

    public static BigDecimal inverse(BigDecimal rate, int scale) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than zero.");
        }
        return BigDecimal.ONE.divide(rate, Math.max(4, scale), ROUNDING_MODE).stripTrailingZeros();
    }

    public static BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than zero.");
        }
        return amount.multiply(rate).setScale(MONEY_SCALE, ROUNDING_MODE);
    }
}
