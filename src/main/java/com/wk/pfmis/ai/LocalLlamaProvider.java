package com.wk.pfmis.ai;

import com.wk.pfmis.models.AiSettings;

public class LocalLlamaProvider implements AiProvider {
    private final String baseUrl;
    private final String model;

    public LocalLlamaProvider(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        BundledLocalAiManager.ensureReady();
        AiSettings settings = new AiSettings(
                true,
                AiSettings.DEFAULT_DISPLAY_NAME,
                AiSettings.PROVIDER_LOCAL_LLAMA,
                BundledLocalAiManager.endpoint(),
                model,
                BundledLocalAiManager.apiKey(),
                AiSettings.DEFAULT_AGENTS,
                AiSettings.DEFAULT_EXTENSIONS,
                AiSettings.KEY_STATUS_ACTIVE,
                true
        );
        return new CustomOpenAiCompatibleProvider(settings).chat(systemPrompt, userPrompt);
    }

    @Override
    public boolean isAvailable() {
        return BundledLocalAiManager.isReady();
    }

    @Override
    public String getProviderName() {
        return AiSettings.DEFAULT_DISPLAY_NAME;
    }
}
