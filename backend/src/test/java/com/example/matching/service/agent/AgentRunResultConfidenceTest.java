package com.example.matching.service.agent;

import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.service.impl.AgentRunConfidencePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunResultConfidenceTest {

    private final AgentRunConfidencePolicy policy = new AgentRunConfidencePolicy();

    @Test
    void weightedMeanOfScoredRefs() {
        AgentSourceRef ref1 = new AgentSourceRef();
        ref1.setConfidenceScore(new BigDecimal("80"));
        AgentSourceRef ref2 = new AgentSourceRef();
        ref2.setConfidenceScore(new BigDecimal("60"));
        BigDecimal result = policy.calculate(List.of(ref1, ref2), false);
        assertThat(result).isEqualTo(new BigDecimal("70.00"));
    }

    @Test
    void nullWhenNoRefs() {
        assertThat(policy.calculate(List.of(), false)).isNull();
        assertThat(policy.calculate(null, false)).isNull();
    }

    @Test
    void nullWhenNoScoredRefs() {
        AgentSourceRef ref = new AgentSourceRef();
        assertThat(policy.calculate(List.of(ref), false)).isNull();
    }

    @Test
    void fallbackCappedAt60() {
        AgentSourceRef ref = new AgentSourceRef();
        ref.setConfidenceScore(new BigDecimal("90"));
        BigDecimal result = policy.calculate(List.of(ref), true);
        assertThat(result).isEqualTo(new BigDecimal("60.00"));
    }

    @Test
    void fallbackNotCappedWhenBelow60() {
        AgentSourceRef ref = new AgentSourceRef();
        ref.setConfidenceScore(new BigDecimal("45"));
        BigDecimal result = policy.calculate(List.of(ref), true);
        assertThat(result).isEqualTo(new BigDecimal("45.00"));
    }
}
