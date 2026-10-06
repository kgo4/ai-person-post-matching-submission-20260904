package com.example.matching.dto.governance.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "Harness审核请求")
public record HarnessCheckRequest(
        @Schema(description = "审核状态：ACCEPTED / REJECTED / RESOLVED") String reviewStatus,
        @Schema(description = "审核备注") String reviewComment,
        @Schema(description = "拒绝原因分类（仅REJECTED时使用）") String rejectReasonCategory,
        @Schema(description = "采纳后是否应用到业务数据（仅ACCEPTED时使用）") Boolean applyToBusiness,
        @Schema(description = "仅自动 BLOCK 的聚合人员能力可用；已确认后强制覆盖并投影") Boolean forceOverride
) implements Serializable {
    public HarnessCheckRequest(String reviewStatus, String reviewComment,
                               String rejectReasonCategory, Boolean applyToBusiness) {
        this(reviewStatus, reviewComment, rejectReasonCategory, applyToBusiness, false);
    }
}
