package com.example.matching.dto.governance.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

@Schema(description = "Harness批量审核请求")
public record BatchHarnessReviewRequest(
        @Schema(description = "待审核记录ID，最多100条") List<Long> ids,
        @Schema(description = "审核动作：ACCEPTED / REJECTED") String reviewStatus,
        @Schema(description = "审核备注；批量驳回时必填") String reviewComment,
        @Schema(description = "驳回原因分类；批量驳回时必填") String rejectReasonCategory
) implements Serializable {
}
