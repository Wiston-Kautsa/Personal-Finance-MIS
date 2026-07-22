package com.wk.pfmis.ai;

public interface AiProvider {
    String chat(String systemPrompt, String userPrompt) throws Exception;

    boolean isAvailable();

    String getProviderName();
}
