package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位原型标签关联表实体
 * <p>
 * 记录岗位原型所需的能力标签及其建议权重、等级。
 * 当应用原型到岗位时，这些标签将作为岗位能力模型的初始配置。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_prototype_tag")
public class PostPrototypeTag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位原型ID */
    private Long prototypeId;

    /** 能力标签ID */
    private Long tagId;

    /** 建议权重，0.00-100.00 */
    private BigDecimal weight;

    /** 建议最低要求等级：1-5级 */
    private Integer minRequiredLevel;

    /** 是否核心能力：0否，1是 */
    private Integer isCore;

    /** 是否必备能力：0否，1是 */
    private Integer isRequired;

    /** 排序字段 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
