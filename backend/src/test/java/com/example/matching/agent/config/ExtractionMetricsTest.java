package com.example.matching.agent.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionMetricsTest {

    @Test
    void recordsScenarioScopedValidationAndEvidenceMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractionMetrics metrics = new ExtractionMetrics(registry);

        metrics.validationFailed(ExtractionMetrics.SCENARIO_EMPLOYEE);
        metrics.evidenceNotLocatable(ExtractionMetrics.SCENARIO_POST);
        metrics.sourceRefInvalid(ExtractionMetrics.SCENARIO_EMPLOYEE);

        assertThat(registry.get("extraction.validation.failed").tag("scenario", "EMPLOYEE").counter().count())
                .isEqualTo(1d);
        assertThat(registry.get("extraction.evidence.not_locatable").tag("scenario", "POST").counter().count())
                .isEqualTo(1d);
        assertThat(registry.get("extraction.source_ref.invalid").tag("scenario", "EMPLOYEE").counter().count())
                .isEqualTo(1d);
    }

    @Test
    void recordsEveryTagAdmissionOutcomeAndGraphToolRegressionMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractionMetrics metrics = new ExtractionMetrics(registry);

        metrics.tagReused();
        metrics.tagFormalCreated();
        metrics.tagCandidateCreated();
        metrics.tagRejected();
        metrics.graphToolCalled();

        assertThat(registry.get("extraction.tag.reused").counter().count()).isEqualTo(1d);
        assertThat(registry.get("extraction.tag.formal_created").counter().count()).isEqualTo(1d);
        assertThat(registry.get("extraction.tag.candidate_created").counter().count()).isEqualTo(1d);
        assertThat(registry.get("extraction.tag.rejected").counter().count()).isEqualTo(1d);
        assertThat(registry.get("extraction.graph_tool_calls").counter().count()).isEqualTo(1d);
    }
}
