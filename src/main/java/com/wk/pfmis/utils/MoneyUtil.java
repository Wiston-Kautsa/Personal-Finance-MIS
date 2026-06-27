package com.wk.pfmis.utils;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyUtil {
    private static final NumberFormat FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private MoneyUtil() {
    }

    public static String mwk(double amount) {
        FORMAT.setMinimumFractionDigits(2);
        FORMAT.setMaximumFractionDigits(2);
        return "MWK " + FORMAT.format(amount);
    }
}
