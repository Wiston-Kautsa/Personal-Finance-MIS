package com.wk.pfmis.config;

import com.wk.pfmis.fx.ExchangeRateProviderType;

import java.time.Duration;

public record FxConfig(
        boolean enabled,
        ExchangeRateProviderType provider,
        String baseUrl,
        String apiKey,
        String mwkPreferredSource,
        int refreshMinutes,
        boolean refreshOnStartup,
        boolean useCachedWhenOffline,
        boolean cacheEnabled,
        int staleAfterHours,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxRetries,
        Duration retryDelay,
        int maxConcurrentRequests,
        boolean historicalEnabled,
        boolean historicalCacheFallback,
        int historyRetentionDays,
        boolean validateResponses,
        int rateScale,
        String userAgent,
        ExchangeRateProviderType fallbackProvider,
        String fallbackBaseUrl,
        String fallbackApiKey
) {
    public static FxConfig fromConfig() {
        ExchangeRateProviderType provider = ExchangeRateProviderType.from(
                AppConfig.get("PFMIS_FX_PROVIDER", ExchangeRateProviderType.FRANKFURTER.name())
        );
        ExchangeRateProviderType fallbackProvider = ExchangeRateProviderType.fromOptional(
                AppConfig.get("PFMIS_FX_FALLBACK_PROVIDER", "")
        );
        return new FxConfig(
                AppConfig.getBoolean("PFMIS_FX_ENABLED", true),
                provider,
                AppConfig.get("PFMIS_FX_BASE_URL", provider.defaultBaseUrl()),
                AppConfig.get("PFMIS_FX_API_KEY", ""),
                AppConfig.get("PFMIS_FX_MWK_PREFERRED_SOURCE", "RBM"),
                AppConfig.getInt("PFMIS_FX_REFRESH_MINUTES", 360, 15, 24 * 60),
                AppConfig.getBoolean("PFMIS_FX_REFRESH_ON_STARTUP", true),
                AppConfig.getBoolean("PFMIS_FX_USE_CACHED_WHEN_OFFLINE", true),
                AppConfig.getBoolean("PFMIS_FX_CACHE_ENABLED", true),
                AppConfig.getInt("PFMIS_FX_STALE_AFTER_HOURS", 24, 1, 24 * 31),
                AppConfig.getDurationSeconds("PFMIS_FX_CONNECT_TIMEOUT_SECONDS", 10, 1, 120),
                AppConfig.getDurationSeconds("PFMIS_FX_REQUEST_TIMEOUT_SECONDS", 20, 1, 180),
                AppConfig.getInt("PFMIS_FX_MAX_RETRIES", 2, 0, 5),
                AppConfig.getDurationMillis("PFMIS_FX_RETRY_DELAY_MILLISECONDS", 1500, 0, 60_000),
                AppConfig.getInt("PFMIS_FX_MAX_CONCURRENT_REQUESTS", 3, 1, 10),
                AppConfig.getBoolean("PFMIS_FX_HISTORICAL_ENABLED", true),
                AppConfig.getBoolean("PFMIS_FX_HISTORICAL_CACHE_FALLBACK", true),
                AppConfig.getInt("PFMIS_FX_HISTORY_RETENTION_DAYS", 0, 0, 3650),
                AppConfig.getBoolean("PFMIS_FX_VALIDATE_RESPONSES", true),
                AppConfig.getInt("PFMIS_FX_RATE_SCALE", 10, 4, 18),
                AppConfig.get("PFMIS_FX_USER_AGENT", "PFMIS/1.0"),
                fallbackProvider,
                AppConfig.get("PFMIS_FX_FALLBACK_BASE_URL", fallbackProvider == null ? "" : fallbackProvider.defaultBaseUrl()),
                AppConfig.get("PFMIS_FX_FALLBACK_API_KEY", "")
        );
    }

    public boolean hasFallbackProvider() {
        return fallbackProvider != null;
    }
}
