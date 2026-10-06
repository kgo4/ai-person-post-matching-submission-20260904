package com.example.matching.ai.validation;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewAnswerQualityValidatorTest {

    private final InterviewAnswerQualityValidator validator = new InterviewAnswerQualityValidator();

    private AnswerQualityEvaluation validEvaluation() {
        return new AnswerQualityEvaluation(
                new AnswerQualityEvaluation.StarCompleteness(true, true, true, false),
                70, 60, 65, 70,
                true, "缺少量化结果", "result", "STAR_MISSING",
                List.of("量化结果"), List.of(), "部分支持",
                List.of(
                        SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_SESSION, 17L),
                        SourceRefConstants.factRef(SourceRefConstants.ENTITY_INTERVIEW_QUESTION, 23L)
                ));
    }

    @Test
    void acceptsValidEvaluation() {
        assertThatCode(() -> validator.validate(validEvaluation(), 17L, 23L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsScoreOutOfRange() {
        AnswerQualityEvaluation invalid = new AnswerQualityEvaluation(
                validEvaluation().starCompleteness(),
                150, 60, 65, 70,
                true, "r", "result", "STAR_MISSING",
                List.of(), List.of(), "c", validEvaluation().sourceRefs());
        assertThatThrownBy(() -> validator.validate(invalid, 17L, 23L))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("specificityScore");
    }

    @Test
    void rejectsIllegalFollowUpType() {
        AnswerQualityEvaluation invalid = new AnswerQualityEvaluation(
                validEvaluation().starCompleteness(),
                70, 60, 65, 70,
                true, "r", "result", "HALLUCINATED_TYPE",
                List.of(), List.of(), "c", validEvaluation().sourceRefs());
        assertThatThrownBy(() -> validator.validate(invalid, 17L, 23L))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("suggestedFollowUpType");
    }

    @Test
    void rejectsIncompleteStarFields() {
        AnswerQualityEvaluation invalid = new AnswerQualityEvaluation(
                new AnswerQualityEvaluation.StarCompleteness(null, true, true, true),
                70, 60, 65, 70,
                true, "r", "result", "STAR_MISSING",
                List.of(), List.of(), "c", validEvaluation().sourceRefs());
        assertThatThrownBy(() -> validator.validate(invalid, 17L, 23L))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("starCompleteness.situation");
    }

    @Test
    void rejectsMissingSourceRefs() {
        AnswerQualityEvaluation invalid = new AnswerQualityEvaluation(
                validEvaluation().starCompleteness(),
                70, 60, 65, 70,
                true, "r", "result", "STAR_MISSING",
                List.of(), List.of(), "c", List.of());
        assertThatThrownBy(() -> validator.validate(invalid, 17L, 23L))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("sourceRefs");
    }

    @Test
    void rejectsSourceRefsOutsideCurrentAnswerContext() {
        AnswerQualityEvaluation invalid = new AnswerQualityEvaluation(
                validEvaluation().starCompleteness(),
                70, 60, 65, 70,
                true, "r", "result", "STAR_MISSING",
                List.of(), List.of(), "c",
                List.of(SourceRefConstants.factRef(SourceRefConstants.ENTITY_EMP_ABILITY, 99L)));
        assertThatThrownBy(() -> validator.validate(invalid, 17L, 23L))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("超出当前回答上下文");
    }
}
