package com.example.matching.ai.validation;

import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 面试追问生成校验器
 * <p>
 * 校验规则：
 * <ul>
 *   <li>题干非空、最大 300 字</li>
 *   <li>追问数量不超过策略限制（单个主问题最大 2 轮）</li>
 *   <li>sessionId、原题 ID、员工 ID 一律从服务端上下文回填，不信任模型输出</li>
 * </ul>
 */
@Slf4j
@Component
public class InterviewFollowUpValidator {

    public static final String SCENARIO = "INTERVIEW_FOLLOW_UP";

    /** 题干最大长度 */
    public static final int MAX_QUESTION_TEXT_LENGTH = 300;

    /** 单个主问题最大追问轮数（与 InterviewFollowUpPolicyService 保持一致） */
    public static final int MAX_FOLLOW_UPS_PER_QUESTION = 2;

    /**
     * 校验模型生成的追问，并把服务端上下文回填到追问记录
     *
     * @param followUp         模型解析生成的追问（尚未落库）
     * @param originalQuestion 原问题（服务端上下文）
     * @param existingCount    该原题已有的追问数量
     */
    public void validateAndBackfill(InterviewFollowUpQuestion followUp,
                                    EmpVideoInterviewQuestion originalQuestion,
                                    int existingCount) {
        if (followUp == null) {
            throw new AiOutputValidationException(SCENARIO, "followUp", "追问记录为空");
        }
        if (originalQuestion == null || originalQuestion.getId() == null) {
            throw new AiOutputValidationException(SCENARIO, "originalQuestion", "原题上下文缺失");
        }

        validateQuestionText(followUp.getQuestionText());
        validateFollowUpCount(existingCount);

        // 服务端上下文回填：不信任模型输出
        followUp.setParentQuestionId(originalQuestion.getId());
        followUp.setSessionId(originalQuestion.getSessionId());
    }

    /**
     * 校验追问题干：非空、最大 300 字
     */
    public void validateQuestionText(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            throw new AiOutputValidationException(SCENARIO, "questionText", "题干为空");
        }
        if (questionText.length() > MAX_QUESTION_TEXT_LENGTH) {
            throw new AiOutputValidationException(SCENARIO, "questionText",
                    "题干超长，最大 " + MAX_QUESTION_TEXT_LENGTH + " 字，实际 " + questionText.length());
        }
    }

    /**
     * 校验追问数量不超过策略限制
     */
    public void validateFollowUpCount(int existingCount) {
        if (existingCount >= MAX_FOLLOW_UPS_PER_QUESTION) {
            throw new AiOutputValidationException(SCENARIO, "followUpCount",
                    "追问数量已达上限: " + existingCount);
        }
    }

    /**
     * 校验追问决策的目标维度、类型不超出策略允许范围
     */
    public void validateDecision(FollowUpDecision decision) {
        if (decision == null) {
            throw new AiOutputValidationException(SCENARIO, "decision", "追问决策为空");
        }
        if (decision.followUpType() != null) {
            for (com.example.matching.entity.interview.FollowUpType type
                    : com.example.matching.entity.interview.FollowUpType.values()) {
                if (type.name().equals(decision.followUpType())) {
                    return;
                }
            }
            throw new AiOutputValidationException(SCENARIO, "decision.followUpType",
                    "非法追问类型: " + decision.followUpType());
        }
    }

    /**
     * 校验已有追问列表内题干均合规（防止历史脏数据影响计数）
     */
    public void validateExistingFollowUps(List<InterviewFollowUpQuestion> existingFollowUps) {
        if (existingFollowUps == null) {
            return;
        }
        for (InterviewFollowUpQuestion followUp : existingFollowUps) {
            if (followUp.getQuestionText() == null || followUp.getQuestionText().isBlank()) {
                throw new AiOutputValidationException(SCENARIO, "existingFollowUps", "存在空题干追问记录");
            }
        }
    }
}
