package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能力标签与知识领域关联实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_tag_domain_rel")
public class AbilityTagDomainRel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 能力标签ID */
    private Long tagId;

    /** 知识领域ID */
    private Long domainId;

    /** 关联度评分：0-1 */
    private BigDecimal relevanceScore;

    /** 是否为主要领域：0否，1是 */
    private Integer isPrimaryDomain;

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
