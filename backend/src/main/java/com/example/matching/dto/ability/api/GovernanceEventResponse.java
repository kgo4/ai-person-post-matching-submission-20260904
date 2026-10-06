package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "治理事件响应")
public record GovernanceEventResponse(
        @Schema(description = "事件ID") Long id,
        @Schema(description = "员工ID") Long empId,
        @Schema(description = "原标签ID") Long oldTagId,
        @Schema(description = "原标签名称") String oldTagName,
        @Schema(description = "新标签ID") Long newTagId,
        @Schema(description = "新标签名称") String newTagName,
        @Schema(description = "原等级") Integer oldLevel,
        @Schema(description = "新等级") Integer newLevel,
        @Schema(description = "原置信度") BigDecimal oldConfidence,
        @Schema(description = "新置信度") BigDecimal newConfidence,
        @Schema(description = "来源分解JSON") String sourceBreakdownJson,
        @Schema(description = "证据快照JSON") String evidenceSnapshotJson,
        @Schema(description = "修改类型") String modifyType,
        @Schema(description = "修改原因") String modifyReason,
        @Schema(description = "模板payload JSON") String templatePayloadJson,
        @Schema(description = "是否生成Agent记忆") Integer generateMemory,
        @Schema(description = "关联的记忆ID") Long memoryId,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
