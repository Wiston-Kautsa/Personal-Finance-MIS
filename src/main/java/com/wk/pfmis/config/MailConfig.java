package com.wk.pfmis.config;

import java.time.Duration;

public record MailConfig(
        boolean enabled,
        Duration connectTimeout,
        Duration readTimeout,
        Duration writeTimeout
) {
    public static MailConfig fromConfig() {
        return new MailConfig(
                AppConfig.getBoolean("PFMIS_MAIL_ENABLED", false),
                AppConfig.getDurationSeconds("PFMIS_MAIL_CONNECT_TIMEOUT_SECONDS", 15, 1, 300),
                AppConfig.getDurationSeconds("PFMIS_MAIL_READ_TIMEOUT_SECONDS", 30, 1, 600),
                AppConfig.getDurationSeconds("PFMIS_MAIL_WRITE_TIMEOUT_SECONDS", 30, 1, 600)
        );
    }
}
