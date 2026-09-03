package com.example.matching.controller.post;

import com.example.matching.application.post.EmergingPostApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.EmergingPostConfirmDTO;
import com.example.matching.dto.post.EmergingPostDiscoveryDTO;
import com.example.matching.dto.post.EmergingPostRequestDTO;
import com.example.matching.dto.post.EmergingPostResponseDTO;
import com.example.matching.dto.post.JdQualityCheckRequest;
import com.example.matching.dto.post.JdQualityReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostEmergingPostControllerTest {

    @Test
    void discoverReturnsEmergingPostList() {
        EmergingPostApiFacade facade = mock(EmergingPostApiFacade.class);
        PostEmergingPostController controller = new PostEmergingPostController(facade);

        EmergingPostDiscoveryDTO discovery = new EmergingPostDiscoveryDTO();
        discovery.setCandidateName("AI训练师");
        when(facade.discover(10)).thenReturn(List.of(discovery));

        R<List<EmergingPostDiscoveryDTO>> response = controller.discover(10);

        assertThat(response.getData()).containsExactly(discovery);
    }

    @Test
    void getMarketInsightReturnsInsight() {
        EmergingPostApiFacade facade = mock(EmergingPostApiFacade.class);
        PostEmergingPostController controller = new PostEmergingPostController(facade);

        EmergingPostDiscoveryDTO.MarketInsight insight = new EmergingPostDiscoveryDTO.MarketInsight();
        insight.setAnalyzedJdCount(100);
        when(facade.getMarketInsight()).thenReturn(insight);

        R<EmergingPostDiscoveryDTO.MarketInsight> response = controller.getMarketInsight();

        assertThat(response.getData()).isSameAs(insight);
    }

    @Test
    void checkJdQualityReturnsQualityReport() {
        EmergingPostApiFacade facade = mock(EmergingPostApiFacade.class);
        PostEmergingPostController controller = new PostEmergingPostController(facade);

        JdQualityCheckRequest request = new JdQualityCheckRequest();
        JdQualityReport report = new JdQualityReport();
        when(facade.checkJdQuality(request)).thenReturn(report);

        R<JdQualityReport> response = controller.checkJdQuality(request);

        assertThat(response.getData()).isSameAs(report);
    }

    @Test
    void analyzeReturnsRecommendedAbilities() {
        EmergingPostApiFacade facade = mock(EmergingPostApiFacade.class);
        PostEmergingPostController controller = new PostEmergingPostController(facade);

        EmergingPostRequestDTO request = new EmergingPostRequestDTO();
        when(facade.submitAnalyze(request)).thenReturn("task-2001");

        R<Map<String, String>> response = controller.analyze(request);

        assertThat(response.getData()).containsEntry("taskId", "task-2001")
                .containsEntry("status", "PENDING");
    }

    @Test
    void confirmReturnsCreatedPostId() {
        EmergingPostApiFacade facade = mock(EmergingPostApiFacade.class);
        PostEmergingPostController controller = new PostEmergingPostController(facade);

        EmergingPostConfirmDTO request = new EmergingPostConfirmDTO();
        when(facade.confirm(request)).thenReturn(2001L);

        R<Long> response = controller.confirm(request);

        assertThat(response.getData()).isEqualTo(2001L);
    }
}
