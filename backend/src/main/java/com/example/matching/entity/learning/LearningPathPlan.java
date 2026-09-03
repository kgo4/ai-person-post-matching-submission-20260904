package com.example.matching.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习路径计划实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("learning_path_plan")
public class LearningPathPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 关联匹配记录ID */
    private Long matchingRecordId;

    /** 计划标题 */
    private String planTitle;

    /** 计划状态：ACTIVE/COMPLETED/ARCHIVED */
    private String planStatus;

    /** 当前匹配分 */
    private BigDecimal currentScore;

    /** 目标匹配分 */
    private BigDecimal targetScore;

    /** AI生成的摘要 */
    private String aiSummary;

    /** 是否AI生成 0否 1是 */
    private Integer generatedByAi;

    /** 来源引用JSON */
    private String sourceRefsJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 逻辑删除 0未删除 1已删除 */
    @TableLogic
    private Integer isDeleted;
}
