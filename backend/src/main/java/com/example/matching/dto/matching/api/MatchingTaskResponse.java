package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "匹配任务响应")
public record MatchingTaskResponse(
    @Schema(description = "任务主键ID") Long id,
    @Schema(description = "任务ID（UUID）") String taskId,
    @Schema(description = "关联岗位ID") Long postId,
    @Schema(description = "员工ID列表（JSON串）") String empIds,
    @Schema(description = "状态：0待执行，1执行中，2已完成，3失败，4已取消") Integer status,
    @Schema(description = "进度百分比") Integer progress,
    @Schema(description = "总记录数") Integer totalCount,
    @Schema(description = "已处理数") Integer processedCount,
    @Schema(description = "结果消息") String resultMessage,
    @Schema(description = "错误信息（失败原因）") String errorMessage,
    @Schema(description = "创建时间") LocalDateTime createdTime,
    @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
