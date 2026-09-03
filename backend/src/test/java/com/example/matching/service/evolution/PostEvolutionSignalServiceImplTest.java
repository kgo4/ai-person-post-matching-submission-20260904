package com.example.matching.service.evolution;

import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.service.evolution.impl.PostEvolutionSignalServiceImpl;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostEvolutionSignalServiceImplTest {

    @Mock private PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    @Mock private AiTrustHarnessService harnessService;
    @Mock private PostEvolutionTaskMapper taskMapper;
    @Mock private PostEvolutionChangeItemMapper changeItemMapper;
    @Mock private PostEvolutionEvidenceMapper evidenceMapper;
    @Mock private EvolutionAbilityTagResolver abilityTagResolver;

    @Test
    void generateSignalsFromWhitepaper_skipsEvidenceWithoutResolvedAbilityTag() {
        when(knowledgeRetrievalService.retrieveIndustryTrends(any(), any(), anyInt())).thenReturn(List.of(
                new PostEvolutionKnowledgeRetrievalService.RetrievalResult(
                        "chunk-1", "加强跨团队协作与交付能力", "趋势", "ABILITY_REQUIREMENT", "doc:1", 0.9D)
        ));
        when(abilityTagResolver.resolve("加强跨团队协作与交付能力")).thenReturn(null);

        PostEvolutionSignalServiceImpl service = new PostEvolutionSignalServiceImpl(
                knowledgeRetrievalService,
                harnessService,
                taskMapper,
                changeItemMapper,
                evidenceMapper,
                new ObjectMapper(),
                abilityTagResolver);

        List<PostEvolutionSignalService.EvolutionSignal> signals = service.generateSignalsFromWhitepaper("互联网", 1L);

        assertThat(signals).isEmpty();
        verify(abilityTagResolver).resolve("加强跨团队协作与交付能力");
        verifyNoInteractions(harnessService, taskMapper, changeItemMapper, evidenceMapper);
    }

    @Test
    void convertingSignalsPassesChangeTypeToHarness() {
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision(AiHarnessDecisionDTO.REVIEW);
        decision.setRiskLevel("HIGH");
        when(harnessService.verify(any(AiHarnessClaimDTO.class))).thenReturn(decision);
        PostEvolutionSignalServiceImpl service = new PostEvolutionSignalServiceImpl(
                knowledgeRetrievalService,
                harnessService,
                taskMapper,
                changeItemMapper,
                evidenceMapper,
                new ObjectMapper(),
                abilityTagResolver);
        PostEvolutionSignalService.EvolutionSignal signal = new PostEvolutionSignalService.EvolutionSignal(
                "INDUSTRY_TREND", "Kubernetes", 15L, "UPGRADE_LEVEL", "market demand evidence",
                List.of("source:MARKET_JD:15"), 0.9D, 0.9D);

        service.convertSignalsToEvolutionTask(7L, List.of(signal), "MANUAL", 3L);

        ArgumentCaptor<AiHarnessClaimDTO> claimCaptor = ArgumentCaptor.forClass(AiHarnessClaimDTO.class);
        verify(harnessService).verify(claimCaptor.capture());
        assertThat(claimCaptor.getValue().getChangeType()).isEqualTo("UPGRADE_LEVEL");
    }

    @Test
    void signalTaskCreatedInWaitConfirmAndItemsPendingEvenWhenHarnessPasses() {
        // M13：信号任务创建为 WAIT_CONFIRM，所有变更项初始 PENDING，即使 Harness PASS
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision(AiHarnessDecisionDTO.PASS);
        decision.setRiskLevel("LOW");
        when(harnessService.verify(any(AiHarnessClaimDTO.class))).thenReturn(decision);
        when(changeItemMapper.selectCount(any())).thenReturn(0L);
        PostEvolutionSignalServiceImpl service = new PostEvolutionSignalServiceImpl(
                knowledgeRetrievalService,
                harnessService,
                taskMapper,
                changeItemMapper,
                evidenceMapper,
                new ObjectMapper(),
                abilityTagResolver);
        PostEvolutionSignalService.EvolutionSignal signal = new PostEvolutionSignalService.EvolutionSignal(
                "INDUSTRY_TREND", "Kubernetes", 15L, "ADD_ABILITY", "market demand evidence",
                List.of("source:MARKET_JD:15"), 0.9D, 0.9D);

        service.convertSignalsToEvolutionTask(7L, List.of(signal), "MANUAL", 3L);

        org.mockito.ArgumentCaptor<com.example.matching.entity.evolution.PostEvolutionTask> taskCaptor =
                org.mockito.ArgumentCaptor.forClass(com.example.matching.entity.evolution.PostEvolutionTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskStatus()).isEqualTo("WAIT_CONFIRM");

        org.mockito.ArgumentCaptor<com.example.matching.entity.evolution.PostEvolutionChangeItem> itemCaptor =
                org.mockito.ArgumentCaptor.forClass(com.example.matching.entity.evolution.PostEvolutionChangeItem.class);
        verify(changeItemMapper).insert(itemCaptor.capture());
        // Harness PASS 也必须是 PENDING（人工确认）
        assertThat(itemCaptor.getValue().getConfirmStatus()).isEqualTo("PENDING");
        assertThat(itemCaptor.getValue().getFingerprint()).isNotBlank();
    }

    @Test
    void duplicateSignalWithinCooldownDoesNotCreateChangeItem() {
        // M4：同指纹变更项在 7 天冷却窗口内不重复创建
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setDecision(AiHarnessDecisionDTO.PASS);
        when(harnessService.verify(any(AiHarnessClaimDTO.class))).thenReturn(decision);
        when(changeItemMapper.selectCount(any())).thenReturn(1L); // 冷却窗口内已有同指纹
        PostEvolutionSignalServiceImpl service = new PostEvolutionSignalServiceImpl(
                knowledgeRetrievalService,
                harnessService,
                taskMapper,
                changeItemMapper,
                evidenceMapper,
                new ObjectMapper(),
                abilityTagResolver);
        PostEvolutionSignalService.EvolutionSignal signal = new PostEvolutionSignalService.EvolutionSignal(
                "INDUSTRY_TREND", "Kubernetes", 15L, "ADD_ABILITY", "market demand evidence",
                List.of("source:MARKET_JD:15"), 0.9D, 0.9D);

        service.convertSignalsToEvolutionTask(7L, List.of(signal), "MANUAL", 3L);

        verify(changeItemMapper, org.mockito.Mockito.never())
                .insert(any(com.example.matching.entity.evolution.PostEvolutionChangeItem.class));
        verify(evidenceMapper, org.mockito.Mockito.never())
                .insert(any(com.example.matching.entity.evolution.PostEvolutionEvidence.class));
    }
}
