package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位能力模型表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_ability_model")
public class PostAbilityModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 能力标签ID */
    private Long tagId;

    /** 岗位能力名称；标签库未收录时仍可作为岗位画像能力 */
    private String abilityName;

    /** 技术栈；岗位全景图谱按此字段聚合，不依赖标签分类 */
    private String techStack;

    /** 岗位内技能点规范键，用于消除同岗位重复能力 */
    private String skillPointKey;

    /** 最低要求等级：1-5级 */
    private Integer minRequiredLevel;

    /** 权重占比，0.00-100.00 */
    private BigDecimal weight;

    /** 是否必填：0否，1是 */
    private Integer isRequired;

    /** 是否核心项：0否，1是 */
    private Integer isCore;

    /** 岗位模型版本号，格式：vyyyyMMddHHmmss */
    private String modelVersion;

    /** 备注 */
    private String remark;

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

    /** 治理准入记录ID（AI管道写入必须引用 PASS 准入） */
    private Long governanceAdmissionId;

    /** AI管道来源标记（JD_IMPORT/POST_EVOLUTION 等），非AI写入为 NULL/MANUAL */
    private String sourceType;
}
