package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "演化定时配置响应")
public record PostEvolutionScheduleConfigResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "是否启用") Integer enabled,
        @Schema(description = "Cron表达式") String cronExpression,
        @Schema(description = "行业") String industry,
        @Schema(description = "业务领域") String businessDomain,
        @Schema(description = "资料范围配置JSON") String sourceScope,
        @Schema(description = "是否包含行业白皮书") Integer includeWhitepaper,
        @Schema(description = "是否包含云知识库") Integer includeCloudKnowledge,
        @Schema(description = "是否包含市场演化线索") Integer includeMarketJd,
        @Schema(description = "最近执行时间") LocalDateTime lastRunTime,
        @Schema(description = "下次执行时间") LocalDateTime nextRunTime,
        @Schema(description = "最近生成的任务ID") Long lastTaskId,
        @Schema(description = "累计执行次数") Integer runCount,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
