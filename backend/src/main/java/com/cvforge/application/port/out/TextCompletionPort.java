package com.cvforge.application.port.out;

/** Outbound port for completion providers such as OpenRouter. */
public interface TextCompletionPort {
    String complete(String systemPrompt, String userPrompt, int maxTokens);
    String complete(String systemPrompt, String userPrompt, int maxTokens, String model);
}
