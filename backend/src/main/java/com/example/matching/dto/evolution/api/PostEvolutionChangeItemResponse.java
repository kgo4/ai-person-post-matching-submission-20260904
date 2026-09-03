package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "岗位演化变更项响应")
public record PostEvolutionChangeItemResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "演化任务ID") Long taskId,
        @Schema(description = "变更类型") String changeType,
        @Schema(description = "能力标签ID") Long tagId,
        @Schema(description = "能力名称") String abilityName,
        @Schema(description = "旧要求等级") Integer oldLevel,
        @Schema(description = "新要求等级") Integer newLevel,
        @Schema(description = "旧权重") BigDecimal oldWeight,
        @Schema(description = "新权重") BigDecimal newWeight,
        @Schema(description = "旧核心标志") Integer oldIsCore,
        @Schema(description = "新核心标志") Integer newIsCore,
        @Schema(description = "证据支持分数") BigDecimal supportScore,
        @Schema(description = "支持的RAG分块ID") String evidenceChunkIds,
        @Schema(description = "数据来源类型") String sourceType,
        @Schema(description = "数据来源引用") String sourceRef,
        @Schema(description = "数据来源详情") String sourceDetail,
        @Schema(description = "变更类型扩展") String changeTypeExtended,
        @Schema(description = "证据文本") String evidenceText,
        @Schema(description = "多来源引用JSON") String sourceRefsJson,
        @Schema(description = "置信度评分") BigDecimal confidenceScore,
        @Schema(description = "时效性评分") BigDecimal freshnessScore,
        @Schema(description = "权威度评分") BigDecimal authorityScore,
        @Schema(description = "跨来源评分") BigDecimal crossSourceScore,
        @Schema(description = "Harness决策") String harnessDecision,
        @Schema(description = "风险等级") String riskLevel,
        @Schema(description = "确认状态") String confirmStatus,
        @Schema(description = "人工审核意见") String reviewComment,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "仅包含已落库且关联当前变更项的真实证据") List<PostEvolutionEvidenceResponse> evidenceItems,
        @Schema(description = "基于 evidenceItems 计算的真实证据汇总；没有关联证据时为 null") PostEvolutionEvidenceSummaryResponse evidenceSummary
) implements Serializable {
}
