package com.wk.pfmis.models;

import com.wk.pfmis.config.LocalAiConfig;

import java.util.Locale;

public class AiSettings {
    public static final String PROVIDER_LOCAL_LLAMA = "LOCAL_LLAMA";
    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "OpenAI Compatible";
    public static final String PROVIDER_OPENROUTER = "OpenRouter";
    public static final String PROVIDER_GROQ = "Groq";
    public static final String PROVIDER_DEEPSEEK = "DeepSeek";
    public static final String PROVIDER_MISTRAL = "Mistral";
    public static final String PROVIDER_ANTHROPIC = "Anthropic Claude";
    public static final String PROVIDER_GEMINI = "Google Gemini";
    public static final String PROVIDER_COHERE = "Cohere";
    public static final String PROVIDER_BUNDLED_LOCAL = "Bundled Local AI";
    public static final String PROVIDER_OLLAMA = "Local Ollama";
    public static final String PROVIDER_CUSTOM = "Custom OpenAI-Style HTTPS API";
    public static final String DEFAULT_PROVIDER = PROVIDER_LOCAL_LLAMA;
    public static final String DEFAULT_DISPLAY_NAME = "PFMIS Local AI";
    public static final String DEFAULT_ENDPOINT = LocalAiConfig.DEFAULT_ENDPOINT;
    public static final String DEFAULT_MODEL = "pfmis-model";
    public static final String BUNDLED_LOCAL_ENDPOINT = DEFAULT_ENDPOINT;
    public static final String BUNDLED_LOCAL_MODEL = DEFAULT_MODEL;
    public static final boolean DEFAULT_AUTO_START_LOCAL = true;
    public static final String DEFAULT_AGENTS = "PFMIS Copilot,Transaction Coach,Data Quality Guardian,Goal Coach,Budget Analyst,Project Spending Review,Loan Review,Backup Guardian";
    public static final String DEFAULT_EXTENSIONS = "Bundled Local Runtime,Local Provider Connector,CSV Insight Pack,Backup Guide";
    public static final String KEY_STATUS_ACTIVE = "ACTIVE";
    public static final String KEY_STATUS_INACTIVE = "INACTIVE";
    public static final String KEY_STATUS_QUOTA_LIMITED = "QUOTA_LIMITED";

    private final boolean enabled;
    private final String displayName;
    private final String provider;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final String agents;
    private final String extensions;
    private final String keyStatus;
    private final boolean autoStartLocal;

    public AiSettings(
            boolean enabled,
            String displayName,
            String provider,
            String endpoint,
            String model,
            String apiKey,
            String agents,
            String extensions
    ) {
        this(
                enabled,
                displayName,
                provider,
                endpoint,
                model,
                apiKey,
                agents,
                extensions,
                defaultKeyStatus(provider, endpoint, apiKey),
                defaultAutoStartLocal(provider)
        );
    }

    public AiSettings(
            boolean enabled,
            String displayName,
            String provider,
            String endpoint,
            String model,
            String apiKey,
            String agents,
            String extensions,
            String keyStatus
    ) {
        this(
                enabled,
                displayName,
                provider,
                endpoint,
                model,
                apiKey,
                agents,
                extensions,
                keyStatus,
                defaultAutoStartLocal(provider)
        );
    }

