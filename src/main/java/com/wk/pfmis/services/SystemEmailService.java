package com.wk.pfmis.services;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.config.MailConfig;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.EmailSettings;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SystemEmailService {
    public static final String SYSTEM_EMAIL = "email.system_email";
    public static final String FROM_NAME = "email.from_name";
    public static final String REPLY_TO = "email.reply_to";
    public static final String MAIL_ENABLED = "email.enabled";
    public static final String SMTP_HOST = "email.smtp_host";
    public static final String SMTP_PORT = "email.smtp_port";
    public static final String SMTP_USERNAME = "email.smtp_username";
    public static final String SMTP_PASSWORD = "email.smtp_password";
    public static final String SMTP_AUTH = "email.smtp_auth";
    public static final String SMTP_STARTTLS = "email.smtp_starttls";
    public static final String SMTP_SSL = "email.smtp_ssl";
    public static final String IMAP_HOST = "email.imap_host";
    public static final String IMAP_PORT = "email.imap_port";
    public static final String IMAP_SSL = "email.imap_ssl";

    private static final SystemEmailService INSTANCE = new SystemEmailService();

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    private SystemEmailService() {
    }

    public static SystemEmailService getInstance() {
        return INSTANCE;
    }

    public void initializeDefaultsFromEnvironment() {
        EmailSettings defaults = environmentDefaults();
        saveDefault(SYSTEM_EMAIL, defaults.systemEmail());
        saveDefault(FROM_NAME, defaults.fromName());
        saveDefault(REPLY_TO, defaults.effectiveReplyTo());
        saveDefault(MAIL_ENABLED, Boolean.toString(defaults.enabled()));
        saveDefault(SMTP_HOST, defaults.smtpHost());
        saveDefault(SMTP_PORT, Integer.toString(defaults.smtpPort()));
        saveDefault(SMTP_USERNAME, defaults.effectiveSmtpUsername());
        saveDefault(SMTP_AUTH, Boolean.toString(defaults.smtpAuth()));
        saveDefault(SMTP_STARTTLS, Boolean.toString(defaults.smtpStartTls()));
        saveDefault(SMTP_SSL, Boolean.toString(defaults.smtpSsl()));
        saveDefault(IMAP_HOST, defaults.imapHost());
        saveDefault(IMAP_PORT, Integer.toString(defaults.imapPort()));
        saveDefault(IMAP_SSL, Boolean.toString(defaults.imapSsl()));
    }

    public EmailSettings currentSettings() {
        EmailSettings defaults = environmentDefaults();
        return new EmailSettings(
                booleanSetting(MAIL_ENABLED, defaults.enabled()),
                setting(SYSTEM_EMAIL, defaults.systemEmail()),
                setting(FROM_NAME, defaults.fromName()),
                setting(REPLY_TO, defaults.effectiveReplyTo()),
                setting(SMTP_HOST, defaults.smtpHost()),
                intSetting(SMTP_PORT, defaults.smtpPort(), 1, 65535),
                setting(SMTP_USERNAME, defaults.effectiveSmtpUsername()),
                passwordSetting(defaults.smtpPassword()),
                booleanSetting(SMTP_AUTH, defaults.smtpAuth()),
                booleanSetting(SMTP_STARTTLS, defaults.smtpStartTls()),
                booleanSetting(SMTP_SSL, defaults.smtpSsl()),
                setting(IMAP_HOST, defaults.imapHost()),
                intSetting(IMAP_PORT, defaults.imapPort(), 1, 65535),
                booleanSetting(IMAP_SSL, defaults.imapSsl()),
                defaults.connectTimeout(),
                defaults.readTimeout(),
                defaults.writeTimeout()
        );
    }

    public EmailSettings environmentDefaults() {
        MailConfig mailConfig = AppConfig.mailConfig();
        String systemEmail = appValue("PFMIS_SYSTEM_EMAIL", appValue("PFMIS_MAIL_FROM", ""));
        String replyTo = appValue("PFMIS_EMAIL_REPLY_TO", appValue("PFMIS_MAIL_REPLY_TO", systemEmail));
        String smtpUsername = appValue("PFMIS_SMTP_USERNAME", appValue("PFMIS_MAIL_USERNAME", systemEmail));
        String smtpPassword = appValue("PFMIS_SMTP_PASSWORD", appValue("PFMIS_MAIL_PASSWORD", ""));
        return new EmailSettings(
                mailConfig.enabled(),
                systemEmail,
                appValue("PFMIS_EMAIL_FROM_NAME", "PFMIS"),
                replyTo,
                appValue("PFMIS_SMTP_HOST", "smtp.gmail.com"),
                AppConfig.getInt("PFMIS_SMTP_PORT", 587, 1, 65535),
                smtpUsername,
                smtpPassword,
                AppConfig.getBoolean("PFMIS_SMTP_AUTH", true),
                AppConfig.getBoolean("PFMIS_SMTP_STARTTLS", true),
                AppConfig.getBoolean("PFMIS_SMTP_SSL", false),
                appValue("PFMIS_IMAP_HOST", "imap.gmail.com"),
                AppConfig.getInt("PFMIS_IMAP_PORT", 993, 1, 65535),
                AppConfig.getBoolean("PFMIS_IMAP_SSL", true),
                mailConfig.connectTimeout(),
                mailConfig.readTimeout(),
                mailConfig.writeTimeout()
        );
    }

    public void saveSettings(EmailSettings settings, boolean preserveExistingPassword, int actingUserId) {
        validateSettings(settings);
        EmailSettings before = currentSettings();
        String storedPassword = preserveExistingPassword && settings.smtpPassword().isBlank()
                ? authSetting(SMTP_PASSWORD, "")
                : settings.smtpPassword();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MAIL_ENABLED, Boolean.toString(settings.enabled()));
        values.put(SYSTEM_EMAIL, settings.systemEmail());
        values.put(FROM_NAME, settings.fromName());
        values.put(REPLY_TO, settings.effectiveReplyTo());
        values.put(SMTP_HOST, settings.smtpHost());
        values.put(SMTP_PORT, Integer.toString(settings.smtpPort()));
        values.put(SMTP_USERNAME, settings.effectiveSmtpUsername());
        values.put(SMTP_AUTH, Boolean.toString(settings.smtpAuth()));
        values.put(SMTP_STARTTLS, Boolean.toString(settings.smtpStartTls()));
        values.put(SMTP_SSL, Boolean.toString(settings.smtpSsl()));
        values.put(IMAP_HOST, settings.imapHost());
        values.put(IMAP_PORT, Integer.toString(settings.imapPort()));
        values.put(IMAP_SSL, Boolean.toString(settings.imapSsl()));
        values.put(SMTP_PASSWORD, storedPassword);
        authDatabase.saveSecuritySettings(values);

        if (!before.systemEmail().equalsIgnoreCase(settings.systemEmail())) {
            recordAudit(
                    "SYSTEM_EMAIL_CHANGED",
                    "System email changed by user " + actingUserId + " from "
                            + before.systemEmail() + " to " + settings.systemEmail() + "."
            );
        }
        recordAudit("EMAIL_CONFIGURATION_CHANGED", "Email configuration changed by user " + actingUserId + ".");
    }

    public void validateSettings(EmailSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Email settings are required.");
        }
        if (!EmailSettings.isEmailLike(settings.systemEmail())) {
            throw new IllegalArgumentException("System Email must be a valid email address.");
        }
        if (!settings.effectiveReplyTo().isBlank() && !EmailSettings.isEmailLike(settings.effectiveReplyTo())) {
            throw new IllegalArgumentException("Reply-To must be a valid email address.");
        }
        if (settings.smtpHost().isBlank()) {
            throw new IllegalArgumentException("SMTP Host is required.");
        }
        if (settings.smtpPort() <= 0 || settings.smtpPort() > 65535) {
            throw new IllegalArgumentException("SMTP Port must be between 1 and 65535.");
        }
        if (settings.smtpAuth() && settings.effectiveSmtpUsername().isBlank()) {
            throw new IllegalArgumentException("SMTP Username is required when authentication is enabled.");
        }
    }

    private void saveDefault(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        authDatabase.saveSecuritySettingIfAbsent(key, value);
    }

    private String setting(String key, String fallback) {
        return authSetting(key, fallback);
    }

    private String passwordSetting(String environmentFallback) {
        String stored = authSetting(SMTP_PASSWORD, "");
        return stored.isBlank() ? environmentFallback : stored;
    }

    private boolean booleanSetting(String key, boolean fallback) {
        String value = authSetting(key, Boolean.toString(fallback));
        return switch (value.trim().toLowerCase(java.util.Locale.ENGLISH)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    private int intSetting(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(authSetting(key, Integer.toString(fallback)).trim());
            return value < min || value > max ? fallback : value;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String authSetting(String key, String fallback) {
        try {
            return authDatabase.getSecuritySetting(key, fallback);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String appValue(String key, String fallback) {
        return AppConfig.get(key, fallback);
    }

    private void recordAudit(String action, String details) {
        try {
            DatabaseHandler.getInstance().recordSystemLog("Administration", action, "INFO", details);
        } catch (RuntimeException ignored) {
            // Settings can be saved before a workspace database is available.
        }
    }
}
