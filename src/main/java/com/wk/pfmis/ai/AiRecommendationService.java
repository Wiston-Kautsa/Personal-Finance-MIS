package com.wk.pfmis.ai;

import com.wk.pfmis.models.AiSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class AiRecommendationService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public String generateGoalRecommendation(AiSettings settings, String prompt) {
        validate(settings);
        String provider = settings.getProvider().toLowerCase(Locale.ENGLISH);
        try {
            if (settings.isBundledLocalProvider() || settings.isCustomOpenAiCompatibleProvider()) {
                return AiProviderFactory.create(settings).chat(systemPrompt(settings), prompt);
            }
            if (provider.contains("ollama")) {
                return callOllama(settings, prompt);
            }
            if (provider.contains("gemini")) {
                return callGemini(settings, prompt);
            }
            if (provider.contains("anthropic") || provider.contains("claude")) {
                return callAnthropic(settings, prompt);
            }
            if (provider.contains("cohere")) {
                return callCohere(settings, prompt);
            }
            if (provider.equals("openai") || settings.getEndpoint().contains("api.openai.com")) {
                return callOpenAiResponses(settings, prompt);
            }
            return callOpenAiCompatible(settings, prompt);
        } catch (HttpTimeoutException exception) {
            throw new IllegalStateException(timeoutMessage("Smart Analysis request"), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("AI request failed. Check the endpoint and internet connection.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request was interrupted.", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("AI request failed.", exception);
        }
    }

    public String testConnection(AiSettings settings) {
        return generateGoalRecommendation(settings, "Reply with one short sentence confirming that the PFMIS AI connection works.");
    }

    public List<String> listModels(AiSettings settings) {
        if (settings != null && settings.isBundledLocalProvider()) {
            return BundledLocalAiManager.modelAliases();
        }
        validateModelListSettings(settings);
        String provider = settings.getProvider().toLowerCase(Locale.ENGLISH);
        try {
            if (provider.contains("ollama")) {
                return callOllamaModels(settings);
            }
            if (provider.contains("gemini")) {
                return callGeminiModels(settings);
            }
            if (provider.contains("anthropic") || provider.contains("claude")) {
                return callAnthropicModels(settings);
            }
            if (provider.contains("cohere")) {
                return callCohereModels(settings);
            }
            return callOpenAiCompatibleModels(settings);
        } catch (HttpTimeoutException exception) {
            throw new IllegalStateException(timeoutMessage("Provider model loading"), exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load provider models. Check the endpoint and internet connection.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model loading was interrupted.", exception);
        }
    }

    private void validate(AiSettings settings) {
        if (settings == null || !settings.isEnabled()) {
            throw new IllegalStateException("Enable AI in Settings before requesting recommendations.");
        }
        if (settings.getEndpoint().isBlank()) {
            throw new IllegalStateException("Enter an AI endpoint in Settings.");
        }
        if (settings.getModel().isBlank()) {
            throw new IllegalStateException("Enter an AI model in Settings.");
        }
        if (!settings.isLocalProvider() && settings.getApiKey().isBlank()) {
            throw new IllegalStateException("Enter an API key in Settings, or use a local provider.");
        }
    }

    private void validateModelListSettings(AiSettings settings) {
        if (settings == null) {
            throw new IllegalStateException("Select an AI provider first.");
        }
        if (settings.getEndpoint().isBlank()) {
            throw new IllegalStateException("Enter an AI endpoint first.");
        }
        if (!settings.isLocalProvider() && settings.getApiKey().isBlank()) {
            throw new IllegalStateException("Enter or save an API key before loading provider models.");
        }
    }

    private String callOpenAiResponses(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"instructions\":\"" + jsonEscape(systemPrompt(settings)) + "\","
                + "\"input\":\"" + jsonEscape(prompt) + "\","
                + "\"temperature\":0.2"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(responsesUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return extractRecommendation(send(request));
    }

    private String callOpenAiCompatible(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemPrompt(settings)) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}"
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
        return extractRecommendation(send(builder.build()));
    }

    private String callAnthropic(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"max_tokens\":900,"
                + "\"temperature\":0.2,"
                + "\"system\":\"" + jsonEscape(systemPrompt(settings)) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}]"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(anthropicMessagesUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return extractRecommendation(send(request));
    }

    private String callGemini(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"systemInstruction\":{\"parts\":[{\"text\":\"" + jsonEscape(systemPrompt(settings)) + "\"}]},"
                + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"" + jsonEscape(prompt) + "\"}]}],"
                + "\"generationConfig\":{\"temperature\":0.2}"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(geminiGenerateContentUri(settings))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return extractRecommendation(send(request));
    }

    private String callCohere(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemPrompt(settings)) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}"
                + "],"
                + "\"temperature\":0.2"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(cohereChatUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return extractRecommendation(send(request));
    }

    private List<String> callOpenAiCompatibleModels(AiSettings settings) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(modelsUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (!settings.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + settings.getApiKey());
        }
        return extractModelNames(send(builder.build()), false);
    }

    private List<String> callAnthropicModels(AiSettings settings) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(modelsUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("x-api-key", settings.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .GET()
                .build();
        return extractModelNames(send(request), false);
    }

    private List<String> callGeminiModels(AiSettings settings) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(geminiModelsUri(settings))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return extractModelNames(send(request), true);
    }

    private List<String> callCohereModels(AiSettings settings) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(cohereModelsUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + settings.getApiKey())
                .GET()
                .build();
        return extractModelNames(send(request), false);
    }

    private List<String> callOllamaModels(AiSettings settings) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ollamaTagsUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return extractModelNames(send(request), false);
    }

    private String callOllama(AiSettings settings, String prompt) throws IOException, InterruptedException {
        String body = "{"
                + "\"model\":\"" + jsonEscape(settings.getModel()) + "\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemPrompt(settings)) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}"
                + "],"
                + "\"stream\":false"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ollamaChatUri(settings.getEndpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return extractRecommendation(send(request));
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(providerErrorMessage(response.statusCode(), response.body()));
        }
        return response.body();
    }

    private String extractRecommendation(String responseBody) {
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

    private URI responsesUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/responses")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/responses");
    }

    private URI chatCompletionsUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        if (normalized.matches("(?i).*/v\\d+")) {
            return URI.create(normalized + "/chat/completions");
        }
        return URI.create(normalized + "/v1/chat/completions");
    }

    private URI anthropicMessagesUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/messages")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/messages");
    }

    private URI geminiGenerateContentUri(AiSettings settings) {
        String normalized = trimTrailingSlash(settings.getEndpoint());
        if (normalized.endsWith(":generateContent")) {
            return URI.create(normalized + "?key=" + urlEncode(settings.getApiKey()));
        }
        String model = settings.getModel().startsWith("models/")
                ? settings.getModel().substring("models/".length())
                : settings.getModel();
        return URI.create(normalized + "/models/" + urlEncode(model) + ":generateContent?key=" + urlEncode(settings.getApiKey()));
    }

    private URI cohereChatUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/chat")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat");
    }

    private URI ollamaChatUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/api/chat")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/api/chat");
    }

    private URI modelsUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/models")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/models");
    }

    private URI geminiModelsUri(AiSettings settings) {
        String normalized = trimTrailingSlash(settings.getEndpoint());
        if (normalized.endsWith("/models")) {
            return URI.create(normalized + "?key=" + urlEncode(settings.getApiKey()));
        }
        return URI.create(normalized + "/models?key=" + urlEncode(settings.getApiKey()));
    }

    private URI cohereModelsUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/models")) {
            return URI.create(normalized);
        }
        if (normalized.endsWith("/v2")) {
            return URI.create(normalized.substring(0, normalized.length() - 3) + "/v1/models");
        }
        if (normalized.endsWith("/v1")) {
            return URI.create(normalized + "/models");
        }
        return URI.create(normalized + "/v1/models");
    }

    private URI ollamaTagsUri(String endpoint) {
        String normalized = trimTrailingSlash(endpoint);
        if (normalized.endsWith("/api/tags")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/api/tags");
    }

    private String systemPrompt(AiSettings settings) {
        return "You are PFMIS Smart Analysis. Use these enabled profiles: "
                + settings.getAgents()
                + ". Use only the data supplied by the app. Give clear personal-finance recommendations in MWK, avoid guarantees, and keep the answer concise.";
    }

    private List<String> extractModelNames(String responseBody, boolean stripGeminiPrefix) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String id : jsonStringValues(responseBody, "id")) {
            names.add(cleanModelName(id, stripGeminiPrefix));
        }
        for (String name : jsonStringValues(responseBody, "name")) {
            names.add(cleanModelName(name, stripGeminiPrefix));
        }
        for (String model : jsonStringValues(responseBody, "model")) {
            names.add(cleanModelName(model, stripGeminiPrefix));
        }
        List<String> result = names.stream()
                .filter(value -> !value.isBlank())
                .filter(value -> !value.contains("/operations/"))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (result.isEmpty()) {
            throw new IllegalStateException("The provider response did not contain model names.");
        }
        return result;
    }

    private String cleanModelName(String value, boolean stripGeminiPrefix) {
        String modelName = value == null ? "" : value.trim();
        if (stripGeminiPrefix && modelName.startsWith("models/")) {
            modelName = modelName.substring("models/".length());
        }
        return modelName;
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
                            value.append(parseUnicodeEscape(json.substring(cursor + 1, cursor + 5)));
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

    private char parseUnicodeEscape(String hex) {
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            return '?';
        }
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String shortBody(String value) {
        if (value == null || value.isBlank()) {
            return "No response body";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }

    private String providerErrorMessage(int statusCode, String body) {
        String providerMessage = firstJsonStringValue(body == null ? "" : body, "message");
        String providerType = firstJsonStringValue(body == null ? "" : body, "type");
        String providerCode = firstJsonStringValue(body == null ? "" : body, "code");
        String details = providerMessage.isBlank() ? shortBody(body) : providerMessage;
        String combined = (providerType + " " + providerCode + " " + details).toLowerCase(Locale.ENGLISH);
        if (statusCode == 401 || statusCode == 403) {
            return "API key INACTIVE. The provider rejected the saved key. Check the key, project permissions, and provider account. HTTP " + statusCode + ".";
        }
        if (statusCode == 429 && (combined.contains("quota") || combined.contains("billing") || combined.contains("insufficient"))) {
            return "AI key ACTIVE, but the provider says quota or billing is not available. Add credits, enable billing, or use another key/provider. HTTP 429.";
        }
        if (statusCode == 429) {
            return "AI key ACTIVE, but the provider rate limit was reached. Wait and try again, or choose another model/provider. HTTP 429.";
        }
        if (statusCode == 400 && combined.contains("model")) {
            return "The provider rejected the selected model. Check the model name and choose a model supported by this provider. HTTP 400.";
        }
        if (statusCode == 400) {
            return "The provider rejected the AI request. Check provider, endpoint, model, and request format. HTTP 400.";
        }
        return "AI request failed with HTTP " + statusCode + ". " + details;
    }

    private String timeoutMessage(String operation) {
        return operation + " timed out after " + REQUEST_TIMEOUT.toSeconds()
                + " seconds. If you are using the built-in local provider, the model may still be loading or the computer may be slow. Try again, ask a shorter question, or restart the local provider under Administration > Settings.";
    }
}
