package com.example.matching.dto.interview;

import com.example.matching.entity.interview.InterviewAbilityObservation;

import java.util.List;

/**
 * 胜任力评估报告（面试维度）
 * <p>
 * 从 {@code service.interview.AIInterviewAgent} 提取到共享 dto 包，
 * 供 assessment / interview / employee 等多域复用，避免 service 域之间的循环依赖。
 *
 * @author system
 */
public record CompetencyReport(
        /** 面试会话ID */
        Long sessionId,
        /** 候选人员工ID */
        Long empId,
        /** 目标岗位ID */
        Long postId,
        /** 综合评分（0-100） */
        int overallScore,
        /** 岗位匹配度评分（0-100） */
        int postMatchScore,
        /** 能力雷达图数据（用于前端展示） */
        List<AbilityRadarItem> radarItems,
        /** 面试能力观察列表 */
        List<InterviewAbilityObservation> observations,
        /** 优势能力列表 */
        List<String> strengths,
        /** 劣势能力列表 */
        List<String> weaknesses,
        /** 风险信号 */
        List<String> riskSignals,
        /** 提升建议 */
        List<String> improvementSuggestions,
        /** 学习路径建议 */
        List<LearningPathSuggestion> learningPathSuggestions,
        /** 面试结论 */
        String conclusion,
        /** 建议 */
        String recommendation,
        /** 降级标记：规则兜底无法派生任何观察时为 true */
        boolean degraded,
        /** 降级原因（degraded=true 时必填） */
        String degradedReason
) {}
