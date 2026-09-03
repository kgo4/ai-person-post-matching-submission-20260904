package com.example.matching.infrastructure.llm;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.service.system.SystemAiModelConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class EnterpriseAiModelInitializerTest {

    @Test
    void defaultAgentTimeoutIsFiveMinutes() {
        assertThat(new LangChain4jAgentProperties().getTimeoutSeconds()).isEqualTo(300L);
    }

    @Test
    void restoresSavedConfigurationAfterTheApplicationIsReady() {
        SystemAiModelConfigService configService = mock(SystemAiModelConfigService.class);
        EnterpriseChatLanguageModel model = mock(EnterpriseChatLanguageModel.class);
        SystemAiModelConfig config = new SystemAiModelConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://model.example/v1");
        config.setModelName("enterprise-model");
        when(configService.getConfig()).thenReturn(config);
        when(configService.decryptApiKey(config)).thenReturn("decrypted-key");

        new EnterpriseAiModelInitializer(configService, model).restoreSavedConfiguration();

        verify(model).refreshFromConfig(config, "decrypted-key");
    }

    @Test
    void usesAgentYamlConfigurationWhenDatabaseModelIsDisabled() {
        SystemAiModelConfigService configService = mock(SystemAiModelConfigService.class);
        EnterpriseChatLanguageModel model = mock(EnterpriseChatLanguageModel.class);
        SystemAiModelConfig databaseConfig = new SystemAiModelConfig();
        databaseConfig.setEnabled(false);
        when(configService.getConfig()).thenReturn(databaseConfig);
        when(configService.decryptApiKey(databaseConfig)).thenReturn(null);

        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("dev-key");
        properties.setModelName("deepseek-v4-flash");
        properties.setTimeoutSeconds(60);

        EnterpriseAiModelInitializer initializer = new EnterpriseAiModelInitializer(configService, model);
        ReflectionTestUtils.setField(initializer, "agentProperties", properties);

        initializer.restoreSavedConfiguration();

        verify(model).refreshFromConfig(argThat(config -> config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && "deepseek-v4-flash".equals(config.getModelName())), eq("dev-key"));
    }
}
