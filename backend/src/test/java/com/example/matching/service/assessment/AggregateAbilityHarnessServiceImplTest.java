package com.example.matching.service.assessment;

import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.dto.assessment.HarnessBatchItemResultDTO;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.AbilityHarnessBatch;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.assessment.impl.AggregateAbilityHarnessServiceImpl;
import com.example.matching.service.harness.AiTrustHarnessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聚合 Harness 批量审核测试
 * <p>
 * 覆盖：一次批量校验、决策数量不匹配整体失败、REVIEW/BLOCK/PASS 的组状态流转。
 */
class AggregateAbilityHarnessServiceImplTest {

    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private AbilityHarnessBatchMapper batchMapper;
    private AbilityHarnessBatchItemMapper batchItemMapper;
    private AiHarnessCheckLogMapper harnessLogMapper;
    private AiTrustHarnessService harnessService;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private AggregateAbilityHarnessServiceImpl service;

    @BeforeEach
    void setUp() {
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        batchMapper = mock(AbilityHarnessBatchMapper.class);
        batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
        harnessLogMapper = mock(AiHarnessCheckLogMapper.class);
        harnessService = mock(AiTrustHarnessService.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        service = new AggregateAbilityHarnessServiceImpl(
                claimGroupMapper, batchMapper, batchItemMapper, harnessLogMapper,
                harnessService, evidenceCollectionService);
    }

    private PersonAbilityClaimGroup group(Long id, String name, String status) {
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(id);
        group.setWorkflowId(1L);
        group.setEmpId(1L);
        group.setNormalizedAbilityName(name);
        group.setCanonicalTagId(10L);
        group.setStatus(status);
        return group;
    }

    private PersonAbilityClaim claim(Long groupId, String sourceType, int level) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setEmpId(1L);
        claim.setClaimGroupId(groupId);
        claim.setAbilityName("Java");
        claim.setNormalizedAbilityName("Java");
        claim.setClaimedLevel(level);
        claim.setSourceType(sourceType);
        claim.setEvidenceText("在XX项目中负责Java后端开发，完成高并发订单模块");
        claim.setSourceRefsJson("[\"source:RESUME_PARSE:100\"]");
        claim.setConfidenceScore(BigDecimal.valueOf(70));
        return claim;
    }

    private AiHarnessDecisionDTO decision(Long claimGroupId, String result, String riskLevel) {
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setClaimGroupId(claimGroupId);
        decision.setDecision(result);
        decision.setRiskLevel(riskLevel);
        decision.setSupportScore(BigDecimal.valueOf(75));
        decision.setReasons(List.of("multi-source support with evidence"));
        decision.setAcceptedSourceRefs(List.of("source:RESUME_PARSE:100"));
        return decision;
    }

    @Test
    void runAggregateHarness_invokesVerifyBatchOnceForWholeBatch() {
        PersonAbilityClaimGroup g1 = group(1L, "Java", "READY_FOR_AGGREGATE_HARNESS");
        PersonAbilityClaimGroup g2 = group(2L, "Spring", "READY_FOR_AGGREGATE_HARNESS");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(g1, g2));
        when(evidenceCollectionService.listClaimsByGroup(1L)).thenReturn(List.of(claim(1L, "RESUME_PARSE", 3)));
        when(evidenceCollectionService.listClaimsByGroup(2L)).thenReturn(List.of(claim(2L, "AI_INTERVIEW", 3)));
        when(batchMapper.insert((AbilityHarnessBatch) any())).thenAnswer(inv -> {
            ((AbilityHarnessBatch) inv.getArgument(0)).setId(500L);
            return 1;
        });
        when(batchItemMapper.insert((com.example.matching.entity.workflow.AbilityHarnessBatchItem) any())).thenReturn(1);
        when(batchMapper.updateById((AbilityHarnessBatch) any())).thenReturn(1);
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenReturn(1);
        when(harnessService.verifyBatch(anyList()))
                .thenReturn(List.of(decision(2L, "REVIEW", "MEDIUM"), decision(1L, "PASS", "LOW")));

