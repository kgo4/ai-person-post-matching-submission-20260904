package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "演化任务响应")
public record PostEvolutionTaskResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "任务编码") String taskCode,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "任务名称") String taskName,
        @Schema(description = "基线模型版本") String baselineVersion,
        @Schema(description = "新JD或市场数据文本") String newJdText,
        @Schema(description = "RAG查询日志ID") Long ragQueryLogId,
        @Schema(description = "任务状态") String taskStatus,
        @Schema(description = "任务摘要JSON") String summaryJson,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源文档ID") Long sourceDocumentId,
        @Schema(description = "业务领域") String businessDomain,
        @Schema(description = "行业") String industry,
        @Schema(description = "触发类型") String triggerType,
        @Schema(description = "上下文哈希") String contextHash,
        @Schema(description = "上下文快照ID") Long contextSnapshotId,
        @Schema(description = "证据摘要JSON") String evidenceSummary,
        @Schema(description = "Agent执行过程追踪JSON") String agentTrace,
        @Schema(description = "Harness校验摘要JSON") String harnessSummary,
        @Schema(description = "执行阶段") String progressStatus,
        @Schema(description = "执行进度百分比") Integer progressPercent,
        @Schema(description = "关联的知识源文档ID列表JSON") String sourceDocumentIds,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
