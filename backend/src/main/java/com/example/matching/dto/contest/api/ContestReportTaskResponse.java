package com.example.matching.dto.contest.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "报告任务响应")
public record ContestReportTaskResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "任务编码") String taskCode,
        @Schema(description = "报告类型") String reportType,
        @Schema(description = "任务状态") String taskStatus,
        @Schema(description = "报告标题") String reportTitle,
        @Schema(description = "生成的报告Markdown") String reportMarkdown,
        @Schema(description = "生成的报告JSON") String reportJson,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "生成模式") String generationMode,
        @Schema(description = "使用的模型名称") String modelName,
        @Schema(description = "Prompt模板版本") String promptVersion,
        @Schema(description = "证据快照JSON") String evidenceSnapshotJson,
        @Schema(description = "校验状态") String validationStatus,
        @Schema(description = "校验消息") String validationMessage,
        @Schema(description = "生成耗时") Long durationMs,
        @Schema(description = "报告字数") Integer wordCount,
        @Schema(description = "RAG场景") String ragScenario,
        @Schema(description = "RAG命中数量") Integer ragHitCount,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "完成时间") LocalDateTime finishedTime
) implements Serializable {
}
