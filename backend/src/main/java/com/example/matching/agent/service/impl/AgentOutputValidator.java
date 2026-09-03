package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentRunResult;
import com.example.matching.agent.dto.interview.AiTestQuestionItem;
import com.example.matching.agent.dto.interview.AiTestEvaluationResultDTO;
import com.example.matching.agent.dto.interview.InterviewAnswerQualityDTO;
import com.example.matching.agent.dto.interview.InterviewFollowUpQuestionDTO;
import com.example.matching.agent.dto.interview.InterviewObservationDTO;
import com.example.matching.agent.dto.interview.InterviewPlanDTO;
import com.example.matching.agent.dto.interview.InterviewReportDTO;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.ai.validation.AiTestQuestionSetValidator;
import com.example.matching.ai.validation.InterviewAnswerQualityValidator;
import com.example.matching.ai.validation.InterviewFollowUpValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOutputValidator {

    private final Validator beanValidator;
    private final AiTestQuestionSetValidator questionSetValidator;
    private final InterviewAnswerQualityValidator answerQualityValidator;
    private final InterviewFollowUpValidator followUpValidator;

    public <T> ValidationResult validate(T output, String scene) {
        if (output == null) {
            log.warn("[AGENT_VALIDATION] Null output for scene={}", scene);
            return ValidationResult.fail(scene, "output", "output is null");
        }

        Set<ConstraintViolation<T>> violations = beanValidator.validate(output);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            String field = first.getPropertyPath().toString();
            String reason = first.getMessage();
            log.warn("[AGENT_VALIDATION] Bean validation failed scene={}, field={}, reason={}", scene, field, reason);
            return ValidationResult.fail(scene, field, reason);
        }

        if (output instanceof AgentRunResult runResult) {
            if (runResult.getOverallConfidence() != null) {
                if (runResult.getOverallConfidence().compareTo(BigDecimal.ZERO) < 0
                        || runResult.getOverallConfidence().compareTo(new BigDecimal("100")) > 0) {
                    log.warn("[AGENT_VALIDATION] overallConfidence out of range [0,100]: {}",
                            runResult.getOverallConfidence());
                    return ValidationResult.fail(scene, "overallConfidence",
                            "overallConfidence must be between 0 and 100");
                }
            }
        }

        try {
            if ("AI_TEST_QUESTION".equals(scene) && output instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<AiTestQuestionItem> questions = (List<AiTestQuestionItem>) list;
                validateQuestionSet(questions);
            }
            validateSceneContract(output, scene);
        } catch (AiOutputValidationException e) {
            log.warn("[AGENT_VALIDATION] Scene validation failed scene={}, field={}, reason={}",
                    e.getScenario(), e.getField(), e.getReason());
            return ValidationResult.fail(scene, e.getField(), e.getReason());
        }

        return ValidationResult.pass();
    }

    public <T> void validateOrThrow(T output, String scene) {
        ValidationResult result = validate(output, scene);
        if (!result.passed()) {
            throw new AiOutputValidationException(scene, result.field(), result.reason());
        }
    }

    private void validateQuestionSet(List<AiTestQuestionItem> list) {
        if (list == null || list.isEmpty()) {
            throw new AiOutputValidationException("AI_TEST_QUESTION", "questions", "questions is empty");
        }
        int count = list.size();
        if (count < AiTestQuestionSetValidator.MIN_QUESTION_COUNT
                || count > AiTestQuestionSetValidator.MAX_QUESTION_COUNT) {
            throw new AiOutputValidationException("AI_TEST_QUESTION", "questions",
                    "question count " + count + " out of range ["
                            + AiTestQuestionSetValidator.MIN_QUESTION_COUNT + ","
                            + AiTestQuestionSetValidator.MAX_QUESTION_COUNT + "]");
        }
    }

    private void validateSceneContract(Object output, String scene) {
        switch (scene) {
            case "AI_TEST_EVALUATION" -> {
                AiTestEvaluationResultDTO dto = (AiTestEvaluationResultDTO) output;
                if (!"VALID".equals(dto.getStatus())) {
                    requireText(dto.getAnalysisReport(), "analysisReport", scene);
                    return;
                }
                requireRange(dto.getScore(), 0, 100, "score", scene);
                requireRange(dto.getMasteryLevel(), 1, 5, "masteryLevel", scene);
                requireText(dto.getAnalysisReport(), "analysisReport", scene);
            }
            case "INTERVIEW_PLAN" -> {
                InterviewPlanDTO dto = (InterviewPlanDTO) output;
                if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
                    throw new AiOutputValidationException(scene, "questions", "questions is empty");
                }
                for (InterviewPlanDTO.Question question : dto.getQuestions()) {
                    requireText(question.getText(), "questions.text", scene);
                    requireText(question.getType(), "questions.type", scene);
                    requireText(question.getDifficulty(), "questions.difficulty", scene);
                    requireText(question.getProjectAnchor(), "questions.projectAnchor", scene);
                    if (question.getExpectedTagIds() == null || question.getExpectedTagIds().isEmpty()) {
                        throw new AiOutputValidationException(scene, "questions.expectedTagIds",
                                "every interview question must bind an existing resume ability tag");
                    }
                }
            }
            case "INTERVIEW_OBSERVATION" -> {
                InterviewObservationDTO dto = (InterviewObservationDTO) output;
                if (dto.getObservations() == null || dto.getObservations().isEmpty()) {
                    throw new AiOutputValidationException(scene, "observations", "observations is empty");
                }
                for (InterviewObservationDTO.Observation observation : dto.getObservations()) {
                    if (observation.getTagId() == null) {
                        throw new AiOutputValidationException(scene, "observations.tagId",
                                "observation must bind an existing resume ability tag");
                    }
                    requireText(observation.getAbilityName(), "observations.abilityName", scene);
                    requireRange(observation.getObservedLevel(), 1, 5, "observations.observedLevel", scene);
                    requireRange(observation.getConfidenceScore(), 0, 100, "observations.confidenceScore", scene);
                    requireText(observation.getEvidenceText(), "observations.evidenceText", scene);
                    if (observation.getSourceRefs() == null || observation.getSourceRefs().isEmpty()) {
                        throw new AiOutputValidationException(scene, "observations.sourceRefs",
                                "observation must cite current-session evidence");
                    }
                }
            }
            case "INTERVIEW_REPORT" -> {
                InterviewReportDTO dto = (InterviewReportDTO) output;
                requireText(dto.getConclusion(), "conclusion", scene);
                requireText(dto.getRecommendation(), "recommendation", scene);
            }
            case "INTERVIEW_FOLLOW_UP" -> {
                InterviewFollowUpQuestionDTO dto = (InterviewFollowUpQuestionDTO) output;
                requireText(dto.getQuestionText(), "questionText", scene);
                requireText(dto.getTargetDimension(), "targetDimension", scene);
            }
            default -> {
                // Existing scenario-specific validators run after DTO-to-domain conversion.
            }
        }
    }

    private void requireText(String value, String field, String scene) {
        if (value == null || value.isBlank()) {
            throw new AiOutputValidationException(scene, field, "value is blank");
        }
    }

    private void requireRange(Integer value, int min, int max, String field, String scene) {
        if (value == null || value < min || value > max) {
            throw new AiOutputValidationException(scene, field,
                    "value must be between " + min + " and " + max);
        }
    }

    public ValidationResult validateAnswerQuality(InterviewAnswerQualityDTO dto) {
        return validate(dto, "INTERVIEW_ANSWER_QUALITY");
    }

    public ValidationResult validateFollowUp(InterviewFollowUpQuestionDTO dto) {
        return validate(dto, "INTERVIEW_FOLLOW_UP");
    }

    public record ValidationResult(boolean passed, String scene, String field, String reason) {
        public static ValidationResult pass() {
            return new ValidationResult(true, null, null, null);
        }

        public static ValidationResult fail(String scene, String field, String reason) {
            return new ValidationResult(false, scene, field, reason);
        }
    }
}
