package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.AiTestResponse;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.service.employee.AiTestService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTestApiFacadeTest {

    @Test
    void generatedTestResponseIncludesQuestionPayloadForImmediateDisplay() throws Exception {
        AiTestService service = mock(AiTestService.class);
        EmpAiTest generated = new EmpAiTest();
        generated.setId(17L);
        generated.setQuestions("[{\"question\":\"What is dependency injection?\"}]");
        when(service.generateTest(1L, 2L, 3L)).thenReturn(generated);

        AiTestResponse response = new AiTestApiFacade(service).generateTest(1L, 2L, 3L);

        Set<String> fields = Arrays.stream(AiTestResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(fields).contains("questions");
        assertThat(AiTestResponse.class.getMethod("questions").invoke(response))
                .isEqualTo(generated.getQuestions());
    }
}
