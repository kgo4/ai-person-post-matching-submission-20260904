package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习路径步骤实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_path_step")
public class LearningPathStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属计划ID */
    private Long planId;

    /** 关联能力标签ID */
    private Long abilityTagId;

    /** 能力名称 */
    private String abilityName;

    /** 当前等级 */
    private Integer currentLevel;

    /** 目标等级 */
    private Integer targetLevel;

    /** 差距类型：LEVEL_GAP/MISSING/EVIDENCE_WEAK */
    private String gapType;

    /** 优先级：HIGH/MEDIUM/LOW */
    private String priority;

    /** 步骤标题 */
    private String stepTitle;

    /** 步骤描述 */
    private String stepDescription;

    /** 预估学时(小时) */
    private Integer estimatedHours;

    /** 状态：PENDING/IN_PROGRESS/COMPLETED/SKIPPED */
    private String status;

    /** 排序序号 */
    private Integer sortOrder;

    /** 证据状态 */
    private String evidenceStatus;

    /** 来源引用JSON */
    private String sourceRefsJson;

    /** 推荐学习资源ID（来自 learning_resource，abilityName 主关联） */
    private Long resourceId;

    /** 推荐资源标题 */
    private String resourceTitle;

    /** 推荐资源链接 */
    private String resourceUrl;

    /** 推荐资源类型：COURSE/DOC/PRACTICE/PROJECT/BOOK/VIDEO */
    private String resourceType;

    /** 该能力匹配到的启用资源总数 */
    private Integer resourceCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;
}
