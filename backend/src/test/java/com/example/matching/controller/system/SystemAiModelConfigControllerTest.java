package com.example.matching.controller.system;

import com.example.matching.application.system.SystemAiModelConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.SystemAiModelConfigDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemAiModelConfigControllerTest {

    private static SystemAiModelConfigDTO createDto(Long id, String modelName) {
        return new SystemAiModelConfigDTO(
                id, true, "https://api.example.com", modelName, null,
                true, 30, new BigDecimal("0.7"), 5, 6, 1L, "2025-06-01 10:00:00");
    }

    @Test
    void getConfigReturnsConfig() {
        SystemAiModelConfigApiFacade facade = mock(SystemAiModelConfigApiFacade.class);
        SystemAiModelConfigController controller = new SystemAiModelConfigController(facade);

        SystemAiModelConfigDTO dto = createDto(1L, "qwen-max");
        when(facade.getConfig()).thenReturn(dto);

        R<SystemAiModelConfigDTO> response = controller.getConfig();

        assertThat(response.getData()).isEqualTo(dto);
    }

    @Test
    void saveConfigReturnsSavedConfig() {
        SystemAiModelConfigApiFacade facade = mock(SystemAiModelConfigApiFacade.class);
        SystemAiModelConfigController controller = new SystemAiModelConfigController(facade);

        SystemAiModelConfigDTO request = createDto(1L, "qwen-max");
        SystemAiModelConfigDTO saved = createDto(1L, "qwen-max");
        when(facade.saveConfig(request)).thenReturn(saved);

        R<SystemAiModelConfigDTO> response = controller.saveConfig(request);

        assertThat(response.getData().modelName()).isEqualTo("qwen-max");
    }

    @Test
    void healthCheckReturnsHealthResult() {
        SystemAiModelConfigApiFacade facade = mock(SystemAiModelConfigApiFacade.class);
        SystemAiModelConfigController controller = new SystemAiModelConfigController(facade);

        Map<String, Object> health = Map.of("ok", true, "latencyMs", 120L);
        when(facade.healthCheck()).thenReturn(health);

        R<Map<String, Object>> response = controller.healthCheck();

        assertThat(response.getData()).containsEntry("ok", true);
    }
}
