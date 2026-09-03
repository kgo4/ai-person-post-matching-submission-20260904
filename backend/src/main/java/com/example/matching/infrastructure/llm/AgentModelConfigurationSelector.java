package com.example.matching.infrastructure.llm;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;

import java.math.BigDecimal;

/** Selects the administrator override when valid, otherwise the configured default agent model. */
public final class AgentModelConfigurationSelector {

    private AgentModelConfigurationSelector() {
    }

    public static boolean isUsable(SystemAiModelConfig config, String apiKey) {
        return config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && hasText(config.getBaseUrl())
                && hasText(config.getModelName())
                && hasText(apiKey);
    }

    public static SystemAiModelConfig defaultAgentConfig(LangChain4jAgentProperties properties) {
        if (properties == null || !properties.isEnabled()
                || !hasText(properties.getBaseUrl()) || !hasText(properties.getApiKey())
                || !hasText(properties.getModelName())) {
            return null;
        }
        SystemAiModelConfig config = new SystemAiModelConfig();
        config.setEnabled(true);
        config.setBaseUrl(properties.getBaseUrl());
        config.setModelName(properties.getModelName());
        config.setTimeoutSeconds(Math.toIntExact(properties.getTimeoutSeconds()));
        config.setTemperature(BigDecimal.valueOf(properties.getTemperature()));
        return config;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
