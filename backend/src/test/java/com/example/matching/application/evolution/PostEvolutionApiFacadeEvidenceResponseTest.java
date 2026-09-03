package com.example.matching.application.evolution;

import com.example.matching.dto.evolution.api.PostEvolutionChangeItemResponse;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostEvolutionApiFacadeEvidenceResponseTest {

    @Test
    void mapsOnlyPersistedItemEvidenceAndBuildsSummaryFromThoseRecords() {
        PostEvolutionChangeItem item = new PostEvolutionChangeItem();
        item.setId(17L);
        item.setTaskId(9L);
        item.setAbilityName("TensorRT");

        PostEvolutionEvidence evidence = new PostEvolutionEvidence();
        evidence.setId(31L);
        evidence.setChangeItemId(17L);
        evidence.setSourceType("INDUSTRY_WHITEPAPER");
        evidence.setSourceTitle("边缘智能白皮书");
        evidence.setEvidenceText("TensorRT 推理优化已成为边缘部署的关键能力。");
        evidence.setCollectedTime(LocalDateTime.of(2026, 9, 2, 9, 30));
        evidence.setSimilarityScore(new BigDecimal("0.76"));
        evidence.setTrustScore(new BigDecimal("0.87"));
        evidence.setSourceRef("source:WHITEPAPER:31");

        PostEvolutionChangeItemResponse response =
                PostEvolutionApiFacade.toChangeItemResponse(item, List.of(evidence));

        assertThat(response.evidenceItems()).hasSize(1);
        assertThat(response.evidenceItems().get(0).sourceRef()).isEqualTo("source:WHITEPAPER:31");
        assertThat(response.evidenceItems().get(0).evidenceText())
                .contains("TensorRT 推理优化");
        assertThat(response.evidenceSummary().sourceCount()).isEqualTo(1);
        assertThat(response.evidenceSummary().maxTrustScore()).isEqualByComparingTo("0.87");
        assertThat(response.evidenceSummary().averageTrustScore()).isEqualByComparingTo("0.87");
        assertThat(response.evidenceSummary().crossSourceVerified()).isFalse();
    }

    @Test
    void leavesEvidenceFieldsEmptyWhenNoPersistedEvidenceIsLinked() {
        PostEvolutionChangeItem item = new PostEvolutionChangeItem();
        item.setId(17L);
        item.setTaskId(9L);

        PostEvolutionChangeItemResponse response =
                PostEvolutionApiFacade.toChangeItemResponse(item, List.of());

        assertThat(response.evidenceItems()).isEmpty();
        assertThat(response.evidenceSummary()).isNull();
    }

}
