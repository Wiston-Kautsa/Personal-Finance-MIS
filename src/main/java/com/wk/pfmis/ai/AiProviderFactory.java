package com.wk.pfmis.ai;

import com.wk.pfmis.models.AiSettings;

public final class AiProviderFactory {
    private AiProviderFactory() {
    }

    public static AiProvider create(AiSettings settings) {
        if (settings == null || settings.isBundledLocalProvider()) {
            return new LocalLlamaProvider(AiSettings.BUNDLED_LOCAL_ENDPOINT, AiSettings.BUNDLED_LOCAL_MODEL);
        }
        return new CustomOpenAiCompatibleProvider(settings);
    }
}
