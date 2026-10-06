package com.example.matching.ai.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTestQuestionSetValidatorTest {

    private final AiTestQuestionSetValidator validator = new AiTestQuestionSetValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    private List<Map<String, Object>> questions(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("id", i + 1);
                    q.put("type", "text");
                    q.put("question", "题目" + (i + 1));
                    q.put("options", List.of());
                    q.put("answer", "");
                    q.put("referenceAnswer", "解析");
                    q.put("score", 10);
                    return q;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Test
    void acceptsValidQuestionSet() {
        assertThatCode(() -> validator.validate(questions(5))).doesNotThrowAnyException();
    }

    @Test
    void rejectsCountBelowMin() {
        assertThatThrownBy(() -> validator.validate(questions(2)))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("题数");
    }

    @Test
    void rejectsCountAboveMax() {
        assertThatThrownBy(() -> validator.validate(questions(11)))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("题数");
    }

    @Test
    void rejectsIllegalQuestionType() {
        List<Map<String, Object>> questions = questions(3);
        questions.get(0).put("type", "ESSAY");
        assertThatThrownBy(() -> validator.validate(questions))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsChoiceQuestionWithoutTwoOptions() {
        List<Map<String, Object>> questions = questions(3);
        questions.get(0).put("type", "choice_single");
        questions.get(0).put("options", List.of("A"));
        assertThatThrownBy(() -> validator.validate(questions))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("options");
    }

    @Test
    void acceptsChoiceQuestionWithTwoOptions() {
        List<Map<String, Object>> questions = questions(3);
        questions.get(0).put("type", "choice_single");
        questions.get(0).put("options", List.of("A", "B"));
        assertThatCode(() -> validator.validate(questions)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankQuestionText() {
        List<Map<String, Object>> questions = questions(3);
        questions.get(1).put("question", "");
        assertThatThrownBy(() -> validator.validate(questions))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("题干为空");
    }

    @Test
    void rejectsInvalidScore() {
        List<Map<String, Object>> questions = questions(3);
        questions.get(1).put("score", 0);
        assertThatThrownBy(() -> validator.validate(questions))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("score");
    }

    @Test
    void deterministicFallbackQuestionsAreValid() throws Exception {
        String json = com.example.matching.ai.validation.DeterministicAiFallbacks
                .get(com.example.matching.ai.validation.DeterministicAiFallbacks.AI_TEST_QUESTIONS).get();
        List<Map<String, Object>> questions = mapper.readValue(json, new TypeReference<>() {});
        assertThatCode(() -> validator.validate(questions)).doesNotThrowAnyException();
    }
}
