package com.wk.pfmis.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AppConfig {
    private static final Map<String, String> ENV_FILE_VALUES = loadEnvFile();

    private AppConfig() {
    }

    public static String get(String key, String fallback) {
        String systemProperty = System.getProperty(key);
        if (hasText(systemProperty)) {
            return systemProperty.trim();
        }
        String environmentValue = System.getenv(key);
        if (hasText(environmentValue)) {
            return environmentValue.trim();
        }
        String fileValue = ENV_FILE_VALUES.get(key);
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
        try {
            return Integer.parseInt(get(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static Path envFilePath() {
        return locateEnvFile();
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
        String explicit = System.getProperty("PFMIS_ENV_FILE");
        if (!hasText(explicit)) {
            explicit = System.getenv("PFMIS_ENV_FILE");
        }
        if (hasText(explicit)) {
            return Path.of(explicit.trim()).toAbsolutePath().normalize();
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
}
