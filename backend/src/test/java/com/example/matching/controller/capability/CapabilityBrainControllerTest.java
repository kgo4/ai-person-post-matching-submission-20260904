package com.example.matching.controller.capability;

import com.example.matching.application.capability.CapabilityBrainApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.capability.CapabilityBrainSummaryDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityBrainControllerTest {

    @Test
    void getSummaryReturnsCapabilityBrainSummary() {
        CapabilityBrainApiFacade facade = mock(CapabilityBrainApiFacade.class);
        CapabilityBrainController controller = new CapabilityBrainController(facade);

        CapabilityBrainSummaryDTO summary = new CapabilityBrainSummaryDTO();
        summary.setTitle("岗位能力大脑");
        summary.setMission("多源采集到学习路径闭环");
        summary.setLoopScore(85);
        when(facade.getSummary()).thenReturn(summary);

        R<CapabilityBrainSummaryDTO> response = controller.getSummary();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTitle()).isEqualTo("岗位能力大脑");
        assertThat(response.getData().getLoopScore()).isEqualTo(85);
    }
}
