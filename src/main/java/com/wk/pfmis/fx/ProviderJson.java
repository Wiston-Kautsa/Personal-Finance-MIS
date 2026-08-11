package com.wk.pfmis.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProviderJson {
    private static final Pattern STRING_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)");
    private static final Pattern RATES_ENTRY_PATTERN = Pattern.compile("\"([A-Z]{3})\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)");

    private ProviderJson() {
    }

    static String stringValue(String json, String key) {
        Matcher matcher = Pattern.compile(STRING_PATTERN.pattern().formatted(Pattern.quote(key))).matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    static BigDecimal numberValue(String json, String key) {
        Matcher matcher = Pattern.compile(NUMBER_PATTERN.pattern().formatted(Pattern.quote(key))).matcher(json);
        if (!matcher.find()) {
            throw new ExchangeRateProviderException("Provider response did not include " + key + ".");
        }
        return new BigDecimal(matcher.group(1));
    }

    static long longValue(String json, String key, long fallback) {
        Matcher matcher = Pattern.compile(NUMBER_PATTERN.pattern().formatted(Pattern.quote(key))).matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : fallback;
    }

    static Map<String, BigDecimal> ratesMap(String json) {
        int ratesIndex = json.indexOf("\"rates\"");
        if (ratesIndex < 0) {
            throw new ExchangeRateProviderException("Provider response did not include rates.");
        }
        int openBrace = json.indexOf('{', ratesIndex);
        if (openBrace < 0) {
            throw new ExchangeRateProviderException("Provider rates object is malformed.");
        }
        int depth = 0;
        int closeBrace = -1;
        for (int index = openBrace; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    closeBrace = index;
                    break;
                }
            }
        }
        if (closeBrace <= openBrace) {
            throw new ExchangeRateProviderException("Provider rates object is malformed.");
        }
        String rates = json.substring(openBrace + 1, closeBrace);
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        Matcher matcher = RATES_ENTRY_PATTERN.matcher(rates);
        while (matcher.find()) {
            values.put(matcher.group(1).toUpperCase(Locale.ENGLISH), new BigDecimal(matcher.group(2)));
        }
        if (values.isEmpty()) {
            throw new ExchangeRateProviderException("Provider rates object was empty.");
        }
        return values;
    }

    static LocalDate localDateValue(String json, String key) {
        String value = stringValue(json, key);
        if (value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return LocalDate.now();
        }
    }

    static Instant unixInstant(String json, String key) {
        long value = longValue(json, key, 0);
        return value <= 0 ? Instant.now() : Instant.ofEpochSecond(value);
    }

    static Instant utcTimestamp(String json, String key) {
        String value = stringValue(json, key);
        if (value.isBlank()) {
            return Instant.now();
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from);
        } catch (DateTimeParseException exception) {
            return Instant.now().atOffset(ZoneOffset.UTC).toInstant();
        }
    }
}
