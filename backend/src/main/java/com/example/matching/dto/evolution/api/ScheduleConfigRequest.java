package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "定时配置请求")
public record ScheduleConfigRequest(
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "是否启用") Integer enabled,
        @Schema(description = "Cron表达式") String cronExpression,
        @Schema(description = "行业") String industry,
        @Schema(description = "业务领域") String businessDomain,
        @Schema(description = "是否包含行业白皮书") Integer includeWhitepaper,
        @Schema(description = "是否包含云知识库") Integer includeCloudKnowledge,
        @Schema(description = "是否包含市场演化线索") Integer includeMarketJd
) implements Serializable {
}
