package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位基础信息表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_post")
public class PostPost implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位编码，唯一 */
    private String postCode;

    /** 岗位名称 */
    private String postName;

    /** 所属部门ID */
    private Long departmentId;

    /** 岗位级别，如P5-P7 */
    private String postLevel;

    /** 岗位职责描述 */
    private String jobDescription;

    /** 关联的岗位模型模板ID */
    private Long templateId;

    /** 自定义扩展字段，JSON格式 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String extendFields;

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

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
