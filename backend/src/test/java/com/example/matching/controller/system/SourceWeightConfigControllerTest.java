package com.example.matching.controller.system;

import com.example.matching.application.system.SourceWeightConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.SourceWeightConfigRequest;
import com.example.matching.dto.system.api.SourceWeightConfigResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceWeightConfigControllerTest {

    private static SourceWeightConfigResponse createResponse(Long id, String sourceType) {
        return new SourceWeightConfigResponse(
                id, sourceType, "来源" + sourceType, new BigDecimal("0.30"),
                1, 1, "备注", null, null);
    }

    @Test
    void listReturnsAllConfigs() {
        SourceWeightConfigApiFacade facade = mock(SourceWeightConfigApiFacade.class);
        SourceWeightConfigController controller = new SourceWeightConfigController(facade);

        SourceWeightConfigResponse response1 = createResponse(1L, "MATCH_RESULT");
        SourceWeightConfigResponse response2 = createResponse(2L, "RESUME");
        when(facade.listAll()).thenReturn(List.of(response1, response2));

        R<List<SourceWeightConfigResponse>> response = controller.list();

        assertThat(response.getData()).containsExactly(response1, response2);
    }

    @Test
    void batchUpdateReturnsUpdatedConfigs() {
        SourceWeightConfigApiFacade facade = mock(SourceWeightConfigApiFacade.class);
        SourceWeightConfigController controller = new SourceWeightConfigController(facade);

        List<SourceWeightConfigRequest> requests = List.of(
                new SourceWeightConfigRequest(1L, "MATCH_RESULT", "匹配结果", new BigDecimal("0.40"), 1, 1, "备注")
        );
        SourceWeightConfigResponse updated = createResponse(1L, "MATCH_RESULT");
        when(facade.batchUpdate(requests)).thenReturn(List.of(updated));

        R<List<SourceWeightConfigResponse>> response = controller.batchUpdate(requests);

        assertThat(response.getData()).containsExactly(updated);
    }
}