    public AiSettings(
            boolean enabled,
            String displayName,
            String provider,
            String endpoint,
            String model,
            String apiKey,
            String agents,
            String extensions,
            String keyStatus,
            boolean autoStartLocal
    ) {
        this.enabled = enabled;
        this.provider = normalizeProvider(provider);
        this.displayName = normalize(displayName, defaultDisplayName(this.provider));
        boolean localLlama = PROVIDER_LOCAL_LLAMA.equals(this.provider);
        this.endpoint = localLlama ? DEFAULT_ENDPOINT : normalize(endpoint, defaultEndpoint(this.provider));
        this.model = localLlama ? DEFAULT_MODEL : normalize(model, defaultModel(this.provider));
        this.apiKey = normalize(apiKey, "");
        this.agents = normalize(agents, DEFAULT_AGENTS);
        this.extensions = normalize(extensions, DEFAULT_EXTENSIONS);
        this.keyStatus = keyStatus == null || keyStatus.isBlank()
                ? defaultKeyStatus(this.provider, this.endpoint, this.apiKey)
                : normalizeKeyStatus(keyStatus);
        this.autoStartLocal = autoStartLocal;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProvider() {
        return provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAgents() {
        return agents;
    }

    public String getExtensions() {
        return extensions;
    }

    public String getKeyStatus() {
        return keyStatus;
    }

    public boolean isAutoStartLocal() {
        return autoStartLocal;
    }

    public boolean isLocalProvider() {
        return isBundledLocalProvider()
                || provider.toLowerCase(Locale.ENGLISH).contains("ollama")
                || endpoint.toLowerCase(Locale.ENGLISH).contains("localhost")
                || endpoint.toLowerCase(Locale.ENGLISH).contains("127.0.0.1");
    }

    public boolean isBundledLocalProvider() {
        return PROVIDER_LOCAL_LLAMA.equals(provider) || PROVIDER_BUNDLED_LOCAL.equals(provider);
    }

    public boolean isCustomOpenAiCompatibleProvider() {
        return PROVIDER_CUSTOM.equals(provider)
                || PROVIDER_OPENAI_COMPATIBLE.equals(provider)
                || PROVIDER_OPENROUTER.equals(provider)
                || PROVIDER_GROQ.equals(provider)
                || PROVIDER_DEEPSEEK.equals(provider)
                || PROVIDER_MISTRAL.equals(provider);
    }

    public String getProviderDisplayName() {
        return isBundledLocalProvider() ? DEFAULT_DISPLAY_NAME : provider;
    }

    public boolean canGenerateRecommendations() {
        if (!enabled || endpoint.isBlank() || model.isBlank()) {
            return false;
        }
        if (KEY_STATUS_INACTIVE.equals(keyStatus)) {
            return false;
        }
        return isLocalProvider() || !apiKey.isBlank();
    }

    public String maskedApiKey() {
        if (isLocalProvider()) {
            return "No API key required for PFMIS Local AI";
        }
        if (apiKey.isBlank()) {
            return "API key INACTIVE - no key saved";
        }
        String statusLabel = switch (keyStatus) {
            case KEY_STATUS_INACTIVE -> "INACTIVE";
            case KEY_STATUS_QUOTA_LIMITED -> "ACTIVE - quota limited";
            default -> "ACTIVE";
        };
        if (apiKey.length() <= 8) {
            return "API key " + statusLabel;
        }
        return "API key " + statusLabel + " (... " + apiKey.substring(apiKey.length() - 4) + ")";
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeProvider(String provider) {
        String normalized = normalize(provider, DEFAULT_PROVIDER);
        if (PROVIDER_BUNDLED_LOCAL.equals(normalized)
                || "PFMIS Local AI".equalsIgnoreCase(normalized)
                || "PFMIS Local Assistant".equalsIgnoreCase(normalized)
                || "PFMIS Local Provider".equalsIgnoreCase(normalized)) {
            return PROVIDER_LOCAL_LLAMA;
        }
        return normalized;
    }

    private static String defaultDisplayName(String provider) {
        return PROVIDER_LOCAL_LLAMA.equals(normalizeProvider(provider)) ? DEFAULT_DISPLAY_NAME : "Primary Provider";
    }

    private static String defaultEndpoint(String provider) {
        return PROVIDER_LOCAL_LLAMA.equals(normalizeProvider(provider)) ? BUNDLED_LOCAL_ENDPOINT : "https://api.openai.com/v1";
    }

    private static String defaultModel(String provider) {
        return PROVIDER_LOCAL_LLAMA.equals(normalizeProvider(provider)) ? BUNDLED_LOCAL_MODEL : "gpt-5-mini";
    }

    private static String defaultKeyStatus(String provider, String endpoint, String apiKey) {
        String normalizedProvider = normalize(provider, DEFAULT_PROVIDER);
        String normalizedEndpoint = normalize(endpoint, DEFAULT_ENDPOINT);
        String normalizedKey = normalize(apiKey, "");
        boolean localProvider = PROVIDER_LOCAL_LLAMA.equals(normalizeProvider(normalizedProvider))
                || PROVIDER_BUNDLED_LOCAL.equals(normalizedProvider)
                || normalizedProvider.toLowerCase(Locale.ENGLISH).contains("ollama")
                || normalizedEndpoint.toLowerCase(Locale.ENGLISH).contains("localhost")
                || normalizedEndpoint.toLowerCase(Locale.ENGLISH).contains("127.0.0.1");
        if (localProvider || !normalizedKey.isBlank()) {
            return KEY_STATUS_ACTIVE;
        }
        return KEY_STATUS_INACTIVE;
    }

    private static boolean defaultAutoStartLocal(String provider) {
        return PROVIDER_LOCAL_LLAMA.equals(normalizeProvider(provider));
    }

    private static String normalizeKeyStatus(String status) {
        String normalized = normalize(status, "").toUpperCase(Locale.ENGLISH);
        return switch (normalized) {
            case KEY_STATUS_INACTIVE -> KEY_STATUS_INACTIVE;
            case KEY_STATUS_QUOTA_LIMITED -> KEY_STATUS_QUOTA_LIMITED;
            default -> KEY_STATUS_ACTIVE;
        };
    }
}
