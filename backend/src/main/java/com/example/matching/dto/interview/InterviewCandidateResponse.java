package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 「可发起沟通」候选池条目（HR 匹配闭环 P5）。
 *
 * <p>来源：**全部未删除的匹配记录**（不限匹配状态、不限是否已推送）。
 * 原实现只取「已推送 + 强适配/适配」，导致员工尚未匹配通过时 HR 根本找不到人去沟通 ——
 * 现口径为「HR 可直接选择匹配结果发起邀约，匹配通过只是向 HR 推荐」。</p>
 *
 * <p>{@code recommended} 表示匹配结论为强适配/适配（即「匹配通过」），
 * 仅供排序与提示，**不构成能否发起的前提**。</p>
 */
@Schema(description = "可发起视频终面的候选")
public record InterviewCandidateResponse(
        @Schema(description = "员工档案ID") Long empId,
        @Schema(description = "员工姓名") String empName,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "关联匹配记录ID") Long matchingRecordId,
        @Schema(description = "匹配状态：1强适配 2适配 3待观察 4不匹配（null=尚未评分）") Integer matchStatus,
        @Schema(description = "匹配状态名称") String matchStatusName,
        @Schema(description = "是否推荐（匹配通过：强适配/适配）；只影响排序与提示，不影响能否发起") Boolean recommended,
        @Schema(description = "匹配分数") BigDecimal matchScore,
        @Schema(description = "是否已有待沟通的终面（重复发起提示用）") Boolean hasPendingInterview,
        @Schema(description = "最近一次终面结论：1通过 2不通过 3待定") Integer latestResult) {
}
