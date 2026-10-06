package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习资源实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_resource")
public class LearningResource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 资源编码，稳定可读ID */
    private String resourceCode;

    /** 关联能力名称 */
    private String abilityName;

    /** 关联能力标签ID */
    private Long tagId;

    /** 资源标题 */
    private String title;

    /** 资源类型：COURSE/DOC/PRACTICE/PROJECT/BOOK/VIDEO */
    private String resourceType;

    /** 难度等级 1-5 */
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

    /** 学习时长描述，如：约8小时 */
    private String duration;

    /** 同一能力下的资源排序权重，越小越靠前 */
    private Integer sortOrder;

    /** 状态：0禁用，1启用 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
