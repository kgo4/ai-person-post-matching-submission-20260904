package com.example.matching.dto.employee.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 员工「已通过岗位」条目（人岗匹配闭环设计 P3）。
 *
 * <p>口径：HR 审核通过且已推送（{@code approvalStatus=APPROVED AND publishStatus=PUBLISHED}）
 * 的匹配记录中，匹配状态为「强适配 / 适配」的岗位。同一岗位存在多条历史记录时只呈现最新一条。</p>
 *
 * <p>纯聚合查询，零表结构变更。</p>
 */
@Schema(description = "员工已通过岗位")
public record PassedPostResponse(
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "匹配状态：1强适配，2适配") Integer matchStatus,
        @Schema(description = "匹配状态名称") String matchStatusName,
        @Schema(description = "匹配分数（finalMatchScore，缺失时回退 aiMatchScore）") BigDecimal matchScore,
        @Schema(description = "结果更新时间（即最近一次推送时间）") LocalDateTime updatedTime) {
}
