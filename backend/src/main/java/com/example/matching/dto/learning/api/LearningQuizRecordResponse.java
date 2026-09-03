package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "答题记录响应")
public record LearningQuizRecordResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "员工ID") Long empId,
        @Schema(description = "题目ID") Long quizId,
        @Schema(description = "学习计划ID") Long planId,
        @Schema(description = "学习步骤ID") Long stepId,
        @Schema(description = "用户答案") String userAnswer,
        @Schema(description = "是否正确") Integer isCorrect,
        @Schema(description = "答题用时(秒)") Integer answerTime,
        @Schema(description = "得分") BigDecimal answerScore,
        @Schema(description = "尝试次数") Integer attemptCount,
        @Schema(description = "首次答题时间") LocalDateTime firstAttemptTime,
        @Schema(description = "最后答题时间") LocalDateTime lastAttemptTime,
        @Schema(description = "累计正确次数") Integer correctCount,
        @Schema(description = "是否已掌握") Integer isMastered,
        @Schema(description = "掌握时间") LocalDateTime masteredTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime,
        @Schema(description = "乐观锁版本号") Integer version
) implements Serializable {
}
