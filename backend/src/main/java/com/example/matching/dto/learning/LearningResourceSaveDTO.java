package com.example.matching.dto.learning;

import lombok.Data;

/**
 * 学习资源保存DTO
 *
 * @author system
 */
@Data
public class LearningResourceSaveDTO {

    private Long id;
    private String abilityName;
    private Long tagId;
    private String title;
    private String resourceType;
    private Integer difficultyLevel;
    private String url;
    private String description;
    /** 资源平台：MOOC/BILIBILI/YOUTUBE/GITHUB/CSDN/OTHER */
    private String platform;
    /** 平台图标标识 */
    private String platformIcon;
    /** 封面图URL */
    private String coverImageUrl;
    /** 学习时长描述 */
    private String duration;
    /** 排序权重 */
    private Integer sortOrder;
}
