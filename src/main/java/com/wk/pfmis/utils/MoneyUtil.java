package com.wk.pfmis.utils;

import java.text.NumberFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class MoneyUtil {
    private MoneyUtil() {
    }

    public static String mwk(double amount) {
        return format("MWK", amount);
    }

    public static String format(String currencyCode, double amount) {
        return format(currencyCode, BigDecimal.valueOf(amount));
    }

    public static String format(String currencyCode, BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        String currency = currencyCode == null || currencyCode.isBlank() ? "MWK" : currencyCode.trim().toUpperCase(Locale.ROOT);
        BigDecimal rounded = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        return currency + " " + format.format(rounded);
    }
}
