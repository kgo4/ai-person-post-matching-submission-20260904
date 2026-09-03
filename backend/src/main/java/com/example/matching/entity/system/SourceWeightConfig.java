package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 来源证据权重配置实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "source_weight_config")
public class SourceWeightConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 统一来源类型：RESUME_PARSE/AI_TEST/AI_PROJECT/AI_INTERVIEW/LEARNING_PROJECT/MANUAL/PERFORMANCE/PROFILE_FUSED */
    private String sourceType;

    /** 来源中文名称 */
    private String sourceLabel;

    /** 权重值 0.00~1.00 */
    private BigDecimal weight;

    /** 是否启用：0禁用 1启用 */
    private Integer isActive;

    /** 排序序号 */
    private Integer sortOrder;

    /** 备注说明 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
