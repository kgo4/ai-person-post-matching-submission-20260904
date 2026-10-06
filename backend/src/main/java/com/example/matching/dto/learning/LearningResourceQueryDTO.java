package com.example.matching.dto.learning;

import lombok.Data;

/**
 * 学习资源查询DTO
 *
 * @author system
 */
@Data
public class LearningResourceQueryDTO {

    private String abilityName;
    private Long tagId;
    private String resourceType;
    private String platform;
    private String keyword;
    private Integer status;
}
