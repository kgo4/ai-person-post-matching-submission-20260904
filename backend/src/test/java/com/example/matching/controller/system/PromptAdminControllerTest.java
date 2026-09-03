package com.example.matching.controller.system;

import com.example.matching.application.system.PromptAdminApiFacade;
import com.example.matching.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptAdminControllerTest {

    @Test
    void listPromptsReturnsPromptFiles() {
        PromptAdminApiFacade facade = mock(PromptAdminApiFacade.class);
        PromptAdminController controller = new PromptAdminController(facade);

        Map<String, Object> result = Map.of("total", 2, "ftlCount", 1);
        when(facade.listPrompts()).thenReturn(result);

        R<Map<String, Object>> response = controller.listPrompts();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("total", 2);
    }

    @Test
    void reloadReturnsReloadResult() {
        PromptAdminApiFacade facade = mock(PromptAdminApiFacade.class);
        PromptAdminController controller = new PromptAdminController(facade);

        Map<String, Object> result = Map.of("success", true, "message", "缓存已清除");
        when(facade.reload()).thenReturn(result);

        R<Map<String, Object>> response = controller.reload();

        assertThat(response.getData()).containsEntry("success", true);
    }

    @Test
    void experimentResultsWithoutPromptName() {
        PromptAdminApiFacade facade = mock(PromptAdminApiFacade.class);
        PromptAdminController controller = new PromptAdminController(facade);

        Map<String, Object> result = Map.of("totalCalls", 0, "groups", Map.of());
        when(facade.getExperimentResults(7, null)).thenReturn(result);

        R<Map<String, Object>> response = controller.experimentResults(7, null);

        assertThat(response.getData()).containsEntry("totalCalls", 0);
    }

    @Test
    void experimentResultsWithPromptName() {
        PromptAdminApiFacade facade = mock(PromptAdminApiFacade.class);
        PromptAdminController controller = new PromptAdminController(facade);

        Map<String, Object> result = Map.of("totalCalls", 3);
        when(facade.getExperimentResults(30, "resume_parse")).thenReturn(result);

        R<Map<String, Object>> response = controller.experimentResults(30, "resume_parse");

        assertThat(response.getData()).containsEntry("totalCalls", 3);
    }
}
