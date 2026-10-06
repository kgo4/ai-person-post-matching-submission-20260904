package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位模型质量评分表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_model_quality")
public class PostModelQuality implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 模型版本号 */
    private String modelVersion;

    /** 综合质量评分，0.00-100.00 */
    private BigDecimal qualityScore;

    /** 权重完整度评分，0.00-100.00 */
    private BigDecimal weightCompleteness;

    /** 核心项清晰度评分，0.00-100.00 */
    private BigDecimal coreClarity;

    /** 标签覆盖度评分（技术/业务/软技能），0.00-100.00 */
    private BigDecimal coverageScore;

    /** 岗位JD是否存在：0否，1是 */
    private Integer jdExists;

    /** 质量检查详情，JSON格式 */
    private String qualityDetail;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
