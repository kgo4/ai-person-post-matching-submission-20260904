package com.example.matching.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 学习成果提交单视图（HR 匹配闭环 P4）。
 *
 * <p>对外只暴露业务字段：内部字段（{@code isDeleted} / {@code version} / {@code createdBy} 等）
 * 不进入响应体。{@code empName} 为瞬态补全字段，便于 HR 复核列表直接展示。</p>
 */
@Schema(description = "学习成果提交单")
public record LearningOutcomeSubmissionResponse(
        @Schema(description = "提交单ID") Long id,
        @Schema(description = "员工档案ID") Long empId,
        @Schema(description = "员工姓名（瞬态补全）") String empName,
        @Schema(description = "关联匹配记录ID") Long matchingRecordId,
        @Schema(description = "能力标签ID") Long tagId,
        @Schema(description = "能力名称") String abilityName,
        @Schema(description = "完成的学习资源ID") Long completedResourceId,
        @Schema(description = "学习前等级") Integer beforeLevel,
        @Schema(description = "自评达成等级") Integer confirmedLevel,
        @Schema(description = "提交说明") String note,
        @Schema(description = "复核状态：0待复核 1通过 2驳回") Integer reviewStatus,
        @Schema(description = "复核意见") String reviewComment,
        @Schema(description = "复核人用户ID") Long reviewedBy,
        @Schema(description = "复核时间") LocalDateTime reviewedTime,
        @Schema(description = "复核通过后回写产生的闭环业务键") String closureBusinessKey,
        @Schema(description = "提交时间") LocalDateTime createdTime) {
}
