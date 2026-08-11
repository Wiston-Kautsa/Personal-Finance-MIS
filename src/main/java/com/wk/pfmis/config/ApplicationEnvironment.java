package com.wk.pfmis.config;

import java.util.Locale;

public enum ApplicationEnvironment {
    PRODUCTION,
    DEVELOPMENT,
    TEST;

    public static ApplicationEnvironment from(String value) {
        if (value == null || value.isBlank()) {
            return PRODUCTION;
        }
        return switch (value.trim().toUpperCase(Locale.ENGLISH)) {
            case "DEV", "DEVELOPMENT" -> DEVELOPMENT;
            case "TEST", "TESTING" -> TEST;
            default -> PRODUCTION;
        };
    }
}
