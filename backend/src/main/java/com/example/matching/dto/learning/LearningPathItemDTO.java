package com.example.matching.dto.learning;

import lombok.Data;

/**
 * 学习路径项DTO
 *
 * @author system
 */
@Data
public class LearningPathItemDTO {

    /** 能力名称 */
    private String abilityName;

    /** 关联能力标签ID */
    private Long tagId;

    /** 资源ID */
    private Long resourceId;

    /** 资源标题 */
    private String title;

    /** 资源类型 */
    private String resourceType;

    /** 难度等级 */
    private Integer difficultyLevel;

    /** 资源URL */
    private String url;

    /** 资源描述 */
    private String description;

    /** 资源平台：MOOC/BILIBILI/YOUTUBE/GITHUB/CSDN/OTHER */
    private String platform;

    /** 平台图标标识 */
    private String platformIcon;

    /** 封面图URL */
    private String coverImageUrl;

    /** 学习时长描述 */
    private String duration;
}
