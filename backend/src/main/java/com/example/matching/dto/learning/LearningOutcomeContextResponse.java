package com.example.matching.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条「学习成果提交单」对应的学习路径与学习情况（HR 复核用）。
 *
 * <p><b>可见性闸门（本 DTO 存在的唯一理由）</b>：HR 默认看不到员工的学习路径，
 * <b>提交单是唯一入口</b> —— 员工提交能力提升申请后，HR 才能看到该员工在这条匹配下的
 * 学习路径进展与该项能力的学习记录。因此本响应<b>只能由提交单 ID 取</b>，
 * 不允许提供「按员工/按匹配记录直接查学习路径」的 HR 接口，否则等于绕过复核去看员工的过程数据。</p>
 *
 * <p>只回原始数据，不回「学习记录是否完整」这类结论：
 * 摘要口径在前端 `summarizeLearningRecord` 一处实现，员工侧（提交前自检）与 HR 侧（复核时）
 * 共用同一份，避免前后端各写一份导致同一个员工在两处看到不同的结论。</p>
 *
 * @author system
 */
@Schema(description = "提交单对应的学习路径与学习情况")
public record LearningOutcomeContextResponse(
        @Schema(description = "提交单ID") Long submissionId,
        @Schema(description = "员工档案ID") Long empId,
        @Schema(description = "员工姓名") String empName,
        @Schema(description = "关联的匹配记录ID") Long matchingRecordId,
        @Schema(description = "目标岗位名称（取自学习计划）") String postName,
        @Schema(description = "本次申请的能力名称") String abilityName,
        @Schema(description = "申请中记录的学习前等级") Integer beforeLevel,
        @Schema(description = "申请中的自评达成等级") Integer confirmedLevel,
        @Schema(description = "学习计划ID；该匹配还没有学习计划时为 null") Long planId,
        @Schema(description = "计划标题") String planTitle,
        @Schema(description = "计划状态：ACTIVE / COMPLETED") String planStatus,
        @Schema(description = "计划总步骤数") Integer totalStepCount,
        @Schema(description = "计划已完成步骤数") Integer completedStepCount,
        @Schema(description = "本次申请的能力在学习计划里对应的步骤ID；找不到对应步骤时为 null") Long appliedStepId,
        @Schema(description = "学习路径的全部步骤（含各自进度），用于判断「他整体学到哪了」")
        List<StepBrief> steps,
        @Schema(description = "本次申请对应步骤的评估题记录（即该能力的验证情况）")
        List<AssessmentBrief> assessments,
        @Schema(description = "项目材料：员工为该学习计划提交过的项目任务成果。"
                + "项目材料已不再单独复核，因此这里是 HR 判断能力提升的主要佐证；"
                + "属于本次申请步骤的排在前面")
        List<ProjectMaterialBrief> projectMaterials
) implements Serializable {

    /**
     * 学习步骤摘要。
     *
     * @param applied 是否为本次申请对应的那一步（前端据此高亮，避免 HR 自己找）
     */
    @Schema(description = "学习步骤摘要")
    public record StepBrief(
            @Schema(description = "步骤ID") Long stepId,
            @Schema(description = "能力名称") String abilityName,
            @Schema(description = "学习前等级") Integer currentLevel,
            @Schema(description = "目标等级") Integer targetLevel,
            @Schema(description = "差距类型：MISSING / LEVEL_GAP / EVIDENCE_WEAK") String gapType,
            @Schema(description = "优先级：HIGH / MEDIUM / LOW") String priority,
            @Schema(description = "步骤状态：PENDING / IN_PROGRESS / COMPLETED 等") String status,
            @Schema(description = "证据状态：VERIFIED / PENDING") String evidenceStatus,
            @Schema(description = "该能力匹配到的学习资源数") Integer resourceCount,
            @Schema(description = "是否为本次申请对应的步骤") Boolean applied
    ) implements Serializable {
    }

    /**
     * 评估题记录摘要。
     *
     * <p>刻意不回模型原始评分过程，只回「答了什么、判了什么、得几分、点评是什么」——
     * HR 复核需要的是判断依据，不是 AI 的完整推理链。</p>
     */
    @Schema(description = "评估题记录")
    public record AssessmentBrief(
            @Schema(description = "评估题ID") Long id,
            @Schema(description = "题型") String questionType,
            @Schema(description = "难度：EASY / MEDIUM / HARD") String difficultyLevel,
            @Schema(description = "判分状态：PENDING / PASSED / NOT_PASSED") String assessmentStatus,
            @Schema(description = "得分（0-100）") Integer score,
            @Schema(description = "员工的作答内容") String answerText,
            @Schema(description = "判分点评") String scoringFeedback,
            @Schema(description = "作答时间") LocalDateTime answeredTime
    ) implements Serializable {
    }

    /**
     * 项目材料摘要（员工自提，未经独立复核）。
     *
     * <p>这些材料只证明「员工做了练习并交了东西」，<b>不等于能力等级已被确认</b>；
     * 等级由 HR 在本次复核中判断。</p>
     *
     * @param belongsToAppliedStep 是否属于本次申请对应的步骤（前端据此分组/加粗）
     */
    @Schema(description = "项目材料")
    public record ProjectMaterialBrief(
            @Schema(description = "提交ID") Long submissionId,
            @Schema(description = "项目任务标题") String taskTitle,
            @Schema(description = "所属学习步骤ID") Long stepId,
            @Schema(description = "所属步骤的能力名称") String stepAbilityName,
            @Schema(description = "仓库地址") String repoUrl,
            @Schema(description = "演示地址") String demoUrl,
            @Schema(description = "报告地址") String reportUrl,
            @Schema(description = "文字说明") String submissionText,
            @Schema(description = "提交时间") LocalDateTime submittedTime,
            @Schema(description = "是否属于本次申请的能力对应步骤") Boolean belongsToAppliedStep
    ) implements Serializable {
    }
}
