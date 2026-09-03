package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.infrastructure.llm.AgentModelConfigurationSelector;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.mapper.system.SystemAiModelConfigMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局企业 AI 模型配置服务（单行，id=1）。
 * <p>
 * GET 永不返回 apiKey，只返回 apiKeyConfigured；PUT 未传 apiKey 时保留旧密钥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAiModelConfigService {

    private static final Long SINGLE_ROW_ID = 1L;

    private final SystemAiModelConfigMapper configMapper;
    private final AiModelKeyCipher keyCipher;
    private final EnterpriseChatLanguageModel enterpriseChatLanguageModel;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LangChain4jAgentProperties agentProperties;

    public SystemAiModelConfig getConfig() {
        SystemAiModelConfig config = configMapper.selectById(SINGLE_ROW_ID);
        if (config == null) {
            config = new SystemAiModelConfig();
            config.setId(SINGLE_ROW_ID);
            config.setEnabled(false);
            config.setTimeoutSeconds(300);
            config.setTemperature(java.math.BigDecimal.valueOf(0.2d));
            config.setTestQuestionCount(5);
            config.setInterviewQuestionCount(6);
            applyClusterDefaults(config);
            configMapper.insert(config);
        }
        return config;
    }

    public SystemAiModelConfig getConfigSafe() {
        SystemAiModelConfig config = getConfig();
        config.setApiKeyCiphertext(null);
        return config;
    }

    public boolean isApiKeyConfigured() {
        String ciphertext = getConfig().getApiKeyCiphertext();
        return ciphertext != null && !ciphertext.isBlank();
    }

    /** 解密已保存的模型密钥（供启动恢复与健康检查使用）。 */
    public String decryptApiKey(SystemAiModelConfig config) {
        return keyCipher.decrypt(config.getApiKeyCiphertext());
    }

    /**
     * 保存配置：PUT 未传 apiKey 时保留旧密钥；保存后原子替换内部模型实例。
     */
    @Transactional
    public SystemAiModelConfig saveConfig(SystemAiModelConfig input, Long operatorId) {
        SystemAiModelConfig current = getConfig();
        if (input.getApiKeyCiphertext() != null && !input.getApiKeyCiphertext().isBlank()) {
            if (!keyCipher.isAvailable()) {
                throw new IllegalStateException(
                        "AI model deployment key is not configured; refusing to store plaintext api key. "
                                + "Set environment variable AI_MODEL_DEPLOYMENT_KEY first.");
            }
            current.setApiKeyCiphertext(keyCipher.encrypt(input.getApiKeyCiphertext()));
        }
        if (input.getEnabled() != null) {
            current.setEnabled(input.getEnabled());
        }
        if (input.getBaseUrl() != null && !input.getBaseUrl().isBlank()) {
            current.setBaseUrl(input.getBaseUrl());
        }
        if (input.getModelName() != null && !input.getModelName().isBlank()) {
            current.setModelName(input.getModelName());
        }
        if (input.getTimeoutSeconds() != null) {
            current.setTimeoutSeconds(validateTimeoutSeconds(input.getTimeoutSeconds()));
        }
        if (input.getTemperature() != null) {
            current.setTemperature(input.getTemperature());
        }
        if (input.getTestQuestionCount() != null) {
            current.setTestQuestionCount(validateTestQuestionCount(input.getTestQuestionCount()));
        }
        if (input.getInterviewQuestionCount() != null) {
            current.setInterviewQuestionCount(validateInterviewQuestionCount(input.getInterviewQuestionCount()));
        }
        if (input.getPostAbilityClusterMinMemberCount() != null) {
            current.setPostAbilityClusterMinMemberCount(validateClusterCount(input.getPostAbilityClusterMinMemberCount(), "最少簇成员数"));
        }
        if (input.getPostAbilityClusterMinPostCount() != null) {
            current.setPostAbilityClusterMinPostCount(validateClusterCount(input.getPostAbilityClusterMinPostCount(), "最少覆盖岗位数"));
        }
        if (input.getPostAbilityClusterJoinSimilarity() != null) {
            current.setPostAbilityClusterJoinSimilarity(validateSimilarity(input.getPostAbilityClusterJoinSimilarity(), "加入相似度"));
        }
        if (input.getPostAbilityClusterPromotionCohesion() != null) {
            current.setPostAbilityClusterPromotionCohesion(validateSimilarity(input.getPostAbilityClusterPromotionCohesion(), "提升内聚度"));
        }
        applyClusterDefaults(current);
        current.setUpdatedBy(operatorId);
        current.setUpdatedTime(LocalDateTime.now());
        configMapper.updateById(current);

        applyPreferredModel(current);
        log.info("Enterprise AI model config updated: enabled={}, modelName={}, apiKeyConfigured={}",
                current.getEnabled(), current.getModelName(), isApiKeyConfigured());
        return getConfigSafe();
    }

    public int getTestQuestionCount() {
        Integer count = getConfig().getTestQuestionCount();
        return count == null ? 5 : validateTestQuestionCount(count);
    }

    public int getInterviewQuestionCount() {
        Integer count = getConfig().getInterviewQuestionCount();
        return count == null ? 6 : validateInterviewQuestionCount(count);
    }

    private int validateTestQuestionCount(int count) {
        if (count < 3 || count > 10) {
            throw new IllegalArgumentException("AI测试题目数量必须在3到10之间");
        }
        return count;
    }

    private int validateInterviewQuestionCount(int count) {
        if (count < 3 || count > 10) {
            throw new IllegalArgumentException("AI面试题目数量必须在3到10之间");
        }
        return count;
    }

    private int validateTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds < 5 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("AI request timeout must be between 5 and 300 seconds");
        }
        return timeoutSeconds;
    }

    private void applyClusterDefaults(SystemAiModelConfig config) {
        if (config.getPostAbilityClusterMinMemberCount() == null) config.setPostAbilityClusterMinMemberCount(3);
        if (config.getPostAbilityClusterMinPostCount() == null) config.setPostAbilityClusterMinPostCount(2);
        if (config.getPostAbilityClusterJoinSimilarity() == null) config.setPostAbilityClusterJoinSimilarity(new java.math.BigDecimal("0.82"));
        if (config.getPostAbilityClusterPromotionCohesion() == null) config.setPostAbilityClusterPromotionCohesion(new java.math.BigDecimal("0.80"));
    }

    private int validateClusterCount(int value, String label) {
        if (value < 1 || value > 100) throw new IllegalArgumentException(label + "必须在1到100之间");
        return value;
    }

    private java.math.BigDecimal validateSimilarity(java.math.BigDecimal value, String label) {
        if (value.compareTo(java.math.BigDecimal.ZERO) < 0 || value.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + "必须在0到1之间");
        }
        return value;
    }

    private void applyPreferredModel(SystemAiModelConfig customConfig) {
        String customApiKey = Boolean.TRUE.equals(customConfig.getEnabled())
                ? keyCipher.decrypt(customConfig.getApiKeyCiphertext()) : null;
        if (AgentModelConfigurationSelector.isUsable(customConfig, customApiKey)) {
            enterpriseChatLanguageModel.refreshFromConfig(customConfig, customApiKey);
            return;
        }
        SystemAiModelConfig defaultConfig = AgentModelConfigurationSelector.defaultAgentConfig(agentProperties);
        if (defaultConfig != null) {
            enterpriseChatLanguageModel.refreshFromConfig(defaultConfig, agentProperties.getApiKey());
            return;
        }
        enterpriseChatLanguageModel.refreshFromConfig(customConfig, customApiKey);
    }

    /**
     * 健康检查：只检查连通性和模型响应，不将真实业务文本发送到外部。
     */
    public Map<String, Object> healthCheck() {
        boolean enabled = Boolean.TRUE.equals(getConfig().getEnabled());
        if (!enabled) {
            return Map.of("ok", false, "enabled", false, "reason", "MODEL_DISABLED");
        }
        if (!isApiKeyConfigured()) {
            return Map.of("ok", false, "enabled", true, "reason", "API_KEY_NOT_CONFIGURED");
        }
        try {
            OpenAiChatModel probe = enterpriseChatLanguageModel.buildProbeModel();
            String reply = probe.chat("Reply with the single word: ok");
            boolean ok = reply != null && reply.trim().toLowerCase().contains("ok");
            return Map.of("ok", ok, "enabled", true, "model", String.valueOf(getConfig().getModelName()));
        } catch (Exception e) {
            log.warn("Enterprise AI model health check failed: {}", e.getMessage());
            return Map.of("ok", false, "enabled", true, "reason", "CONNECTION_FAILED", "error", e.getMessage());
        }
    }
}
