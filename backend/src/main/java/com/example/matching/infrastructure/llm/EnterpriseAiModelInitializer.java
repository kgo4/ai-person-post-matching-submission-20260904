package com.example.matching.infrastructure.llm;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.service.system.SystemAiModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Applies the administrator override or the default Agent model after all beans are ready. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseAiModelInitializer {

    private final SystemAiModelConfigService configService;
    private final EnterpriseChatLanguageModel enterpriseChatLanguageModel;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LangChain4jAgentProperties agentProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void restoreSavedConfiguration() {
        try {
            SystemAiModelConfig config = configService.getConfig();
            String apiKey = Boolean.TRUE.equals(config.getEnabled()) ? configService.decryptApiKey(config) : null;
            if (AgentModelConfigurationSelector.isUsable(config, apiKey)) {
                enterpriseChatLanguageModel.refreshFromConfig(config, apiKey);
                log.info("AI model source=administrator override: modelName={}", config.getModelName());
                return;
            }

            SystemAiModelConfig yamlConfig = AgentModelConfigurationSelector.defaultAgentConfig(agentProperties);
            if (yamlConfig != null) {
                enterpriseChatLanguageModel.refreshFromConfig(yamlConfig, agentProperties.getApiKey());
                log.info("AI model source=default agent configuration: modelName={}",
                        yamlConfig.getModelName());
                return;
            }

            enterpriseChatLanguageModel.refreshFromConfig(config, apiKey);
        } catch (Exception exception) {
            log.warn("Failed to apply an AI model configuration", exception);
        }
    }
}
