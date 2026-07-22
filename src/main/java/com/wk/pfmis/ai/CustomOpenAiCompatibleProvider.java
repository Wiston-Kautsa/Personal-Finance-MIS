package com.wk.pfmis.ai;

import com.wk.pfmis.models.AiSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CustomOpenAiCompatibleProvider implements AiProvider {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    private final AiSettings settings;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public CustomOpenAiCompatibleProvider(AiSettings settings) {
        this.settings = settings;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemPrompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(userPrompt) + "\"}"
                + "],"
                + "\"temperature\":0.2"
                + "}";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(chatCompletionsUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!settings.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + settings.getApiKey());
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI request failed with HTTP " + response.statusCode() + ".");
        }
        return extractRecommendation(response.body());
    }

    @Override
    public boolean isAvailable() {
        return settings != null
                && settings.isEnabled()
                && !settings.getEndpoint().isBlank()
                && !settings.getModel().isBlank()
                && (settings.isLocalProvider() || !settings.getApiKey().isBlank());
    }

    @Override
    public String getProviderName() {
        return settings == null ? "OpenAI Compatible" : settings.getProviderDisplayName();
    }

    protected URI chatCompletionsUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        if (normalized.matches("(?i).*/v\\d+")) {
            return URI.create(normalized + "/chat/completions");
        }
        return URI.create(normalized + "/v1/chat/completions");
    }

    protected String extractRecommendation(String responseBody) {
        String content = firstJsonStringValue(responseBody, "content");
        if (content.isBlank()) {
            content = firstJsonStringValue(responseBody, "output_text");
        }
        if (content.isBlank()) {
            content = firstJsonStringValue(responseBody, "text");
        }
        if (content.isBlank()) {
            throw new IllegalStateException("The AI response did not contain readable recommendation text.");
        }
        return content.strip();
    }

    private String firstJsonStringValue(String json, String key) {
        List<String> values = jsonStringValues(json, key);
        return values.isEmpty() ? "" : values.getFirst();
    }

    private List<String> jsonStringValues(String json, String key) {
        List<String> values = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return values;
        }
        String needle = "\"" + key + "\"";
        int searchFrom = 0;
        while (searchFrom < json.length()) {
            int keyIndex = json.indexOf(needle, searchFrom);
            if (keyIndex < 0) {
                return values;
            }
            int colonIndex = json.indexOf(':', keyIndex + needle.length());
            if (colonIndex < 0) {
                return values;
            }
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
                searchFrom = colonIndex + 1;
                continue;
            }
            values.add(parseJsonString(json, valueStart + 1));
            searchFrom = valueStart + 1;
        }
        return values;
    }

    private String parseJsonString(String json, int index) {
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int cursor = index; cursor < json.length(); cursor++) {
            char character = json.charAt(cursor);
            if (escaping) {
                switch (character) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (cursor + 4 < json.length()) {
                            value.append((char) Integer.parseInt(json.substring(cursor + 1, cursor + 5), 16));
                            cursor += 4;
                        }
                    }
                    default -> value.append(character);
                }
                escaping = false;
            } else if (character == '\\') {
                escaping = true;
            } else if (character == '"') {
                return value.toString();
            } else {
                value.append(character);
            }
        }
        return value.toString();
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
