package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.workflow.AbilityLevelPolicy;
import com.example.matching.mapper.workflow.AbilityLevelPolicyMapper;
import com.example.matching.service.assessment.AbilityLevelPolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 能力等级确认策略服务实现
 * <p>
 * 从 ability_level_policy 表读取生效策略；无配置时回退内置默认策略。
 *
 * @author system
 */
@Slf4j
@Service
public class AbilityLevelPolicyServiceImpl implements AbilityLevelPolicyService {

    /** 默认策略版本 */
    public static final String DEFAULT_POLICY_VERSION = "level-confirmation-v1";

    private final AbilityLevelPolicyMapper policyMapper;
    private final ObjectMapper objectMapper;

    public AbilityLevelPolicyServiceImpl(AbilityLevelPolicyMapper policyMapper, ObjectMapper objectMapper) {
        this.policyMapper = policyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public LevelPolicy getActivePolicy() {
        List<AbilityLevelPolicy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<AbilityLevelPolicy>()
                        .eq(AbilityLevelPolicy::getEnabled, 1)
                        .orderByDesc(AbilityLevelPolicy::getEffectiveFrom)
                        .last("LIMIT 1"));
        if (policies.isEmpty()) {
            return defaultPolicy();
        }
        LevelPolicy policy = parse(policies.get(0));
        return policy != null ? policy : defaultPolicy();
    }

    @Override
    public LevelPolicy getPolicy(String version) {
        if (version == null || version.isBlank()) {
            return getActivePolicy();
        }
        AbilityLevelPolicy policy = policyMapper.selectOne(
                new LambdaQueryWrapper<AbilityLevelPolicy>()
                        .eq(AbilityLevelPolicy::getPolicyVersion, version)
                        .last("LIMIT 1"));
        if (policy == null) {
            log.warn("策略版本不存在，回退默认: {}", version);
            return defaultPolicy();
        }
        LevelPolicy parsed = parse(policy);
        return parsed != null ? parsed : defaultPolicy();
    }

    private LevelPolicy parse(AbilityLevelPolicy policy) {
        try {
            Map<String, Object> config = objectMapper.readValue(policy.getConfigJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return new LevelPolicy(
                    policy.getPolicyVersion(),
                    policy.getPolicyName(),
                    intOf(config.get("conflictThreshold"), 2),
                    intOf(config.get("level4MinIndependentSources"), 2),
                    doubleOf(config.get("highCredibilityThreshold"), 0.20),
                    decimalOf(config.get("autoConfirmWeightThreshold"), new BigDecimal("0.30")),
                    decimalOf(config.get("reviewWeightThreshold"), new BigDecimal("0.15")),
                    ceilingMapOf(config.get("singleSourceLevelCeiling")));
        } catch (Exception e) {
            log.error("策略配置解析失败，回退默认: version={}, error={}", policy.getPolicyVersion(), e.getMessage());
            return null;
        }
    }

    private LevelPolicy defaultPolicy() {
        return new LevelPolicy(
                DEFAULT_POLICY_VERSION,
                "默认等级确认策略",
                2,
                2,
                0.20,
                new BigDecimal("0.30"),
                new BigDecimal("0.15"),
                Map.of(
                        "RESUME_PARSE", 2,
                        "AI_TEST", 3,
                        "AI_INTERVIEW", 3));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> ceilingMapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()),
                        entry.getValue() instanceof Number n ? n.intValue() : 3);
            }
            return result;
        }
        return defaultPolicy().getSingleSourceLevelCeiling();
    }

    private int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private double doubleOf(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private BigDecimal decimalOf(Object value, BigDecimal fallback) {
        return value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : fallback;
    }
}
