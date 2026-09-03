package com.example.matching.application.governance;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessReviewService;
import com.example.matching.service.governance.AiGovernanceApplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 治理评审工作流契约测试：
 * 先应用治理记录更新，再采纳/驳回能力声明，顺序不可颠倒；
 * 任何一步失败必须抛异常（触发事务回滚），禁止半提交。
 */
@ExtendWith(MockitoExtension.class)
class GovernanceReviewWorkflowTest {

    @Mock
    private AiGovernanceApplyService governanceApplyService;
    @Mock
    private PersonAbilityClaimAdmissionService personClaimAdmissionService;
    @Mock
    private AggregateAbilityHarnessReviewService aggregateHarnessReviewService;

    private GovernanceReviewWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new GovernanceReviewWorkflow(governanceApplyService, personClaimAdmissionService,
                aggregateHarnessReviewService);
    }

    @Test
    void acceptAppliesGovernanceThenAcceptsTheAbilityClaim() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("PASS");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);
        when(personClaimAdmissionService.acceptReview(21L)).thenReturn(true);

        workflow.acceptReview(21L, "reviewer");

        InOrder order = inOrder(governanceApplyService, personClaimAdmissionService);
        order.verify(governanceApplyService).acceptReview(21L, "reviewer");
        order.verify(personClaimAdmissionService).acceptReview(21L);
    }

    /**
     * BLOCK 决策的治理记录（含聚合 PERSON_ABILITY_AGGREGATE 记录，不关联单条 claim）：
     * 人工采纳 = 确认 Harness 否决，不执行 claim 融合（否则 findByHarnessLogId 落空报错）。
     */
    @Test
    void acceptBlockDecisionSkipsClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("BLOCK");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);

        workflow.acceptReview(21L, "reviewer");

        verify(governanceApplyService).acceptReview(21L, "reviewer");
        verify(personClaimAdmissionService, never()).acceptReview(anyLong());
        verify(aggregateHarnessReviewService, never()).acceptAndProject(anyLong(), org.mockito.ArgumentMatchers.anyString());
        verify(aggregateHarnessReviewService, never()).rejectAndFinalize(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void acceptAggregateReviewResumesLevelConfirmationInsteadOfClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("REVIEW");
        checkLog.setScenario("PERSON_ABILITY_AGGREGATE");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);

        workflow.acceptReview(21L, "reviewer");

        verify(aggregateHarnessReviewService).acceptAndProject(21L, "reviewer");
        verify(personClaimAdmissionService, never()).acceptReview(anyLong());
    }

    @Test
    void acceptEvidenceBackfillReviewSkipsClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("REVIEW");
        checkLog.setScenario("PERSON_ABILITY");
        checkLog.setClaimType("EMP_ABILITY");
        checkLog.setBusinessTargetType("EMP_ABILITY");
        checkLog.setBusinessTargetId(99L);
        checkLog.setSourceRefs("[\"fact:EMP_ABILITY:99\"]");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);

        workflow.acceptReview(21L, "reviewer");

        verify(personClaimAdmissionService, never()).acceptReview(anyLong());
    }

    @Test
    void evidenceReferenceForAnotherAbilityDoesNotSkipClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("REVIEW");
        checkLog.setClaimType("EMP_ABILITY");
        checkLog.setBusinessTargetType("EMP_ABILITY");
        checkLog.setBusinessTargetId(99L);
        checkLog.setSourceRefs("[\"fact:EMP_ABILITY:100\"]");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);
        when(personClaimAdmissionService.acceptReview(21L)).thenReturn(true);

        workflow.acceptReview(21L, "reviewer");

        verify(personClaimAdmissionService).acceptReview(21L);
    }

    @Test
    void resumeCapabilityExtractionReviewDoesNotEnterClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog =
                new com.example.matching.entity.harness.AiHarnessCheckLog();
        checkLog.setDecision("REVIEW");
        checkLog.setScenario("RESUME_PARSE");
        checkLog.setClaimType("ABILITY_TAG");
        when(governanceApplyService.getCheckLog(21L)).thenReturn(checkLog);

        workflow.acceptReview(21L, "reviewer");

        verify(personClaimAdmissionService, never()).acceptReview(anyLong());
    }

    @Test
    void rejectAppliesGovernanceThenRejectsTheAbilityClaim() {
        when(governanceApplyService.rejectReview(21L, "reviewer")).thenReturn(true);
        when(personClaimAdmissionService.rejectReview(21L)).thenReturn(true);

        workflow.rejectReview(21L, "reviewer");

        InOrder order = inOrder(governanceApplyService, personClaimAdmissionService);
        order.verify(governanceApplyService).rejectReview(21L, "reviewer");
        order.verify(personClaimAdmissionService).rejectReview(21L);
    }

    @Test
    void acceptThrowsWhenGovernanceApplyFailsAndSkipsClaimAdmission() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(false);

        assertThatThrownBy(() -> workflow.acceptReview(21L, "reviewer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("治理复审未通过，状态未变更");

        verifyNoInteractions(personClaimAdmissionService);
    }

    @Test
    void acceptThrowsWhenClaimAdmissionFails() {
        when(governanceApplyService.acceptReview(21L, "reviewer")).thenReturn(true);
        when(personClaimAdmissionService.acceptReview(21L)).thenReturn(false);

        assertThatThrownBy(() -> workflow.acceptReview(21L, "reviewer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("claim admission failed");
    }

    @Test
    void rejectThrowsWhenGovernanceApplyFailsAndSkipsClaimRejection() {
        when(governanceApplyService.rejectReview(21L, "reviewer")).thenReturn(false);

        assertThatThrownBy(() -> workflow.rejectReview(21L, "reviewer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("治理复审未通过，状态未变更");

        verifyNoInteractions(personClaimAdmissionService);
    }

    @Test
    void rejectThrowsWhenClaimRejectionFails() {
        when(governanceApplyService.rejectReview(21L, "reviewer")).thenReturn(true);
        when(personClaimAdmissionService.rejectReview(21L)).thenReturn(false);

        assertThatThrownBy(() -> workflow.rejectReview(21L, "reviewer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("claim rejection failed");
    }
}
