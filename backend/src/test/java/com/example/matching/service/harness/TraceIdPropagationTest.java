package com.example.matching.service.harness;

import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.common.trace.TraceContext;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.service.harness.impl.AiTrustHarnessServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TraceId Propagation")
class TraceIdPropagationTest {

    private AiHarnessCheckLogMapper logMapper;
    private AiTrustHarnessServiceImpl harness;

    @BeforeEach
    void setUp() {
        logMapper = mock(AiHarnessCheckLogMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiHarnessCheckLogMapper> logProvider = mock(ObjectProvider.class);
        when(logProvider.getIfAvailable()).thenReturn(logMapper);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable()).thenReturn(new ObjectMapper());
        @SuppressWarnings("unchecked")
        ObjectProvider<AiContextSourceRefService> sourceRefProvider = mock(ObjectProvider.class);
        when(sourceRefProvider.getIfAvailable()).thenReturn(null);
        harness = new AiTrustHarnessServiceImpl(logProvider, objectMapperProvider, sourceRefProvider, null);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("Harness persisted log carries the request traceId")
    void harnessLogCarriesTraceId() {
        TraceContext.set("trace-req-42");

        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("MATCH_GAP_DIAGNOSIS");
        claim.setClaimText("Java gap");
        claim.setEvidenceText("L2 -> L4");
        claim.setSourceRefs(List.of("fact:ABILITY"));
        AiHarnessDecisionDTO decision = harness.verify(claim);

        assertThat(decision.getTraceId()).isEqualTo("trace-req-42");
        ArgumentCaptor<AiHarnessCheckLog> captured = ArgumentCaptor.forClass(AiHarnessCheckLog.class);
        verify(logMapper).insert(captured.capture());
        assertThat(captured.getValue().getTraceId()).isEqualTo("trace-req-42");
    }

    @Test
    @DisplayName("TraceContext generates a traceId when none present")
    void traceContextGeneratesWhenAbsent() {
        TraceContext.clear();
        String traceId = TraceContext.current();
        assertThat(traceId).startsWith("TRC_");
        assertThat(TraceContext.getOrNull()).isEqualTo(traceId);
    }

    @Test
    @DisplayName("Same traceId flows through decision traceId field")
    void decisionCarriesTraceId() {
        TraceContext.set("trace-req-7");
        AiHarnessDecisionDTO decision = harness.verify(new AiHarnessClaimDTO() {{
            setScenario("PERSON_ABILITY");
            setClaimType("PERSON_ABILITY");
            setClaimText("Java");
        }});
        assertThat(decision.getTraceId()).isEqualTo("trace-req-7");
    }
}
