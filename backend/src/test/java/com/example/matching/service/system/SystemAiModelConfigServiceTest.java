package com.example.matching.service.system;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.infrastructure.llm.EnterpriseVisionChatModel;
import com.example.matching.mapper.system.SystemAiModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
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
        EnterpriseVisionChatModel visionModel = mock(EnterpriseVisionChatModel.class);
        SystemAiModelConfig custom = new SystemAiModelConfig();
        custom.setId(1L);
        custom.setEnabled(true);
        custom.setBaseUrl("https://custom.example/v1");
        custom.setModelName("custom-model");
        custom.setApiKeyCiphertext("encrypted");
        when(mapper.selectById(1L)).thenReturn(custom);

        SystemAiModelConfigService service =
                new SystemAiModelConfigService(mapper, keyCipher, model, visionModel);
        LangChain4jAgentProperties defaults = new LangChain4jAgentProperties();
        defaults.setEnabled(true);
        defaults.setBaseUrl("https://api.deepseek.com");
        defaults.setApiKey("dev-key");
        defaults.setModelName("deepseek-v4-flash");
        defaults.setVisionModelName("deepseek-v4.1-flash");
        ReflectionTestUtils.setField(service, "agentProperties", defaults);

        SystemAiModelConfig update = new SystemAiModelConfig();
        update.setEnabled(false);
        service.saveConfig(update, 1L);

        verify(model).refreshFromConfig(argThat(config -> config != null
                && Boolean.TRUE.equals(config.getEnabled())
                && "deepseek-v4-flash".equals(config.getModelName())), eq("dev-key"));
        // 视觉模型必须与文本模型同步刷新，避免出现「文本已切换、视觉仍指向旧端点」的不一致状态
        verify(visionModel).refreshFromProperties(any(LangChain4jAgentProperties.class), eq("deepseek-v4.1-flash"));
    }

    @Test
    void customDatabaseConfigRefreshesBothTextAndVisionModels() {
        SystemAiModelConfigMapper mapper = mock(SystemAiModelConfigMapper.class);
        AiModelKeyCipher keyCipher = mock(AiModelKeyCipher.class);
        EnterpriseChatLanguageModel model = mock(EnterpriseChatLanguageModel.class);
        EnterpriseVisionChatModel visionModel = mock(EnterpriseVisionChatModel.class);
        SystemAiModelConfig custom = new SystemAiModelConfig();
        custom.setId(1L);
        custom.setEnabled(true);
        custom.setBaseUrl("https://custom.example/v1");
        custom.setModelName("custom-model");
        custom.setApiKeyCiphertext("encrypted");
        when(mapper.selectById(1L)).thenReturn(custom);
        when(keyCipher.isAvailable()).thenReturn(true);
        when(keyCipher.encrypt("new-plain-key")).thenReturn("re-encrypted");
        when(keyCipher.decrypt("re-encrypted")).thenReturn("new-plain-key");

        SystemAiModelConfigService service =
                new SystemAiModelConfigService(mapper, keyCipher, model, visionModel);
        LangChain4jAgentProperties defaults = new LangChain4jAgentProperties();
        defaults.setEnabled(true);
        defaults.setBaseUrl("https://api.deepseek.com");
        defaults.setApiKey("dev-key");
        defaults.setModelName("deepseek-v4-flash");
        defaults.setVisionModelName("deepseek-v4.1-flash");
        ReflectionTestUtils.setField(service, "agentProperties", defaults);

        SystemAiModelConfig update = new SystemAiModelConfig();
        update.setEnabled(true);
        update.setBaseUrl("https://custom.example/v1");
        update.setModelName("custom-model");
        // PUT 传入的明文密钥经 keyCipher 加密后写入 apiKeyCiphertext
        update.setApiKeyCiphertext("new-plain-key");
        service.saveConfig(update, 1L);

        // 后台配置可用时，文本与视觉模型都必须以「同一份后台配置 + 各自模型名」刷新
        verify(model).refreshFromConfig(argThat(config -> config != null
                && "custom-model".equals(config.getModelName())), eq("new-plain-key"));
        verify(visionModel).refreshFromConfig(argThat(config -> config != null
                && "custom-model".equals(config.getModelName())), eq("new-plain-key"), eq("deepseek-v4.1-flash"));
    }
}
