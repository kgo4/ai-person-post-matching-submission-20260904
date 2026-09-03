package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingScoringConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.ScoringWeightUpdateRequest;
import com.example.matching.dto.matching.ScoringWeightVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingScoringConfigControllerTest {

    @Test
    void getConfigReturnsFacadeConfig() {
        MatchingScoringConfigApiFacade facade = mock(MatchingScoringConfigApiFacade.class);
        MatchingScoringConfigController controller = new MatchingScoringConfigController(facade);

        ScoringWeightVO config = new ScoringWeightVO("v1", 0.65d, 0.15d, 0.10d, 0.10d, true);
        when(facade.getConfig()).thenReturn(config);

        R<ScoringWeightVO> response = controller.getConfig();

        assertThat(response.getData()).isEqualTo(config);
        assertThat(response.getData().version()).isEqualTo("v1");
    }

    @Test
    void saveConfigCallsFacadeAndReturnsOk() {
        MatchingScoringConfigApiFacade facade = mock(MatchingScoringConfigApiFacade.class);
        MatchingScoringConfigController controller = new MatchingScoringConfigController(facade);

        ScoringWeightUpdateRequest request = new ScoringWeightUpdateRequest(
                0.65d, 0.15d, 0.10d, 0.10d, true);

        R<Void> response = controller.saveConfig(request);

        verify(facade).saveConfig(request);
        assertThat(response.getCode()).isEqualTo(200);
    }
}
