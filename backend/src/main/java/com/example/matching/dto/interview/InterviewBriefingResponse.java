package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 真实沟通要点文档（HR 匹配闭环 P5）。
 *
 * <p>HR 发起视频终面前，系统基于该员工的**全方位能力评估 + 匹配结果**实时聚合（不落库），
 * 供 HR 侧重点沟通。四区：能力概况 / 匹配概况 / 差距与学习 / 建议沟通要点。
 * 另附 {@code plainText} 供一键复制。</p>
 */
@Schema(description = "真实沟通要点文档")
public record InterviewBriefingResponse(
        @Schema(description = "员工档案ID") Long empId,
        @Schema(description = "员工姓名") String empName,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "一、能力概况") AbilityOverview ability,
        @Schema(description = "二、匹配概况") MatchOverview match,
        @Schema(description = "三、差距与学习") GapAndLearning gapAndLearning,
        @Schema(description = "四、建议沟通要点") List<String> talkingPoints,
        @Schema(description = "一键复制用纯文本") String plainText) {

    /** 一、能力概况：直接复用 P2 报告快照，报告缺失时降级为空统计 */
    @Schema(description = "能力概况")
    public record AbilityOverview(
            @Schema(description = "报告摘要（无报告时说明原因）") String summary,
            @Schema(description = "报告版本号") Integer reportVersion,
            @Schema(description = "harness 自动通过项数") int autoPassedCount,
            @Schema(description = "人工确认通过项数") int manualConfirmedCount,
            @Schema(description = "人工拒绝项数") int manualRejectedCount,
            @Schema(description = "被拒绝的能力项名称（沟通时重点澄清）") List<String> rejectedAbilities) {
    }

    /** 二、匹配概况 */
    @Schema(description = "匹配概况")
    public record MatchOverview(
            @Schema(description = "最终匹配分") BigDecimal finalMatchScore,
            @Schema(description = "AI 匹配分") BigDecimal aiMatchScore,
            @Schema(description = "匹配状态") Integer matchStatus,
            @Schema(description = "匹配状态名称") String matchStatusName) {
    }

    /** 三、差距与学习：差距项 + 学习成果复核进度 */
    @Schema(description = "差距与学习")
    public record GapAndLearning(
            @Schema(description = "主要差距项数量") int gapCount,
            @Schema(description = "差距能力项名称（最多 10 条）") List<String> gapAbilities,
            @Schema(description = "学习成果已通过复核数") int approvedOutcomeCount,
            @Schema(description = "学习成果待复核数") int pendingOutcomeCount,
            @Schema(description = "学习成果被驳回数") int rejectedOutcomeCount) {
    }
}
