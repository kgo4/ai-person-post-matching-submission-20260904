package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评估阶段类型枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum StageTypeEnum {

    RESUME_PARSE("RESUME_PARSE", "简历解析"),
    RESUME_CLAIM_EXTRACTION("RESUME_CLAIM_EXTRACTION", "简历能力主张提取"),
    AI_TEST_GENERATION("AI_TEST_GENERATION", "AI测试生成"),
    AI_TEST_EVALUATION("AI_TEST_EVALUATION", "AI测试评分"),
    AI_INTERVIEW("AI_INTERVIEW", "AI面试"),
    AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION("AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION", "聚合审核与等级确认"),
    PMS_INCREMENTAL("PMS_INCREMENTAL", "PMS增量");

    private final String code;
    private final String description;

    public static StageTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (StageTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
