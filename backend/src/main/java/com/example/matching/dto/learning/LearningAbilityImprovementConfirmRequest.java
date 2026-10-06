package com.example.matching.dto.learning;

import jakarta.validation.constraints.NotNull;

/** 基于已通过学习测评确认能力提升。 */
public record LearningAbilityImprovementConfirmRequest(@NotNull Long planId, @NotNull Long stepId) {
}
