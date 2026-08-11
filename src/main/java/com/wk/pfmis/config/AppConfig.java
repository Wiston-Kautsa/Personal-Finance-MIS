package com.wk.pfmis.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class AppConfig {
    private static volatile Map<String, String> envFileValues = loadEnvFile();

    private AppConfig() {
    }

    public static String get(String key, String fallback) {
        String environmentValue = System.getenv(key);
        if (hasText(environmentValue)) {
            return environmentValue.trim();
        }
        String systemProperty = System.getProperty(key);
        if (hasText(systemProperty)) {
            return systemProperty.trim();
        }
        String fileValue = envFileValues.get(key);
        return hasText(fileValue) ? fileValue.trim() : fallback;
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String value = get(key, Boolean.toString(fallback)).trim().toLowerCase(Locale.ENGLISH);
        return switch (value) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    public static int getInt(String key, int fallback) {
        return getInt(key, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static int getInt(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(get(key, Integer.toString(fallback)).trim());
            return value < min || value > max ? fallback : value;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static Duration getDurationSeconds(String key, int fallbackSeconds, int minSeconds, int maxSeconds) {
        return Duration.ofSeconds(getInt(key, fallbackSeconds, minSeconds, maxSeconds));
    }

    public static Duration getDurationMillis(String key, int fallbackMillis, int minMillis, int maxMillis) {
        return Duration.ofMillis(getInt(key, fallbackMillis, minMillis, maxMillis));
    }

    public static <E extends Enum<E>> E getEnum(
            String key,
            Class<E> enumType,
            E fallback,
            Function<String, E> parser
    ) {
        try {
            String value = get(key, fallback.name());
            E parsed = parser == null ? Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ENGLISH)) : parser.apply(value);
            return parsed == null ? fallback : parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static FxConfig fxConfig() {
        return FxConfig.fromConfig();
    }

    public static MailConfig mailConfig() {
        return MailConfig.fromConfig();
    }

    public static LoggingConfig loggingConfig() {
        return LoggingConfig.fromConfig();
    }

    public static LocalAiConfig localAiConfig() {
        return LocalAiConfig.fromConfig();
    }

    public static ApplicationEnvironment applicationEnvironment() {
        return ApplicationEnvironment.from(get("PFMIS_APP_ENV", "production"));
    }

    public static Path envFilePath() {
        return locateEnvFile();
    }

    public static Path applicationDataDirectory() {
        return resolveApplicationDataDirectory();
    }

    public static void ensureLocalEnvFileExists() {
        Path path = envFilePath();
        if (Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultEnvTemplate());
            envFileValues = loadEnvFile();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create PFMIS .env file: " + path, exception);
        }
    }

    public static void reload() {
        envFileValues = loadEnvFile();
    }

    private static Map<String, String> loadEnvFile() {
        Path file = locateEnvFile();
        if (!Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = stripInlineComment(trimmed.substring(equalsIndex + 1).trim());
                values.put(key, stripQuotes(value));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read PFMIS .env file: " + file, exception);
        }
        return Collections.unmodifiableMap(values);
    }

    private static Path locateEnvFile() {
        String explicit = System.getenv("PFMIS_ENV_FILE");
        if (!hasText(explicit)) {
            explicit = System.getProperty("PFMIS_ENV_FILE");
        }
        if (hasText(explicit)) {
            return Path.of(explicit.trim()).toAbsolutePath().normalize();
        }

        Path appDataEnv = resolveApplicationDataDirectory().resolve(".env").toAbsolutePath().normalize();
        if (Files.isRegularFile(appDataEnv) || applicationEnvironmentFromProcess() == ApplicationEnvironment.PRODUCTION) {
            return appDataEnv;
        }

        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int index = 0; index < 6 && current != null; index++) {
            Path candidate = current.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(".env");
    }

    private static ApplicationEnvironment applicationEnvironmentFromProcess() {
        String value = System.getenv("PFMIS_APP_ENV");
        if (!hasText(value)) {
            value = System.getProperty("PFMIS_APP_ENV");
        }
        return ApplicationEnvironment.from(hasText(value) ? value : "production");
    }

    private static Path resolveApplicationDataDirectory() {
        String explicitDataDirectory = System.getProperty("pfmis.data.dir", "").trim();
        if (!explicitDataDirectory.isBlank()) {
            return Path.of(explicitDataDirectory).toAbsolutePath().normalize();
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String userHome = System.getProperty("user.home", ".");
        if (osName.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (hasText(localAppData)) {
                return Path.of(localAppData, "PFMIS").toAbsolutePath().normalize();
            }
            return Path.of(userHome, "AppData", "Local", "PFMIS").toAbsolutePath().normalize();
        }
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "PFMIS").toAbsolutePath().normalize();
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (hasText(xdgDataHome)) {
            return Path.of(xdgDataHome, "PFMIS").toAbsolutePath().normalize();
        }
        return Path.of(userHome, ".local", "share", "PFMIS").toAbsolutePath().normalize();
    }

    private static String stripInlineComment(String value) {
        boolean quoted = value.startsWith("\"") || value.startsWith("'");
        if (quoted) {
            return value;
        }
        int commentIndex = value.indexOf(" #");
        return commentIndex >= 0 ? value.substring(0, commentIndex).trim() : value;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String defaultEnvTemplate() {
        List<String> lines = new ArrayList<>();
        lines.add("PFMIS_APP_ENV=production");
        lines.add("");
        lines.add("# Foreign Exchange");
        lines.add("PFMIS_FX_ENABLED=true");
        lines.add("PFMIS_FX_PROVIDER=FRANKFURTER");
        lines.add("PFMIS_FX_BASE_URL=https://api.frankfurter.dev/v2");
        lines.add("PFMIS_FX_API_KEY=");
        lines.add("PFMIS_FX_MWK_PREFERRED_SOURCE=RBM");
        lines.add("PFMIS_FX_REFRESH_MINUTES=360");
        lines.add("PFMIS_FX_REFRESH_ON_STARTUP=true");
        lines.add("PFMIS_FX_USE_CACHED_WHEN_OFFLINE=true");
        lines.add("PFMIS_FX_CACHE_ENABLED=true");
        lines.add("PFMIS_FX_STALE_AFTER_HOURS=24");
        lines.add("PFMIS_FX_CONNECT_TIMEOUT_SECONDS=10");
        lines.add("PFMIS_FX_REQUEST_TIMEOUT_SECONDS=20");
        lines.add("PFMIS_FX_MAX_RETRIES=2");
        lines.add("PFMIS_FX_RETRY_DELAY_MILLISECONDS=1500");
        lines.add("PFMIS_FX_MAX_CONCURRENT_REQUESTS=3");
        lines.add("PFMIS_FX_HISTORICAL_ENABLED=true");
        lines.add("PFMIS_FX_HISTORICAL_CACHE_FALLBACK=true");
        lines.add("PFMIS_FX_HISTORY_RETENTION_DAYS=0");
        lines.add("PFMIS_FX_VALIDATE_RESPONSES=true");
        lines.add("PFMIS_FX_RATE_SCALE=10");
        lines.add("PFMIS_FX_USER_AGENT=PFMIS/1.0");
        lines.add("PFMIS_FX_FALLBACK_PROVIDER=");
        lines.add("PFMIS_FX_FALLBACK_BASE_URL=");
        lines.add("PFMIS_FX_FALLBACK_API_KEY=");
        lines.add("");
        lines.add("# Local AI");
        lines.add("PFMIS_LOCAL_AI_ENABLED=true");
        lines.add("PFMIS_LOCAL_AI_HOST=127.0.0.1");
        lines.add("PFMIS_LOCAL_AI_PORT=8080");
        lines.add("PFMIS_LOCAL_AI_CONTEXT_SIZE=2048");
        lines.add("PFMIS_LOCAL_AI_STARTUP_TIMEOUT_SECONDS=120");
        lines.add("PFMIS_LOCAL_AI_HEALTH_POLL_MILLISECONDS=1000");
        lines.add("PFMIS_LOCAL_AI_REQUEST_TIMEOUT_SECONDS=10");
        lines.add("PFMIS_LOCAL_AI_DIR=");
        lines.add("");
        lines.add("# Mail");
        lines.add("PFMIS_MAIL_ENABLED=false");
        lines.add("PFMIS_MAIL_CONNECT_TIMEOUT_SECONDS=15");
        lines.add("PFMIS_MAIL_READ_TIMEOUT_SECONDS=30");
        lines.add("PFMIS_MAIL_WRITE_TIMEOUT_SECONDS=30");
        lines.add("PFMIS_SMTP_HOST=smtp.gmail.com");
        lines.add("PFMIS_SMTP_PORT=587");
        lines.add("PFMIS_SMTP_STARTTLS=true");
        lines.add("PFMIS_SMTP_SSL=false");
        lines.add("PFMIS_IMAP_HOST=imap.gmail.com");
        lines.add("PFMIS_IMAP_PORT=993");
        lines.add("PFMIS_IMAP_SSL=true");
        lines.add("PFMIS_MAIL_FROM=");
        lines.add("PFMIS_MAIL_REPLY_TO=");
        lines.add("PFMIS_MAIL_USERNAME=");
        lines.add("PFMIS_MAIL_PASSWORD=");
        lines.add("");
        lines.add("# Logging");
        lines.add("PFMIS_LOG_LEVEL=INFO");
        lines.add("PFMIS_LOG_RETENTION_DAYS=30");
        lines.add("");
        return String.join(System.lineSeparator(), lines);
    }
}
