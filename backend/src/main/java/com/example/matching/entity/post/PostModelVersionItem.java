package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位能力模型版本明细表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_model_version_item")
public class PostModelVersionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 版本ID，关联 post_model_version.id */
    private Long versionId;

    /** 能力标签ID */
    private Long tagId;

    /** 最低要求等级：1-5级 */
    private Integer minRequiredLevel;

    /** 权重占比，0.00-100.00 */
    private BigDecimal weight;

    /** 是否必填：0否，1是 */
    private Integer isRequired;

    /** 是否核心项：0否，1是 */
    private Integer isCore;

    /** 配置理由（AI生成时填写） */
    private String reason;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
