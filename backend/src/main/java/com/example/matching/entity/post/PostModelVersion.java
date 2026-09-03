package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位能力模型版本主表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_model_version")
public class PostModelVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 版本号，格式：vyyyyMMddHHmmss */
    private String versionNo;

    /** 来源类型：TEMPLATE, JD_AI, EXCEL, COPY, MANUAL, FEEDBACK */
    private String sourceType;

    /** 状态：DRAFT, ACTIVE, ARCHIVED */
    private String status;

    /** 质量评分（0-100） */
    private BigDecimal qualityScore;

    /** 能力项数量 */
    private Integer itemCount;

    /** 权重总和 */
    private BigDecimal totalWeight;

    /** 版本说明 */
    private String description;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
