package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位模型未匹配能力标签实体（M-07）
 * <p>
 * AI 从 JD 中提取的能力无法匹配已有 AbilityTag 时，保留在此表，
 * 供管理员查看、绑定已有标签或忽略。
 * <p>
 * 状态流转：PENDING -> TAG_BOUND | IGNORED
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_model_unmatched_ability")
public class PostModelUnmatchedAbility implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未匹配原因：matchedTagId 无效 */
    public static final String REASON_MATCHED_TAG_ID_NOT_FOUND = "MATCHED_TAG_ID_NOT_FOUND";
    /** 未匹配原因：按名称找不到标签 */
    public static final String REASON_TAG_NAME_NOT_FOUND = "TAG_NAME_NOT_FOUND";
    /** 未匹配原因：标签被禁用 */
    public static final String REASON_TAG_DISABLED = "TAG_DISABLED";
    /** 未匹配原因：名称存在歧义 */
    public static final String REASON_TAG_NAME_AMBIGUOUS = "TAG_NAME_AMBIGUOUS";

    /** 状态：待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已绑定正式标签 */
    public static final String STATUS_TAG_BOUND = "TAG_BOUND";
    /** 状态：已忽略 */
    public static final String STATUS_IGNORED = "IGNORED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位模型版本ID */
    private Long versionId;

    /** AI 提取的能力名称 */
    private String abilityName;

    /** 归一化后的能力名称 */
    private String normalizedAbilityName;

    /** 未匹配原因 */
    private String reason;

    /** 建议最低要求等级 1-5 */
    private Integer minRequiredLevel;

    /** 建议权重 0-100 */
    private BigDecimal weight;

    /** 是否必需 0-否 1-是 */
    private Integer isRequired;

    /** 是否核心 0-否 1-是 */
    private Integer isCore;

    /** AI 推理说明 */
    private String reasoning;

    /** 状态：PENDING/TAG_BOUND/IGNORED */
    private String status;

    /** 已创建的标签候选ID */
    private Long candidateId;

    /** 绑定后的正式标签ID */
    private Long boundTagId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
