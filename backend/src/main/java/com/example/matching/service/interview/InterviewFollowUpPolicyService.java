package com.example.matching.service.interview;

import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.interview.FollowUpType;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 追问策略服务
 * <p>
 * 规则兜底，防止大模型自由失控。
 * 决定是否追问、追问类型、目标维度、终止原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFollowUpPolicyService {

    /** 单个主问题最多一次补证据追问，避免把核验面试变成连续盘问。 */
    private static final int MAX_FOLLOW_UPS_PER_QUESTION = 1;

    /** 单个能力标签最大追问轮数（跨题目累计） */
    private static final int MAX_FOLLOW_UPS_PER_ABILITY_TAG = 3;

    /** 回答质量 >= 此分数停止追问 */
    private static final int QUALITY_STOP_THRESHOLD = 75;

    /**
     * 追问决策
     *
     * @param sessionId          会话ID
     * @param parentQuestionId   父问题ID
     * @param evaluation         回答质量评估
     * @param existingFollowUps  当前题已有的追问列表
     * @return 追问决策
     */
    public FollowUpDecision decide(Long sessionId, Long parentQuestionId,
                                   AnswerQualityEvaluation evaluation,
                                   List<InterviewFollowUpQuestion> existingFollowUps) {
        int currentCount = existingFollowUps != null ? existingFollowUps.size() : 0;

        int effectiveMaxFollowUps = MAX_FOLLOW_UPS_PER_QUESTION;

        // 任何上游异常都不能把明确否认、无回答或无关回答变成继续盘问。
        if (isTerminalNonEvidenceEvaluation(evaluation)) {
            String reason = evaluation != null && evaluation.conclusion() != null
                    ? evaluation.conclusion() : "未形成能力核验证据";
            log.info("追问终止：回答不构成能力证据，sessionId={}, reason={}", sessionId, reason);
            return FollowUpDecision.skip(reason, currentCount, effectiveMaxFollowUps);
        }

        // 终止条件 1：单题追问已达动态上限
        if (currentCount >= effectiveMaxFollowUps) {
            log.info("追问终止：单题追问已达动态上限，sessionId={}, parentQuestionId={}, count={}, max={}",
                    sessionId, parentQuestionId, currentCount, effectiveMaxFollowUps);
            return FollowUpDecision.skip("单题追问已达上限(" + effectiveMaxFollowUps + "轮)", currentCount, effectiveMaxFollowUps);
        }

        // 终止条件 2：已有充分证据时结束该能力核验，不能为了加题而继续追问。
        if (evaluation != null && evaluation.overallScore() >= QUALITY_STOP_THRESHOLD) {
            log.info("追问终止：回答质量达标，sessionId={}, score={}", sessionId, evaluation.overallScore());
            return FollowUpDecision.skip("回答质量达标(" + evaluation.overallScore() + "分)", currentCount, effectiveMaxFollowUps);
        }

        // 终止条件 3：没有可靠的评估结果或评估未明确要求补证据时，不生成追问。
        if (evaluation == null || !Boolean.TRUE.equals(evaluation.needFollowUp())) {
            log.info("追问终止：评估判定不需要追问，sessionId={}, reason={}",
                    sessionId, evaluation == null ? "缺少回答评估" : evaluation.followUpReason());
            return FollowUpDecision.skip(
                    evaluation != null && evaluation.followUpReason() != null
                            ? evaluation.followUpReason() : "没有明确的补证据需求",
                    currentCount, effectiveMaxFollowUps);
        }

        // 终止条件 4：追问内容与已问维度相似（去重）
        if (existingFollowUps != null && evaluation != null && evaluation.targetDimension() != null) {
            for (InterviewFollowUpQuestion existing : existingFollowUps) {
                if (evaluation.targetDimension().equals(existing.getTargetDimension())) {
                    log.info("追问终止：目标维度重复，sessionId={}, dimension={}",
                            sessionId, evaluation.targetDimension());
                    return FollowUpDecision.skip("追问维度重复", currentCount, effectiveMaxFollowUps);
                }
            }
        }

        // 需要追问
        String followUpType = evaluation != null && evaluation.suggestedFollowUpType() != null
                ? evaluation.suggestedFollowUpType()
                : FollowUpType.STAR_MISSING.name();
        String targetDimension = evaluation != null && evaluation.targetDimension() != null
                ? evaluation.targetDimension()
                : "detail";

        log.info("决定追问，sessionId={}, type={}, dimension={}, currentCount={}",
                sessionId, followUpType, targetDimension, currentCount);
        return FollowUpDecision.followUp(followUpType, targetDimension, currentCount, effectiveMaxFollowUps);
    }

    /**
     * 检查能力标签的追问次数是否超限（跨题目累计）
     */
    public boolean isAbilityTagFollowUpExhausted(Long sessionId, Long abilityTagId,
                                                   List<InterviewFollowUpQuestion> allFollowUpsForTag) {
        if (allFollowUpsForTag == null) return false;
        long count = allFollowUpsForTag.stream()
                .filter(f -> f.getTargetAbilityTagId() != null && f.getTargetAbilityTagId().equals(abilityTagId))
                .filter(f -> "ANSWERED".equals(f.getFollowUpStatus()) || "ASKED".equals(f.getFollowUpStatus()))
                .count();
        return count >= MAX_FOLLOW_UPS_PER_ABILITY_TAG;
    }

    private boolean isTerminalNonEvidenceEvaluation(AnswerQualityEvaluation evaluation) {
        if (evaluation == null) {
            return false;
        }
        String text = String.valueOf(evaluation.followUpReason()) + " " + String.valueOf(evaluation.conclusion());
        return text.contains("否认") || text.contains("无关") || text.contains("无回答") || text.contains("未涉及");
    }
}
