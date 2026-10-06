package com.example.matching.dto.kg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "图谱变更集响应")
public record GraphChangeSetResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "变更编码") String changeCode,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "实体类型") String entityType,
        @Schema(description = "实体ID") Long entityId,
        @Schema(description = "操作类型") String operationType,
        @Schema(description = "负载JSON") String payloadJson,
        @Schema(description = "图谱版本") String graphVersion,
        @Schema(description = "处理状态") String processStatus,
        @Schema(description = "重试次数") Integer retryCount,
        @Schema(description = "影响节点数") Integer affectedNodeCount,
        @Schema(description = "影响边数") Integer affectedEdgeCount,
        @Schema(description = "开始时间") LocalDateTime startedTime,
        @Schema(description = "完成时间") LocalDateTime completedTime,
        @Schema(description = "错误信息") String errorMessage,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
