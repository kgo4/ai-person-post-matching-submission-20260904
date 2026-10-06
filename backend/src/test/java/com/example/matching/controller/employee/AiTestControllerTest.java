package com.example.matching.controller.employee;

import com.example.matching.application.employee.AiTestApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.AiTestAnswerSubmitRequest;
import com.example.matching.dto.employee.api.AiTestResponse;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTestControllerTest {

    private AiTestApiFacade facade;
    private AiTestController controller;

    @BeforeEach
    void setUp() {
        SecurityUtils.clear();
        facade = mock(AiTestApiFacade.class);
        controller = new AiTestController(facade);
    }

    private static AiTestResponse testResponse(Long id, Long empId) {
        return new AiTestResponse(
                id, empId, "AI综合测试", 101L, "Java",
                "questions", "answers", "ai evaluation", "analysis report",
                null, new BigDecimal("85.5"), 3, 2,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 30),
                LocalDateTime.of(2025, 1, 1, 10, 31));
    }

    @Test
    void generatePostTestReturnsGeneratedTest() {
        AiTestResponse test = testResponse(1L, 100L);
        when(facade.generatePostTest(eq(100L), eq(200L), any())).thenReturn(test);

        R<AiTestResponse> response = controller.generatePostTest(100L, 200L);

        assertThat(response.getData()).isEqualTo(test);
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("综合测试题目已生成");
    }

    @Test
    void generateTestReturnsGeneratedTest() {
        AiTestResponse test = testResponse(2L, 100L);
        when(facade.generateTest(eq(100L), eq(101L), any())).thenReturn(test);

        R<AiTestResponse> response = controller.generateTest(100L, 101L);

        assertThat(response.getData()).isEqualTo(test);
        assertThat(response.getMessage()).isEqualTo("测试题目已生成");
    }

    @Test
    void submitAnswersPassesAnswersAndReturnsResult() {
        AiTestAnswerSubmitRequest request = new AiTestAnswerSubmitRequest(Map.of("q1", "答案A"));
        AiTestResponse result = testResponse(3L, 100L);
        when(facade.submitAnswers(eq(3L), eq(request.answers()))).thenReturn(result);

        R<AiTestResponse> response = controller.submitAnswers(3L, request);

        assertThat(response.getData()).isEqualTo(result);
        assertThat(response.getMessage()).isEqualTo("答案已提交，AI批阅完成");
    }

    @Test
    void getTestResultReturnsTestData() {
        AiTestResponse test = testResponse(4L, 100L);
        when(facade.getTestResult(4L)).thenReturn(test);

        R<AiTestResponse> response = controller.getTestResult(4L);

        assertThat(response.getData()).isEqualTo(test);
        assertThat(response.getData().testTitle()).isEqualTo("AI综合测试");
    }

    @Test
    void listByEmpIdReturnsAllTests() {
        AiTestResponse first = testResponse(1L, 100L);
        AiTestResponse second = testResponse(2L, 100L);
        when(facade.listByEmpId(100L)).thenReturn(List.of(first, second));

        R<List<AiTestResponse>> response = controller.listByEmpId(100L);

        assertThat(response.getData()).containsExactly(first, second);
    }

    @Test
    void importToAbilityProfileReturnsTrue() {
        when(facade.importToAbilityProfile(5L)).thenReturn(true);

        R<Boolean> response = controller.importToAbilityProfile(5L);

        assertThat(response.getData()).isTrue();
        assertThat(response.getMessage()).isEqualTo("测试结果已导入能力档案");
    }

    @Test
    void redeliverTaskReturnsTrue() {
        when(facade.redeliverTask(6L)).thenReturn(true);

        R<Boolean> response = controller.redeliverTask(6L);

        assertThat(response.getData()).isTrue();
        assertThat(response.getMessage()).isEqualTo("任务已重新投递");
    }
}
