package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.AiTestAnswerSubmitRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiTestAnswerSubmitRequest 验证")
class AiTestAnswerSubmitRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("空答案 Map -> 验证失败")
    void emptyAnswerMapIsRejected() {
        Set<ConstraintViolation<AiTestAnswerSubmitRequest>> violations =
                validator.validate(new AiTestAnswerSubmitRequest(Map.of()));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("答案不能为空"));
    }

    @Test
    @DisplayName("blank 题目ID -> 验证失败")
    void blankKeyIsRejected() {
        Set<ConstraintViolation<AiTestAnswerSubmitRequest>> violations =
                validator.validate(new AiTestAnswerSubmitRequest(Map.of(" ", "answer")));

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("题目ID不能为空"));
    }

    @Test
    @DisplayName("合法答案(字符串) -> 验证通过且 answers 可解包")
    void validStringPayloadPasses() {
        AiTestAnswerSubmitRequest request =
                new AiTestAnswerSubmitRequest(Map.of("q1", "回答一", "q2", "回答二"));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.answers()).containsEntry("q1", "回答一");
    }

    @Test
    @DisplayName("合法答案(数组) -> 多选题验证通过")
    void validArrayPayloadPasses() {
        AiTestAnswerSubmitRequest request =
                new AiTestAnswerSubmitRequest(Map.of("q1", java.util.List.of("选项A", "选项B")));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.answers()).containsEntry("q1", java.util.List.of("选项A", "选项B"));
    }
}
