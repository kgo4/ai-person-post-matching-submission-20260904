package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识领域实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("knowledge_domain")
public class KnowledgeDomain implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 领域编码 */
    private String domainCode;

    /** 领域名称 */
    private String domainName;

    /** 领域图标 */
    private String domainIcon;

    /** 领域颜色 */
    private String domainColor;

    /** 领域权重 */
    private Integer domainWeight;

    /** 领域描述 */
    private String domainDescription;

    /** 父领域ID */
    private Long parentId;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