        List<HarnessBatchItemResultDTO> results = service.runAggregateHarness(1L, 600L);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(HarnessBatchItemResultDTO::getClaimGroupId,
                        HarnessBatchItemResultDTO::getDecision)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "REVIEW"),
                        org.assertj.core.groups.Tuple.tuple(2L, "REVIEW"));
        // 一次批量调用而非逐条
        ArgumentCaptor<List<AiHarnessClaimDTO>> claimsCaptor = ArgumentCaptor.forClass(List.class);
        verify(harnessService, times(1)).verifyBatch(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue()).hasSize(2);
        // PASS -> 保持 READY_FOR_AGGREGATE_HARNESS（等待等级确认）；REVIEW -> PENDING_MANUAL_REVIEW
        verify(claimGroupMapper, times(2)).updateById((PersonAbilityClaimGroup) any());
    }

    @Test
    void runAggregateHarness_failsBatchWhenDecisionCountMismatch() {
        PersonAbilityClaimGroup g1 = group(1L, "Java", "READY_FOR_AGGREGATE_HARNESS");
        PersonAbilityClaimGroup g2 = group(2L, "Spring", "READY_FOR_AGGREGATE_HARNESS");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(g1, g2));
        when(evidenceCollectionService.listClaimsByGroup(1L)).thenReturn(List.of(claim(1L, "RESUME_PARSE", 3)));
        when(evidenceCollectionService.listClaimsByGroup(2L)).thenReturn(List.of(claim(2L, "AI_INTERVIEW", 3)));
        when(batchMapper.insert((AbilityHarnessBatch) any())).thenAnswer(inv -> {
            ((AbilityHarnessBatch) inv.getArgument(0)).setId(501L);
            return 1;
        });
        // 批量返回数量不一致 -> 整个批次失败（可重试）
        when(harnessService.verifyBatch(anyList())).thenReturn(List.of(decision(1L, "PASS", "LOW")));

        assertThatThrownBy(() -> service.runAggregateHarness(1L, 600L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批量返回数量不一致");
    }

    @Test
    void runAggregateHarness_blocksGroupWhenDecisionIsBlock() {
        PersonAbilityClaimGroup g1 = group(1L, "Java", "READY_FOR_AGGREGATE_HARNESS");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(g1));
        when(evidenceCollectionService.listClaimsByGroup(1L)).thenReturn(List.of(claim(1L, "RESUME_PARSE", 3)));
        when(batchMapper.insert((AbilityHarnessBatch) any())).thenAnswer(inv -> {
            ((AbilityHarnessBatch) inv.getArgument(0)).setId(502L);
            return 1;
        });
        when(batchItemMapper.insert((com.example.matching.entity.workflow.AbilityHarnessBatchItem) any())).thenReturn(1);
        when(batchMapper.updateById((AbilityHarnessBatch) any())).thenReturn(1);
        when(harnessService.verifyBatch(anyList())).thenReturn(List.of(decision(1L, "BLOCK", "HIGH")));
        PersonAbilityClaimGroup[] captured = new PersonAbilityClaimGroup[1];
        when(claimGroupMapper.updateById((PersonAbilityClaimGroup) any())).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return 1;
        });

        service.runAggregateHarness(1L, 600L);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getStatus()).isEqualTo(EvidenceStatusEnum.BLOCKED.getCode());
    }

    @Test
    void runAggregateHarness_rejectsUnknownEvidenceRef() {
        PersonAbilityClaimGroup g1 = group(1L, "Java", "READY_FOR_AGGREGATE_HARNESS");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(g1));
        when(evidenceCollectionService.listClaimsByGroup(1L)).thenReturn(List.of(claim(1L, "RESUME_PARSE", 3)));
        when(batchMapper.insert((AbilityHarnessBatch) any())).thenAnswer(inv -> {
            ((AbilityHarnessBatch) inv.getArgument(0)).setId(503L);
            return 1;
        });
        AiHarnessDecisionDTO badDecision = decision(1L, "PASS", "LOW");
        badDecision.setAcceptedSourceRefs(List.of("source:UNKNOWN:999")); // 未在输入包中
        when(harnessService.verifyBatch(anyList())).thenReturn(List.of(badDecision));

        assertThatThrownBy(() -> service.runAggregateHarness(1L, 600L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未在输入包中的证据引用");
    }
}
