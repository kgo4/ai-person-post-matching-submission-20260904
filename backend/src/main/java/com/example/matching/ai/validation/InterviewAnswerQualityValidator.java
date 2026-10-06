package com.example.matching.ai.validation;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.entity.interview.FollowUpType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 面试回答质量评估校验器
 * <p>
 * 校验规则：
 * <ul>
 *   <li>各项评分必须在 0-100 范围内</li>
 *   <li>建议追问类型必须在允许枚举内</li>
 *   <li>STAR 字段必须完整（四个维度均为非 null 布尔值）</li>
 *   <li>sourceRefs 必须存在，且只能引用当前回答上下文（当前会话、当前题目）</li>
 * </ul>
 */
@Slf4j
@Component
public class InterviewAnswerQualityValidator {

    public static final String SCENARIO = "INTERVIEW_ANSWER_QUALITY";

    /**
     * 校验评估结果；不合法时抛出 {@link AiOutputValidationException}
     *
     * @param evaluation 模型解析后的评估结果
     * @param sessionId  当前会话ID（服务端上下文）
     * @param questionId 当前题目ID（服务端上下文）
     */
    public void validate(AnswerQualityEvaluation evaluation, Long sessionId, Long questionId) {
        if (evaluation == null) {
            throw new AiOutputValidationException(SCENARIO, "evaluation", "评估结果为空");
        }
        validateScore("specificityScore", evaluation.specificityScore());
        validateScore("evidenceScore", evaluation.evidenceScore());
        validateScore("personalContributionScore", evaluation.personalContributionScore());
        validateScore("logicConsistencyScore", evaluation.logicConsistencyScore());

        validateFollowUpType(evaluation.suggestedFollowUpType());

        validateStarCompleteness(evaluation.starCompleteness());

        validateSourceRefs(evaluation.sourceRefs(), sessionId, questionId);
    }

    private void validateScore(String field, Integer score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new AiOutputValidationException(SCENARIO, field, "评分超出范围 0-100: " + score);
        }
    }

    private void validateFollowUpType(String suggestedType) {
        if (suggestedType == null || suggestedType.isBlank()) {
            return;
        }
        for (FollowUpType type : FollowUpType.values()) {
            if (type.name().equals(suggestedType)) {
                return;
            }
        }
        throw new AiOutputValidationException(SCENARIO, "suggestedFollowUpType",
                "非法追问类型: " + suggestedType);
    }

    private void validateStarCompleteness(AnswerQualityEvaluation.StarCompleteness star) {
        if (star == null) {
            throw new AiOutputValidationException(SCENARIO, "starCompleteness", "STAR完整性缺失");
        }
        requireBoolean(star.situation(), "starCompleteness.situation");
        requireBoolean(star.task(), "starCompleteness.task");
        requireBoolean(star.action(), "starCompleteness.action");
        requireBoolean(star.result(), "starCompleteness.result");
    }

    private void requireBoolean(Boolean value, String field) {
        if (value == null) {
            throw new AiOutputValidationException(SCENARIO, field, "STAR 字段缺失");
        }
    }

    /**
     * sourceRefs 必须存在且只能引用当前回答上下文（当前会话、当前题目）
     */
    private void validateSourceRefs(List<String> sourceRefs, Long sessionId, Long questionId) {
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            throw new AiOutputValidationException(SCENARIO, "sourceRefs", "sourceRefs 缺失");
        }
        String sessionRef = sessionId != null
                ? SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, sessionId) : null;
        String questionRef = questionId != null
                ? SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, questionId) : null;

        for (String ref : sourceRefs) {
            if (ref == null || ref.isBlank()) {
                throw new AiOutputValidationException(SCENARIO, "sourceRefs", "包含空引用");
            }
            boolean allowed = (sessionRef != null && sessionRef.equals(ref))
                    || (questionRef != null && questionRef.equals(ref));
            if (!allowed) {
                throw new AiOutputValidationException(SCENARIO, "sourceRefs",
                        "引用超出当前回答上下文: " + ref);
            }
        }
    }
}
