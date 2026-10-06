package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位硬性条件规则。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_hard_condition_rule")
public class PostHardConditionRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 员工字段名：内置字段或员工扩展字段 fieldName */
    private String fieldName;

    /** 展示名称 */
    private String fieldLabel;

    /** 字段类型：text/number/select/date 等 */
    private String fieldType;

    /** 操作符：eq/neq/in/notin/contains/gte/lte/gt/lt */
    private String operator;

    /** 期望值，多个值用英文逗号分隔 */
    private String expectedValue;

    /** 枚举等级映射JSON，仅字段类型为rank时使用 */
    private String valueRankJson;

    /** 是否启用：0否，1是 */
    private Integer enabled;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Version
    private Integer version;
}
