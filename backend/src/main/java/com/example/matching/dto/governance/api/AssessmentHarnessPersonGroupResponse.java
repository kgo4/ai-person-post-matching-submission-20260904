package com.example.matching.dto.governance.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "按人员收纳的最终能力审核记录")
public record AssessmentHarnessPersonGroupResponse(
        @Schema(description = "员工ID；无法关联员工时为 null") Long empId,
        @Schema(description = "员工姓名") String empName,
        @Schema(description = "员工编号") String empCode,
        @Schema(description = "该人员的审核记录") List<AiHarnessCheckLogResponse> items,
        @Schema(description = "记录总数") int totalCount,
        @Schema(description = "待人工审核数") int pendingCount,
        @Schema(description = "可按AI建议直接通过的记录数") int safeAiAcceptCount
) {
}
