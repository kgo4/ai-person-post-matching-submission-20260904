package com.example.matching.application.system;

import com.example.matching.dto.system.api.SystemAiModelConfigDTO;
import com.example.matching.entity.system.SystemAiModelConfig;
import com.example.matching.service.system.SystemAiModelConfigService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemAiModelConfigApiFacade {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemAiModelConfigService configService;

    public SystemAiModelConfigDTO getConfig() {
        return toDto(configService.getConfigSafe());
    }

    public SystemAiModelConfigDTO saveConfig(SystemAiModelConfigDTO dto) {
        SystemAiModelConfig input = new SystemAiModelConfig();
        input.setEnabled(dto.enabled());
        input.setBaseUrl(dto.baseUrl());
        input.setModelName(dto.modelName());
        input.setApiKeyCiphertext(dto.apiKey());
        input.setTimeoutSeconds(dto.timeoutSeconds());
        input.setTemperature(dto.temperature());
        input.setTestQuestionCount(dto.testQuestionCount());
        input.setInterviewQuestionCount(dto.interviewQuestionCount());
        input.setPostAbilityClusterMinMemberCount(dto.postAbilityClusterMinMemberCount());
        input.setPostAbilityClusterMinPostCount(dto.postAbilityClusterMinPostCount());
        input.setPostAbilityClusterJoinSimilarity(dto.postAbilityClusterJoinSimilarity());
        input.setPostAbilityClusterPromotionCohesion(dto.postAbilityClusterPromotionCohesion());
        Long operatorId = null;
        try {
            operatorId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignored) {
        }
        return toDto(configService.saveConfig(input, operatorId));
    }

    public Map<String, Object> healthCheck() {
        return configService.healthCheck();
    }

    private SystemAiModelConfigDTO toDto(SystemAiModelConfig config) {
        return new SystemAiModelConfigDTO(
                config.getId(),
                config.getEnabled(),
                config.getBaseUrl(),
                config.getModelName(),
                null,
                configService.isApiKeyConfigured(),
                config.getTimeoutSeconds(),
                config.getTemperature(),
                config.getTestQuestionCount(),
                config.getInterviewQuestionCount(),
                config.getPostAbilityClusterMinMemberCount(),
                config.getPostAbilityClusterMinPostCount(),
                config.getPostAbilityClusterJoinSimilarity(),
                config.getPostAbilityClusterPromotionCohesion(),
                config.getUpdatedBy(),
                config.getUpdatedTime() != null ? config.getUpdatedTime().format(TIME_FORMAT) : null
        );
    }
}
