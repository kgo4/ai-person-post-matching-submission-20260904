package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "反馈数据集响应")
public record FeedbackDatasetResponse(
    @Schema(description = "主键ID") Long id,
    @Schema(description = "关联的匹配记录ID") Long matchingRecordId,
    @Schema(description = "员工ID") Long empId,
    @Schema(description = "岗位ID") Long postId,
    @Schema(description = "原始AI匹配度") BigDecimal aiMatchScore,
    @Schema(description = "人工最终匹配度") BigDecimal finalMatchScore,
    @Schema(description = "人工最终匹配状态") Integer finalMatchStatus,
    @Schema(description = "采纳情况：1完全采纳，2部分采纳，3未采纳") Integer adoptionStatus,
    @Schema(description = "结构化反馈原因") String feedbackReasons,
    @Schema(description = "人工补充说明") String feedbackComment,
    @Schema(description = "是否允许导出：0否，1是") Integer exportEnabled,
    @Schema(description = "反馈时间") LocalDateTime feedbackTime
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
