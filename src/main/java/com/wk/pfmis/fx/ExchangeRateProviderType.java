package com.wk.pfmis.fx;

import java.util.Locale;

public enum ExchangeRateProviderType {
    FRANKFURTER("Frankfurter", "https://api.frankfurter.dev/v2", false, true),
    EXCHANGE_RATE_API_OPEN("ExchangeRate-API Open Access", "https://open.er-api.com/v6", false, false);

    private final String displayName;
    private final String defaultBaseUrl;
    private final boolean apiKeyRequired;
    private final boolean historicalSupported;

    ExchangeRateProviderType(String displayName, String defaultBaseUrl, boolean apiKeyRequired, boolean historicalSupported) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.apiKeyRequired = apiKeyRequired;
        this.historicalSupported = historicalSupported;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public boolean apiKeyRequired() {
        return apiKeyRequired;
    }

    public boolean historicalSupported() {
        return historicalSupported;
    }

    public static ExchangeRateProviderType from(String value) {
        ExchangeRateProviderType optional = fromOptional(value);
        return optional == null ? FRANKFURTER : optional;
    }

    public static ExchangeRateProviderType fromOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FRANKFURTER" -> FRANKFURTER;
            case "EXCHANGE_RATE_API_OPEN", "OPEN_ER_API", "OPEN_EXCHANGE_RATE_API", "EXCHANGERATE_API_OPEN" -> EXCHANGE_RATE_API_OPEN;
            default -> null;
        };
    }
}
