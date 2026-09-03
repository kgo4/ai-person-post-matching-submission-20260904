package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "演化任务请求")
public record EvolutionTaskRequest(
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "任务名称") String taskName,
        @Schema(description = "新JD或市场数据文本") String newJdText
) implements Serializable {
}
