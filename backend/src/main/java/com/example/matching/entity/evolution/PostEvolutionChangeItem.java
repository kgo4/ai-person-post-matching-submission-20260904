package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位演化变更项实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_evolution_change_item")
public class PostEvolutionChangeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 演化任务ID */
    private Long taskId;

    /** 变更类型：ADDED/REMOVED/UPDATED_LEVEL/UPDATED_WEIGHT/UPDATED_CORE/NO_CHANGE */
    private String changeType;

    /** 能力标签ID */
    private Long tagId;

    /** 能力名称 */
    private String abilityName;

    /** 旧要求等级 */
    private Integer oldLevel;

    /** 新要求等级 */
    private Integer newLevel;

    /** 旧权重 */
    private BigDecimal oldWeight;

    /** 新权重 */
    private BigDecimal newWeight;

    /** 旧核心标志 */
    private Integer oldIsCore;

    /** 新核心标志 */
    private Integer newIsCore;

    /** 证据支持分数 */
    private BigDecimal supportScore;

    /** 支持的RAG分块ID，逗号分隔 */
    private String evidenceChunkIds;

    /** 数据来源类型：RAG_CHUNK/JD_TEXT/PROTOTYPE/MANUAL */
    private String sourceType;

    /** 数据来源引用（如知识文档ID、JD文本哈希等） */
    private String sourceRef;

    /** 数据来源详情（如文档标题、JD来源平台等） */
    private String sourceDetail;

    /** 变更类型扩展：ADD_ABILITY/REMOVE_ABILITY/UPGRADE_LEVEL/DOWNGRADE_LEVEL/INCREASE_WEIGHT/DECREASE_WEIGHT/ADD_TASK/REMOVE_TASK/ADD_TOOL/REMOVE_TOOL */
    private String changeTypeExtended;

    /** 证据文本 */
    private String evidenceText;

    /** 多来源引用JSON数组 */
    private String sourceRefsJson;

    /** M4: 变更指纹（postId:tagId:changeType:sourceRef 的 MD5），用于同信号去重与冷却窗口 */
    private String fingerprint;

    /** 置信度评分：0-100 */
    private BigDecimal confidenceScore;

    /** 时效性评分：0-100 */
    private BigDecimal freshnessScore;

    /** 权威度评分：0-100 */
    private BigDecimal authorityScore;

    /** 跨来源评分：0-100 */
    private BigDecimal crossSourceScore;

    /** Harness 决策：PASS/REVIEW/BLOCK */
    private String harnessDecision;

    /** 风险等级：LOW/MEDIUM/HIGH */
    private String riskLevel;

    /** 确认状态：PENDING/APPROVED/REJECTED */
    private String confirmStatus;

    /** 人工审核意见 */
    private String reviewComment;

    /** 治理准入记录ID（变更项对应的 PASS/REVIEW 准入） */
    private Long governanceAdmissionId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 乐观锁版本，避免两个审核人覆盖同一变更项的确认结果。 */
    @Version
    private Integer version;
}
