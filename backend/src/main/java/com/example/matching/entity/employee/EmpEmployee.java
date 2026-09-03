package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工基础信息表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_employee")
public class EmpEmployee implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工工号，唯一 */
    private String empCode;

    /** 真实姓名 */
    private String realName;

    /** 性别：0女，1男 */
    private Integer gender;

    /** 身份证号，加密存储 */
    private String idCard;

    /** 手机号，加密存储 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属部门ID */
    private Long departmentId;

    /** 当前岗位ID */
    private Long currentPostId;

    /** 入职日期 */
    private LocalDate entryDate;

    /** 职级，如P5/M3 */
    private String level;

    /** 自定义扩展字段，JSON格式 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String extendFields;

    /** 绑定的系统用户ID，移动端登录后用于身份关联 */
    private Long userId;

    /** 是否锁定：0否，1是 */
    private Integer isLocked;

    /** 状态：0离职，1在职 */
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

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
