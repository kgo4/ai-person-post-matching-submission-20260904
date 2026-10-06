package com.example.matching.dto.ability.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Agent memory fields editable by a governance user")
public record AgentMemoryUpdateRequest(
        @NotBlank @Schema(description = "Memory title") String title,
        @NotBlank @Schema(description = "Memory content") String content,
        @NotNull @Schema(description = "Priority") Integer priority,
        @NotBlank @Schema(description = "Applicable scope") String applicableScope
) {
}
