package com.example.matching.dto.interview;

import java.math.BigDecimal;

/**
 * 能力雷达图数据项
 *
 * @author system
 */
public record AbilityRadarItem(
        /** 能力标签ID */
        Long tagId,
        /** 能力名称 */
        String abilityName,
        /** 观察到的等级（1-5） */
        Integer observedLevel,
        /** 岗位要求等级（1-5） */
        Integer requiredLevel,
        /** 置信度评分（0-100） */
        BigDecimal confidenceScore,
        /** Harness决策 */
        String harnessDecision,
        /** 能力评分（0-100，observedLevel * 20） */
        int score
) {}
