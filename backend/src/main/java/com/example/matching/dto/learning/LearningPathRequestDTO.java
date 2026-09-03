package com.example.matching.dto.learning;

import lombok.Data;

import java.util.List;

/**
 * 学习路径请求DTO
 *
 * @author system
 */
@Data
public class LearningPathRequestDTO {

    /** 需要学习的能力名称列表 */
    private List<String> abilityNames;

    /** 需要学习的能力标签ID列表（与abilityNames一一对应，优先使用tagId精确匹配） */
    private List<Long> tagIds;

    /** 当前等级（可选） */
    private Integer currentLevel;

    /** 目标等级（可选） */
    private Integer targetLevel;
}
