package com.example.matching.service.system;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.mapper.system.SystemAiModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAiModelConfigServiceTest {

    @Test
    void disablingAdministratorOverrideImmediatelyRestoresDefaultAgentModel() {
        SystemAiModelConfigMapper mapper = mock(SystemAiModelConfigMapper.class);
        AiModelKeyCipher keyCipher = mock(AiModelKeyCipher.class);
        EnterpriseChatLanguageModel model = mock(EnterpriseChatLanguageModel.class);
        SystemAiModelConfig custom = new SystemAiModelConfig();
        custom.setId(1L);
        custom.setEnabled(true);
        custom.setBaseUrl("https://custom.example/v1");
        custom.setModelName("custom-model");
        custom.setApiKeyCiphertext("encrypted");
        when(mapper.selectById(1L)).thenReturn(custom);

        SystemAiModelConfigService service = new SystemAiModelConfigService(mapper, keyCipher, model);
        LangChain4jAgentProperties defaults = new LangChain4jAgentProperties();
        defaults.setEnabled(true);
        defaults.setBaseUrl("https://api.deepseek.com");
        defaults.setApiKey("dev-key");
        defaults.setModelName("deepseek-v4-flash");
        ReflectionTestUtils.setField(service, "agentProperties", defaults);

        SystemAiModelConfig update = new SystemAiModelConfig();
        update.setEnabled(false);
        service.saveConfig(update, 1L);

        verify(model).refreshFromConfig(argThat(config -> config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && "deepseek-v4-flash".equals(config.getModelName())), eq("dev-key"));
    }
}
