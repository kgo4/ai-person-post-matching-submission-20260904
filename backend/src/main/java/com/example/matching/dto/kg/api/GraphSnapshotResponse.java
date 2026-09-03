package com.example.matching.dto.kg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "图谱快照响应")
public record GraphSnapshotResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "快照编码") String snapshotCode,
        @Schema(description = "快照名称") String snapshotName,
        @Schema(description = "快照类型") String snapshotType,
        @Schema(description = "节点数量") Integer nodeCount,
        @Schema(description = "边数量") Integer edgeCount,
        @Schema(description = "图JSON快照") String snapshotJson,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
