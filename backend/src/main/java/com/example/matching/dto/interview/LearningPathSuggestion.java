package com.example.matching.dto.interview;

/**
 * 学习路径建议
 *
 * @author system
 */
public record LearningPathSuggestion(
        /** 能力标签ID */
        Long tagId,
        /** 能力名称 */
        String abilityName,
        /** 当前等级（1-5） */
        Integer currentLevel,
        /** 目标等级（1-5） */
        Integer targetLevel,
        /** 建议内容 */
        String suggestion,
        /** 优先级：HIGH/MEDIUM/LOW */
        String priority
) {}
