package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Agent记忆响应")
public record AgentMemoryResponse(
        @Schema(description = "记忆ID") Long id,
        @Schema(description = "记忆类型") String memoryType,
        @Schema(description = "记忆标题") String title,
        @Schema(description = "记忆内容") String content,
        @Schema(description = "触发表达JSON") String triggerExpressionsJson,
        @Schema(description = "结构化规则载荷JSON") String rulePayloadJson,
        @Schema(description = "规则强度：HARD/GUIDANCE") String ruleStrength,
        @Schema(description = "规则唯一键") String ruleKey,
        @Schema(description = "适用范围") String applicableScope,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "状态") String status,
        @Schema(description = "来源治理事件ID") Long sourceEventId,
        @Schema(description = "使用次数") Integer useCount,
        @Schema(description = "最后使用时间") LocalDateTime lastUsedTime,
        @Schema(description = "失效时间") LocalDateTime expireTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
