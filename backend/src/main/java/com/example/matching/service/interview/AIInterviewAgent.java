package com.example.matching.service.interview;

import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.entity.interview.InterviewAbilityObservation;

import java.util.List;

/**
 * AI面试Agent接口
 * <p>
 * 独立的AI面试Agent，负责：
 * 1. 基于简历 + 岗位模型进行面试
 * 2. 通过追问识别能力边界
 * 3. 生成本场面试的能力观察结果
 * 4. 生成胜任力评估报告
 * <p>
 * 注意：AI面试Agent不直接决定最终能力画像，只输出面试能力观察。
 * 最终人员画像由PersonAbilityProfileAgent融合多来源后决定。
 *
 * @author system
 */
public interface AIInterviewAgent {

    /**
     * 检查候选人是否可以进入AI面试
     * <p>
     * 前置条件：
     * 1. 候选人必须有简历
     * 2. 简历必须已解析
     *
     * @param empId 候选人员工ID
     * @return 检查结果
     */
    InterviewEligibilityCheck checkInterviewEligibility(Long empId);

    /**
     * 生成面试计划
     * <p>
     * 输入：
     * 1. empId - 候选人ID
     * 2. resumeParseId - 简历解析记录ID
     * 3. postId - 目标岗位ID
     * 4. resumeText - 简历原文
     * 5. resumeAbilityClaims - 简历中声称的能力
     * 6. postAbilityModel - 岗位能力模型
     *
     * @param request 面试计划请求
     * @return 面试计划
     */
    InterviewPlan generateInterviewPlan(InterviewPlanRequest request);

    /**
     * 执行面试并生成能力观察
     * <p>
     * 输出：
     * 1. InterviewAbilityObservation - 面试能力观察
     * 2. 不直接写EmpAbility
     *
     * @param sessionId 面试会话ID
     * @return 面试能力观察列表
     */
    List<InterviewAbilityObservation> conductInterviewAndObserve(Long sessionId);

    /**
     * 生成胜任力评估报告
     *
     * @param sessionId 面试会话ID
     * @return 胜任力评估报告
     */
    CompetencyReport generateCompetencyReport(Long sessionId);

    /**
     * 面试资格检查结果
     */
    record InterviewEligibilityCheck(
        /** 是否可以进入面试 */
        boolean eligible,
        /** 不可进入的原因（如果eligible=false） */
        String reason,
        /** 简历解析记录ID（如果eligible=true） */
        Long resumeParseId,
        /** 简历原文（如果eligible=true） */
        String resumeText,
        /** 简历结构化数据（如果eligible=true） */
        String resumeStructuredData,
        /** 简历中声称的能力（如果eligible=true） */
        String resumeAbilityClaims
    ) {}

    /**
     * 面试计划请求
     */
    record InterviewPlanRequest(
        Long sessionId,
        /** 候选人员工ID */
        Long empId,
        /** 简历解析记录ID */
        Long resumeParseId,
        /** 目标岗位ID */
        Long postId,
        /** 简历原文 */
        String resumeText,
        /** 简历中声称的能力 */
        String resumeAbilityClaims,
        /** 岗位能力模型JSON */
        String postAbilityModel,
        /** 历史面试记录（可选） */
        String interviewHistory,
        Integer questionCount
    ) {}

    /**
     * 面试计划
     */
    record InterviewPlan(
        /** 面试会话ID */
        Long sessionId,
        /** 面试问题列表 */
        List<InterviewQuestion> questions,
        /** 面试策略说明 */
        String strategy,
        /** 预计时长（分钟） */
        int estimatedDuration
    ) {}

    /**
     * 面试问题
     */
    record InterviewQuestion(
        /** 问题序号 */
        int order,
        /** 问题文本 */
        String text,
        /** 问题类型：TECHNICAL/BEHAVIORAL/GENERAL */
        String type,
        /** 难度：EASY/MEDIUM/HARD */
        String difficulty,
        /** 关联的能力标签ID列表 */
        List<Long> expectedTagIds,
        /** 追问策略 */
        String followUpStrategy
    ) {}
}
