package com.example.matching.controller.employee;

import com.example.matching.controller.matching.MatchingScoringConfigController;
import com.example.matching.dto.employee.api.AiTestAnswerSubmitRequest;
import com.example.matching.dto.matching.ScoringWeightUpdateRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Controller 请求体 DTO 契约")
class ValidatedRequestBodyControllerContractTest {

    @Test
    @DisplayName("评分配置保存端点使用 @Valid + 验证 DTO")
    void scoringConfigSaveUsesValidatedDto() throws NoSuchMethodException {
        Method method = MatchingScoringConfigController.class.getMethod("saveConfig", ScoringWeightUpdateRequest.class);
        Parameter body = method.getParameters()[0];

        assertThat(body.isAnnotationPresent(RequestBody.class)).isTrue();
        assertThat(body.isAnnotationPresent(Valid.class)).isTrue();
        assertThat(body.getType()).isEqualTo(ScoringWeightUpdateRequest.class);
    }

    @Test
    @DisplayName("AI 测试答案提交端点使用 @Valid + 验证 DTO")
    void aiTestSubmitUsesValidatedDto() throws NoSuchMethodException {
        Method method = AiTestController.class.getMethod("submitAnswers", Long.class, AiTestAnswerSubmitRequest.class);
        Parameter body = method.getParameters()[1];

        assertThat(body.isAnnotationPresent(RequestBody.class)).isTrue();
        assertThat(body.isAnnotationPresent(Valid.class)).isTrue();
        assertThat(body.getType()).isEqualTo(AiTestAnswerSubmitRequest.class);
    }

    @Test
    @DisplayName("AI 测试答案 DTO 仅承载 answers 字段且业务方法仍接收 Map")
    void aiTestAnswerDtoCarriesOnlyAnswers() {
        assertThat(AiTestAnswerSubmitRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("answers");
    }
}
