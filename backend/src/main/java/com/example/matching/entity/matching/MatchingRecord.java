package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.matching.dto.matching.MatchDetailDTO;

/**
 * 人岗匹配记录表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_record")
public class MatchingRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 匹配批次号 */
    private String batchNo;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 员工姓名（关联查询填充） */
    @TableField(exist = false)
    private String empName;

    /** 岗位名称（关联查询填充） */
    @TableField(exist = false)
    private String postName;

    /** 匹配时使用的岗位模型版本号 */
    private String postModelVersion;

    /** AI匹配度（最终综合分），0.00-100.00 */
    private BigDecimal aiMatchScore;

    /** 向量语义匹配分，0.00-100.00 */
    private BigDecimal vectorScore;

    /** L2标签加权分，0.00-100.00 */
    private BigDecimal l2Score;

    /** AI建议分，0.00-100.00 */
    private BigDecimal aiScore;

    /** 岗位能力模型匹配分（L2标签分），0.00-100.00 */
    private BigDecimal postModelScore;

    /** 整人×整岗语义相似度分（从 L2 拆出的独立维度），0.00-100.00 */
    @TableField(exist = false)
    private BigDecimal profileSemanticScore;

    /** 证据可信度分（能力来源可信度 × 时间衰减），0.00-100.00 */
    @TableField(exist = false)
    private BigDecimal evidenceScore;

    /** 证据可信度分（持久化），0.00-100.00 */
    private BigDecimal evidenceCredibilityScore;

    /** 证据覆盖分（岗位能力覆盖度），0.00-100.00 */
    private BigDecimal evidenceCoverageScore;

    @TableField(exist = false)
    private BigDecimal rankScore;

    @TableField(exist = false)
    private BigDecimal qualityAdjustment;

    @TableField(exist = false)
    private BigDecimal feedbackAdjustment;

    @TableField(exist = false)
    private BigDecimal calibrationAdjustment;

    /** LLM建议分（L3 AI分），0.00-100.00 */
    private BigDecimal llmScore;

    /** RAG知识库匹配分，0.00-100.00 */
    private BigDecimal ragScore;

    /** 岗位模型质量系数，0.00-100.00 */
    private BigDecimal modelQualityCoefficient;

    /** 人工反馈校准值，-10.00至+10.00 */
    private BigDecimal feedbackCalibration;

    /** 最终匹配度（人工调整） */
    private BigDecimal finalMatchScore;

    /** 匹配状态：0待审核，1强适配，2适配，3待观察，4不适配 */
    private Integer matchStatus;

    /** 通过的筛选级别：1=L1硬性条件通过, 2=L2能力标签通过, 3=L3 AI深度匹配完成 */
    private Integer screeningLevel;

    /** 黑白名单强制标记：null=未命中, 1=白名单强制100分, 2=黑名单强制0分 */
    private Integer forcedByList;

    /** 硬性条件检查结果，JSON格式 */
    private String hardConditionResult;

    /** 量化分析报告（含能力维度详情），JSON格式 */
    private String quantitativeReport;

    /** AI生成的结构化分析报告，JSON格式 */
    private String aiAnalysisReport;

    /** 人工备注 */
    private String manualRemark;

    // ===== 分层评估字段（V49新增） =====

    /** 权重方案版本（评估时使用的版本标识） */
    private String weightProfileVersion;

    /** 权重快照JSON（评估时使用的精确权重） */
    private String weightSnapshotJson;

    /** 系统评分分解JSON（各维度详情） */
    private String scoreBreakdownJson;

    /** 人工修正评分分解JSON（复核后填充） */
    private String manualBreakdownJson;

    /** 向量语义分是否缺失：0-正常，1-缺失 */
    private Integer semanticMissing;

    /**
     * 当前评分过程生成的能力匹配明细，仅用于报告组装，不写入 matching_record。
     * 避免报告阶段再次调用语义匹配和 embedding 服务。
     */
    @TableField(exist = false)
    private List<MatchDetailDTO> matchDetails;

    // ===== 瞬态字段（不持久化） =====

    /** 结构化反馈原因（JSON数组，仅用于传递，不持久化到matching_record表） */
    @TableField(exist = false)
    private String feedbackReasons;

    /** 人工反馈补充说明（仅用于传递，不持久化到matching_record表） */
    @TableField(exist = false)
    private String feedbackComment;

    /** 审批状态：0未发起，1审批中，2审批通过，3审批驳回 */
    private Integer approvalStatus;

    /** 是否锁定：0否，1是 */
    private Integer isLocked;

    /** 锁定人ID */
    private Long lockedBy;

    /** 锁定时间 */
    private LocalDateTime lockedTime;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID（匹配发起人） */
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

    /** AI评分状态：PENDING/PROCESSING/SKIPPED/COMPLETED/FAILED */
    private String aiScoringStatus;

    /** AI评分失败原因 */
    private String aiScoringFailReason;

    /** AI评分尝试次数 */
    private Integer aiScoringAttemptCount;

    /** AI评分上次尝试时间 */
    private LocalDateTime aiScoringLastAttemptAt;

    /** AI评分下次重试时间 */
    private LocalDateTime aiScoringNextRetryAt;

    // ===== 临时能力匹配字段 =====

    /** 是否使用了临时能力 */
    private Integer usedProvisionalAbilities;

    /** 临时能力数量 */
    private Integer provisionalAbilityCount;

    /** 临时能力快照JSON */
    private String provisionalSnapshotJson;

    /** 临时能力风险标记JSON */
    private String provisionalRiskFlagsJson;
}
