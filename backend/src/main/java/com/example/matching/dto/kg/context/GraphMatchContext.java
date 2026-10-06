package com.example.matching.dto.kg.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人岗匹配图谱上下文（紧凑 DTO）
 */
@Schema(description = "人岗匹配图谱上下文")
public record GraphMatchContext(
        @Schema(description = "上下文状态") GraphContextStatus status,
        @Schema(description = "员工ID") Long employeeId,
        @Schema(description = "员工姓名") String employeeName,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "图谱版本") String graphVersion,
        @Schema(description = "刷新时间") LocalDateTime refreshedAt,
        @Schema(description = "能力匹配列表") List<GraphMatchAbilityContext> abilities
) implements Serializable {

    public static GraphMatchContext empty(GraphContextStatus status, Long employeeId, Long postId) {
        return new GraphMatchContext(status, employeeId, null, postId, null, null, null, List.of());
    }
}
