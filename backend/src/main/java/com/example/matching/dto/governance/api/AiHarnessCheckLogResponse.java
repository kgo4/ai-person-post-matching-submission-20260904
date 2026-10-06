package com.example.matching.dto.governance.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "AI Harness检查日志响应")
public record AiHarnessCheckLogResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "检查编码") String checkCode,
        @Schema(description = "场景") String scenario,
        @Schema(description = "声明类型") String claimType,
        @Schema(description = "声明文本") String claimText,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源引用ID") Long sourceRefId,
        @Schema(description = "证据文本") String evidenceText,
        @Schema(description = "RAG分块ID") String ragChunkIds,
        @Schema(description = "来源引用") String sourceRefs,
        @Schema(description = "匹配标签ID") Long matchedTagId,
        @Schema(description = "相似标签ID") Long similarTagId,
        @Schema(description = "支持分数") BigDecimal supportScore,
        @Schema(description = "风险等级") String riskLevel,
        @Schema(description = "决策") String decision,
        @Schema(description = "是否自证据") Integer isSelfEvidence,
        @Schema(description = "结构化原因JSON") String reasonJson,
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "审核意见") String reviewComment,
        @Schema(description = "审核时间") LocalDateTime reviewedTime,
        @Schema(description = "业务应用状态") String businessApplyStatus,
        @Schema(description = "业务目标类型") String businessTargetType,
        @Schema(description = "业务目标ID") Long businessTargetId,
        @Schema(description = "上下文哈希") String contextHash,
        @Schema(description = "上下文快照ID") Long contextSnapshotId,
        @Schema(description = "声明载荷JSON") String claimPayloadJson,
        @Schema(description = "接受的来源引用JSON") String acceptedSourceRefs,
        @Schema(description = "无效的来源引用JSON") String invalidSourceRefs,
        @Schema(description = "缺失的证据JSON") String missingEvidenceJson,
        @Schema(description = "归属员工ID(非人员场景为 null)") Long empId,
        @Schema(description = "归属员工姓名") String empName,
        @Schema(description = "归属员工编号") String empCode,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
