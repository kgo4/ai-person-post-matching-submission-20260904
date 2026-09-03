package com.example.matching.application.agent;

import com.example.matching.common.enums.AbilitySourceType;

/**
 * Source of a capability claim.
 * Each source has different reliability characteristics used by the governance layer.
 */
public enum ClaimSource {

    RESUME_PARSE("简历解析", 0.15),
    AI_TEST("AI测试", 0.20),
    AI_PROJECT("AI项目分析", 0.30),
    LEARNING_PROJECT("学习项目", 0.10),
    MANUAL("人工录入", 0.10),
    AI_INTERVIEW("AI面试", 0.25),
    PERFORMANCE("绩效", 0.30),
    PROFILE_FUSED("融合画像", 0.25);

    private final String displayName;
    private final double defaultWeight;

    ClaimSource(String displayName, double defaultWeight) {
        this.displayName = displayName;
        this.defaultWeight = defaultWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    /**
     * Converts persisted legacy values to their canonical source.
     */
    public static ClaimSource fromString(String sourceType) {
        return valueOf(AbilitySourceType.canonicalize(sourceType));
    }
}
