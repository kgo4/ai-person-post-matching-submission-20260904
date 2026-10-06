package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingOutboxApiFacade;
import com.example.matching.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingOutboxControllerTest {

    @Test
    void summaryReturnsFacadeStatusSummary() {
        MatchingOutboxApiFacade facade = mock(MatchingOutboxApiFacade.class);
        MatchingOutboxController controller = new MatchingOutboxController(facade);

        Map<String, Long> summary = Map.of("PENDING", 2L, "SUCCESS", 5L, "FAILED", 1L);
        when(facade.statusSummary()).thenReturn(summary);

        R<Map<String, Long>> response = controller.summary();

        assertThat(response.getData()).isEqualTo(summary);
    }

    @Test
    void replayReturnsTrueWhenFacadeSucceeds() {
        MatchingOutboxApiFacade facade = mock(MatchingOutboxApiFacade.class);
        MatchingOutboxController controller = new MatchingOutboxController(facade);

        when(facade.replay(10L)).thenReturn(true);

        R<Boolean> response = controller.replay(10L);

        assertThat(response.getData()).isTrue();
    }

    @Test
    void replayReturnsFalseWhenFacadeFails() {
        MatchingOutboxApiFacade facade = mock(MatchingOutboxApiFacade.class);
        MatchingOutboxController controller = new MatchingOutboxController(facade);

        when(facade.replay(10L)).thenReturn(false);

        R<Boolean> response = controller.replay(10L);

        assertThat(response.getData()).isFalse();
    }
}
