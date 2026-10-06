package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 反馈原因枚举
 * <p>
 * 定义人工修改AI匹配结果时可选的结构化原因。
 * 后续校准时可分别作用到岗位侧、人员侧、AI侧。
 */
@Getter
public enum FeedbackReasonEnum {

    /** 岗位模型配置不准 → 作用到岗位侧：调整岗位能力权重建议 */
    POST_MODEL_INACCURATE("POST_MODEL_INACCURATE", "岗位模型配置不准", "岗位侧"),

    /** 人员能力来源不可信 → 作用到人员侧：调整来源可信度 */
    ABILITY_SOURCE_UNRELIABLE("ABILITY_SOURCE_UNRELIABLE", "人员能力来源不可信", "人员侧"),

    /** 简历解析高估 → 作用到人员侧：降低简历解析可信度 */
    RESUME_OVERESTIMATED("RESUME_OVERESTIMATED", "简历解析高估", "人员侧"),

    /** AI测试低估 → 作用到人员侧：调整AI测试评估 */
    AI_TEST_UNDERESTIMATED("AI_TEST_UNDERESTIMATED", "AI测试低估", "人员侧"),

    /** 核心能力缺口 → 作用到AI侧：修正LLM评分偏差 */
    CORE_ABILITY_GAP("CORE_ABILITY_GAP", "核心能力缺口", "AI侧"),

    /** 业务经验不匹配 → 作用到AI侧：修正LLM评分偏差 */
    BUSINESS_MISMATCH("BUSINESS_MISMATCH", "业务经验不匹配", "AI侧"),

    /** 人工特殊考虑 → 通用原因 */
    MANUAL_SPECIAL_CONSIDER("MANUAL_SPECIAL_CONSIDER", "人工特殊考虑", "通用");

    /** 原因编码 */
    private final String code;

    /** 原因描述 */
    private final String desc;

    /** 校准作用域 */
    private final String calibrationTarget;

    FeedbackReasonEnum(String code, String desc, String calibrationTarget) {
        this.code = code;
        this.desc = desc;
        this.calibrationTarget = calibrationTarget;
    }

    /**
     * 根据编码获取枚举
     */
    public static FeedbackReasonEnum getByCode(String code) {
        if (code == null) return null;
        for (FeedbackReasonEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
