package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 扩展字段元数据表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_extend_field")
public class SysExtendField implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务模块：EMPLOYEE-员工，POST-岗位，ABILITY-能力 */
    private String businessModule;

    /** 字段名称，用于JSON key */
    private String fieldName;

    /** 字段显示标签 */
    private String fieldLabel;

    /** 字段类型：STRING-文本，TEXT-长文本，NUMBER-数字，DATE-日期，SELECT-下拉 */
    private String fieldType;

    /** 下拉选项，仅SELECT类型存储，格式：[{"value":"1","label":"选项1"}] */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String selectOptions;

    /** 是否必填：0否，1是 */
    private Integer isRequired;

    /** 排序字段 */
    private Integer sortOrder;

    /** 状态：0停用，1启用 */
    private Integer status;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
