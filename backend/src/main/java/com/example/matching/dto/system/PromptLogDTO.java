package com.example.matching.dto.system;

/** Prompt 埋点日志 DTO — Controller 不应直接引用 Entity */
public record PromptLogDTO(
        String promptName,
        String promptVersion,
        Boolean success,
        Long latencyMs,
        Integer feedbackScore
) {}
