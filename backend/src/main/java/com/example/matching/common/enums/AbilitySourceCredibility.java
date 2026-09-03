package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 能力来源可信度枚举
 * <p>
 * 不同来源的能力评估数据可信度不同，用于能力证据融合计算。
 * 可信度越高，该来源的数据在融合计算中权重越大。
 */
@Getter
public enum AbilitySourceCredibility {

    /** 绩效/项目结果 - 最可信 */
    PERFORMANCE("PERFORMANCE", 1.00, "绩效/项目结果"),

    /** 手工录入 - 高可信 */
    MANUAL("MANUAL", 0.95, "手工录入"),

    /** AI测试 - 较高可信 */
    AI_TEST("AI_TEST", 0.90, "AI测试"),

    /** AI评估 - 中等可信 */
    /** 简历解析 - 较低可信 */
    RESUME_PARSE("RESUME_PARSE", 0.70, "简历解析"),

    /** AI视频面试 - 较高可信 */
    AI_INTERVIEW(AbilitySourceType.AI_INTERVIEW, 0.88, "AI面试"),

    /** AI项目数据分析 - 中等可信 */
    AI_PROJECT("AI_PROJECT", 0.70, "AI项目数据分析"),

    /** 多源融合画像 - 高可信（已综合多来源证据） */
    PROFILE_FUSED("PROFILE_FUSED", 0.95, "多源融合画像");

    /** 来源标识 */
    private final String source;

    /** 可信度权重（0.00-1.00） */
    private final double weight;

    /** 来源描述 */
    private final String desc;

    AbilitySourceCredibility(String source, double weight, String desc) {
        this.source = source;
        this.weight = weight;
        this.desc = desc;
    }

    /**
     * 根据来源标识获取可信度权重
     *
     * @param source 来源标识（如 "MANUAL", "AI_TEST" 等）
     * @return 可信度权重，未知来源返回 0.50
     */
    public static double getWeightBySource(String source) {
        String canonicalSource = AbilitySourceType.canonicalize(source);
        for (AbilitySourceCredibility e : values()) {
            if (e.getSource().equals(canonicalSource)) {
                return e.getWeight();
            }
        }
        return 0.50; // 未知来源默认中等可信度
    }
}
