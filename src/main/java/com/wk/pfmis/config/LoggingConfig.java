package com.wk.pfmis.config;

import java.util.Locale;

public record LoggingConfig(LogLevel level, int retentionDays) {
    public static LoggingConfig fromConfig() {
        return new LoggingConfig(
                LogLevel.from(AppConfig.get("PFMIS_LOG_LEVEL", "INFO")),
                AppConfig.getInt("PFMIS_LOG_RETENTION_DAYS", 30, 1, 3650)
        );
    }

    public enum LogLevel {
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE;

        public static LogLevel from(String value) {
            if (value == null || value.isBlank()) {
                return INFO;
            }
            try {
                return LogLevel.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException exception) {
                return INFO;
            }
        }
    }
}
