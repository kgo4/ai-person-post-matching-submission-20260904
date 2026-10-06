package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "匹配记录响应")
public record MatchingRecordResponse(
    @Schema(description = "主键ID") Long id,
    @Schema(description = "匹配批次号") String batchNo,
    @Schema(description = "员工ID") Long empId,
    @Schema(description = "员工姓名") String empName,
    @Schema(description = "岗位ID") Long postId,
    @Schema(description = "岗位名称") String postName,
    @Schema(description = "岗位模型版本号") String postModelVersion,
    @Schema(description = "AI匹配度（最终综合分）") BigDecimal aiMatchScore,
    @Schema(description = "向量语义匹配分") BigDecimal vectorScore,
    @Schema(description = "L2标签加权分") BigDecimal l2Score,
    @Schema(description = "AI建议分") BigDecimal aiScore,
    @Schema(description = "岗位能力模型匹配分") BigDecimal postModelScore,
    @Schema(description = "整人×整岗语义相似度分") BigDecimal profileSemanticScore,
    @Schema(description = "证据可信度分") BigDecimal evidenceScore,
    @Schema(description = "排名分") BigDecimal rankScore,
    @Schema(description = "质量调整") BigDecimal qualityAdjustment,
    @Schema(description = "反馈调整") BigDecimal feedbackAdjustment,
    @Schema(description = "校准调整") BigDecimal calibrationAdjustment,
    @Schema(description = "LLM建议分") BigDecimal llmScore,
    @Schema(description = "RAG知识库匹配分") BigDecimal ragScore,
    @Schema(description = "岗位模型质量系数") BigDecimal modelQualityCoefficient,
    @Schema(description = "人工反馈校准值") BigDecimal feedbackCalibration,
    @Schema(description = "最终匹配度（人工调整）") BigDecimal finalMatchScore,
    @Schema(description = "匹配状态：0待审核，1强适配，2适配，3待观察，4不适配") Integer matchStatus,
    @Schema(description = "通过的筛选级别") Integer screeningLevel,
    @Schema(description = "硬性条件检查结果") String hardConditionResult,
    @Schema(description = "量化分析报告") String quantitativeReport,
    @Schema(description = "AI生成的结构化分析报告") String aiAnalysisReport,
    @Schema(description = "人工备注") String manualRemark,
    @Schema(description = "权重方案版本") String weightProfileVersion,
    @Schema(description = "权重快照JSON") String weightSnapshotJson,
    @Schema(description = "系统评分分解JSON") String scoreBreakdownJson,
    @Schema(description = "人工修正评分分解JSON") String manualBreakdownJson,
    @Schema(description = "向量语义分是否缺失") Integer semanticMissing,
    @Schema(description = "结构化反馈原因") String feedbackReasons,
    @Schema(description = "人工反馈补充说明") String feedbackComment,
    @Schema(description = "审批状态") Integer approvalStatus,
    /**
     * 推送状态：0未推送，1已推送（员工侧可见）。
     *
     * <p>【2026-09-04 修复】此前该 DTO 漏了本字段，而前端「推送状态」列读的正是它，
     * 于是 HR 点推送后列表恒显示「未推送」（数据库其实已写入）——
     * 表现为「点了推送但状态没更新」。与审批状态一样是正交的两个动作，必须一并下发。
     */
    @Schema(description = "推送状态：0未推送，1已推送（员工侧可见）") Integer publishStatus,
    /**
     * 最近一次**已完成的视频终面**结果。
     *
     * <p>1 通过 / 2 未通过 / 3 待定；null = 尚未完成终面（含已约未面、已取消）。
     * 取值与 {@code EmpCommunicationInterview.RESULT_*} 一致。
     *
     * <p>【2026-09-04】原「匹配结果」页的「已通过」统计的是 `approvalStatus == 2`（**审批**通过），
     * 而业务上 HR 关注的是**视频终面**通过的人数；两者是相互独立的动作，
     * 审批通过数会被读成"已经面过并过了"。故新增本字段，让前端按终面口径统计。
     */
    @Schema(description = "视频终面结果：1通过，2未通过，3待定；null未完成") Integer interviewResult,
    @Schema(description = "是否锁定") Integer isLocked,
    @Schema(description = "锁定人ID") Long lockedBy,
    @Schema(description = "锁定时间") LocalDateTime lockedTime,
    @Schema(description = "创建人ID") Long createdBy,
    @Schema(description = "创建时间") LocalDateTime createdTime,
    @Schema(description = "更新人ID") Long updatedBy,
    @Schema(description = "更新时间") LocalDateTime updatedTime,
    @Schema(description = "乐观锁版本号") Integer version
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
