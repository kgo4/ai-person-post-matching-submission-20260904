package com.example.matching.dto.learning;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新学习路径步骤状态请求。
 */
public record LearningStepStatusUpdateRequest(
        @NotBlank(message = "status不能为空") String status
) {
}
