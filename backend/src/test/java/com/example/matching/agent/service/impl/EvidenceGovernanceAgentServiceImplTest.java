package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import com.example.matching.agent.lc4j.EvidenceGovernanceAiService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceGovernanceAgentServiceImplTest {

    @Test
    void deterministicReviewCannotBeUpgradedToPassByAgent() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AiHarnessDecisionDTO deterministic = new AiHarnessDecisionDTO();
        deterministic.setDecision("REVIEW");
        deterministic.setRiskLevel("MEDIUM");
        deterministic.setSupportScore(new BigDecimal("80"));
        deterministic.setReasons(List.of("requires human review"));

        AiTrustHarnessService harness = mock(AiTrustHarnessService.class);
        when(harness.verify(any())).thenReturn(deterministic);
        EvidenceGovernanceAiService aiService = mock(EvidenceGovernanceAiService.class);
        EvidenceGovernanceAgentResult aiResult = new EvidenceGovernanceAgentResult();
        aiResult.setDecision("PASS");
        aiResult.setRiskLevel("LOW");
        aiResult.setSupportScore(new BigDecimal("90"));
        aiResult.setReasons(List.of("model approved"));
        when(aiService.review(any())).thenReturn(aiResult);

        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceGovernanceAiService> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiService);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiTrustHarnessService> harnessProvider = mock(ObjectProvider.class);
        when(harnessProvider.getIfAvailable()).thenReturn(harness);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(any(), any())).thenReturn(
                new AgentMemoryContextService.ContextRules(List.of(), List.of(), "", ""));

        EvidenceGovernanceAgentServiceImpl service = new EvidenceGovernanceAgentServiceImpl(
                properties, mock(AgentFallbackService.class), new ObjectMapper(),
                aiProvider, harnessProvider, mock(AgentRunConfidencePolicy.class),
                memoryContextService, mock(AgentMemoryRuleEnforcer.class));
        EvidenceGovernanceAgentRequest request = new EvidenceGovernanceAgentRequest();
        request.setClaimText("claim");
        request.setSourceType("RESUME");

        EvidenceGovernanceAgentResult result = service.review(request);

        assertThat(result.getDecision()).isEqualTo("REVIEW");
        assertThat(result.getReasons()).contains("requires human review", "model approved");
    }

    @Test
    void deterministicRetryCannotBeUpgradedToPassByAgent() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AiHarnessDecisionDTO deterministic = new AiHarnessDecisionDTO();
        deterministic.setDecision("RETRY");
        deterministic.setRiskLevel("HIGH");
        deterministic.setSupportScore(new BigDecimal("40"));
        deterministic.setReasons(List.of("source refs unverifiable; fail closed with RETRY"));

        AiTrustHarnessService harness = mock(AiTrustHarnessService.class);
        when(harness.verify(any())).thenReturn(deterministic);
        EvidenceGovernanceAiService aiService = mock(EvidenceGovernanceAiService.class);
        EvidenceGovernanceAgentResult aiResult = new EvidenceGovernanceAgentResult();
        aiResult.setDecision("PASS");
        aiResult.setRiskLevel("LOW");
        aiResult.setSupportScore(new BigDecimal("90"));
        aiResult.setReasons(List.of("model approved"));
        when(aiService.review(any())).thenReturn(aiResult);

        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceGovernanceAiService> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiService);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiTrustHarnessService> harnessProvider = mock(ObjectProvider.class);
        when(harnessProvider.getIfAvailable()).thenReturn(harness);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(any(), any())).thenReturn(
                new AgentMemoryContextService.ContextRules(List.of(), List.of(), "", ""));

        EvidenceGovernanceAgentServiceImpl service = new EvidenceGovernanceAgentServiceImpl(
                properties, mock(AgentFallbackService.class), new ObjectMapper(),
                aiProvider, harnessProvider, mock(AgentRunConfidencePolicy.class),
                memoryContextService, mock(AgentMemoryRuleEnforcer.class));
        EvidenceGovernanceAgentRequest request = new EvidenceGovernanceAgentRequest();
        request.setClaimText("claim");
        request.setSourceType("RESUME");

        EvidenceGovernanceAgentResult result = service.review(request);

        assertThat(result.getDecision()).isEqualTo("RETRY");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getSupportScore()).isEqualTo(new BigDecimal("40"));
        assertThat(result.getReasons()).contains("source refs unverifiable; fail closed with RETRY");
    }

    @Test
    void deterministicRetryWithAgentBlockStaysBlock() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AiHarnessDecisionDTO deterministic = new AiHarnessDecisionDTO();
        deterministic.setDecision("RETRY");
        deterministic.setRiskLevel("HIGH");
        deterministic.setSupportScore(new BigDecimal("40"));
        deterministic.setReasons(List.of("source refs unverifiable"));

        AiTrustHarnessService harness = mock(AiTrustHarnessService.class);
        when(harness.verify(any())).thenReturn(deterministic);
        EvidenceGovernanceAiService aiService = mock(EvidenceGovernanceAiService.class);
        EvidenceGovernanceAgentResult aiResult = new EvidenceGovernanceAgentResult();
        aiResult.setDecision("BLOCK");
        aiResult.setRiskLevel("HIGH");
        aiResult.setSupportScore(BigDecimal.ZERO);
        aiResult.setReasons(List.of("model blocked"));
        when(aiService.review(any())).thenReturn(aiResult);

        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceGovernanceAiService> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiService);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiTrustHarnessService> harnessProvider = mock(ObjectProvider.class);
        when(harnessProvider.getIfAvailable()).thenReturn(harness);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(any(), any())).thenReturn(
                new AgentMemoryContextService.ContextRules(List.of(), List.of(), "", ""));

        EvidenceGovernanceAgentServiceImpl service = new EvidenceGovernanceAgentServiceImpl(
                properties, mock(AgentFallbackService.class), new ObjectMapper(),
                aiProvider, harnessProvider, mock(AgentRunConfidencePolicy.class),
                memoryContextService, mock(AgentMemoryRuleEnforcer.class));
        EvidenceGovernanceAgentRequest request = new EvidenceGovernanceAgentRequest();
        request.setClaimText("claim");
        request.setSourceType("RESUME");

        EvidenceGovernanceAgentResult result = service.review(request);

        assertThat(result.getDecision()).isEqualTo("BLOCK");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void deterministicRetryWithoutAgentResultStaysRetry() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AiHarnessDecisionDTO deterministic = new AiHarnessDecisionDTO();
        deterministic.setDecision("RETRY");
        deterministic.setRiskLevel("HIGH");
        deterministic.setSupportScore(new BigDecimal("35"));
        deterministic.setReasons(List.of("resolver unavailable"));

        AiTrustHarnessService harness = mock(AiTrustHarnessService.class);
        when(harness.verify(any())).thenReturn(deterministic);
        EvidenceGovernanceAiService aiService = mock(EvidenceGovernanceAiService.class);
        when(aiService.review(any())).thenThrow(new RuntimeException("LLM down"));

        @SuppressWarnings("unchecked")
        ObjectProvider<EvidenceGovernanceAiService> aiProvider = mock(ObjectProvider.class);
        when(aiProvider.getIfAvailable()).thenReturn(aiService);
        @SuppressWarnings("unchecked")
        ObjectProvider<AiTrustHarnessService> harnessProvider = mock(ObjectProvider.class);
        when(harnessProvider.getIfAvailable()).thenReturn(harness);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(any(), any())).thenReturn(
                new AgentMemoryContextService.ContextRules(List.of(), List.of(), "", ""));

        EvidenceGovernanceAgentServiceImpl service = new EvidenceGovernanceAgentServiceImpl(
                properties, mock(AgentFallbackService.class), new ObjectMapper(),
                aiProvider, harnessProvider, mock(AgentRunConfidencePolicy.class),
                memoryContextService, mock(AgentMemoryRuleEnforcer.class));
        EvidenceGovernanceAgentRequest request = new EvidenceGovernanceAgentRequest();
        request.setClaimText("claim");
        request.setSourceType("RESUME");

        EvidenceGovernanceAgentResult result = service.review(request);

        assertThat(result.getDecision()).isEqualTo("RETRY");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getSupportScore()).isEqualTo(new BigDecimal("35"));
    }
}
