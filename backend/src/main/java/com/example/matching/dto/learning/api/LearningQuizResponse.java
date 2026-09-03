package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "测验题目响应")
public record LearningQuizResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "题目编码") String quizCode,
        @Schema(description = "题目文本") String questionText,
        @Schema(description = "题目类型") String questionType,
        @Schema(description = "选项JSON") String optionsJson,
        @Schema(description = "参考答案") String referenceAnswer,
        @Schema(description = "答案解析") String answerExplanation,
        @Schema(description = "难度级别") String difficultyLevel,
        @Schema(description = "所属知识领域ID") Long domainId,
        @Schema(description = "所属知识点ID") Long nodeId,
        @Schema(description = "关联能力标签ID") Long tagId,
        @Schema(description = "预计答题时间(秒)") Integer estimatedTime,
        @Schema(description = "分值") BigDecimal score,
        @Schema(description = "使用次数") Integer usageCount,
        @Schema(description = "正确率") BigDecimal correctRate,
        @Schema(description = "状态") String status,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime,
        @Schema(description = "乐观锁版本号") Integer version
) implements Serializable {
}
