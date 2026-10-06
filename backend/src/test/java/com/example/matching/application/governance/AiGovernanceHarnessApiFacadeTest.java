package com.example.matching.application.governance;

import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.service.governance.AiGovernanceApplyService;
import com.example.matching.service.system.AuditQueryService;
import com.example.matching.dto.governance.api.HarnessCheckRequest;
import com.example.matching.dto.governance.api.BatchHarnessReviewRequest;
import com.example.matching.dto.governance.api.AssessmentHarnessReviewView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.mockito.Mockito.never;

class AiGovernanceHarnessApiFacadeTest {

    @Test
    void pendingAssessmentGroups_excludeProcessedRecordsAndCountSafeAiApprovals() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);

        AiHarnessCheckLog safe = pendingLog(301L, "PASS", "LOW", 0);
        AiHarnessCheckLog needsManualReview = pendingLog(302L, "REVIEW", "MEDIUM", 0);
        when(auditService.listAssessmentHarnessByReviewStatuses(eq(Set.of("PENDING"))))
                .thenReturn(List.of(safe, needsManualReview));
        when(auditService.resolveHarnessPersons(List.of(safe, needsManualReview))).thenReturn(Map.of(
                301L, new AuditQueryService.HarnessPerson(7L, "张三", "E007"),
                302L, new AuditQueryService.HarnessPerson(7L, "张三", "E007")));

        var groups = facade.listAssessmentPersonGroups(AssessmentHarnessReviewView.PENDING);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).empId()).isEqualTo(7L);
        assertThat(groups.get(0).items()).hasSize(2);
        assertThat(groups.get(0).safeAiAcceptCount()).isEqualTo(1);
    }

    @Test
    void historyAssessmentGroups_queryOnlyManualTerminalReviewStates() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);

        when(auditService.listAssessmentHarnessByReviewStatuses(
                Set.of("ACCEPTED", "REJECTED", "RESOLVED"))).thenReturn(List.of());
        when(auditService.resolveHarnessPersons(List.of())).thenReturn(Map.of());

        assertThat(facade.listAssessmentPersonGroups(AssessmentHarnessReviewView.HISTORY)).isEmpty();
        verify(auditService).listAssessmentHarnessByReviewStatuses(
                Set.of("ACCEPTED", "REJECTED", "RESOLVED"));
    }

    @Test
    void batchAccept_skipsUnsafeRecordsAndAppliesOnlySafePendingPasses() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);

        AiHarnessCheckLog safe = pendingLog(101L, "PASS", "LOW", 0);
        AiHarnessCheckLog blocked = pendingLog(102L, "BLOCK", "LOW", 0);
        when(auditService.getHarnessById(101L)).thenReturn(safe);
        when(auditService.getHarnessById(102L)).thenReturn(blocked);
        when(workflow.acceptReview(101L, "批量确认", false)).thenReturn(true);

        var result = facade.batchReview(new BatchHarnessReviewRequest(
                List.of(101L, 102L), "ACCEPTED", "批量确认", null));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.results()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(102L);
            assertThat(item.reason()).contains("不满足批量通过条件");
        });
        verify(workflow).acceptReview(101L, "批量确认", false);
        verify(workflow, never()).acceptReview(102L, "批量确认", false);
    }

    @Test
    void batchReject_requiresReasonAndReusesSingleReviewWorkflow() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);
        when(auditService.getHarnessById(201L)).thenReturn(pendingLog(201L, "REVIEW", "MEDIUM", 0));
        when(workflow.rejectReview(201L, "证据不足")).thenReturn(true);

        var result = facade.batchReview(new BatchHarnessReviewRequest(
                List.of(201L), "REJECTED", "证据不足", "EVIDENCE_INSUFFICIENT"));

        assertThat(result.successCount()).isEqualTo(1);
        verify(workflow).rejectReview(201L, "证据不足");
    }

    private static AiHarnessCheckLog pendingLog(Long id, String decision, String riskLevel, int selfEvidence) {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(id);
        log.setReviewStatus("PENDING");
        log.setDecision(decision);
        log.setRiskLevel(riskLevel);
        log.setIsSelfEvidence(selfEvidence);
        return log;
    }

    @Test
    void acceptingNonPersonnelClaimDelegatesToUnifiedApplyService() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(88L);
        log.setClaimType("POST_EVOLUTION_CHANGE");
        log.setReviewStatus("PENDING");
        when(auditService.getHarnessById(88L)).thenReturn(log);
        when(workflow.acceptReview(88L, "confirmed", false)).thenReturn(true);

        facade.updateReviewStatus(88L, new HarnessCheckRequest("ACCEPTED", "confirmed", null, false));

        verify(workflow).acceptReview(88L, "confirmed", false);
    }

    @Test
    void acceptingAutoPassedClaimIsRejectedBeforeBusinessApplication() {
        AuditQueryService auditService = mock(AuditQueryService.class);
        AiGovernanceApplyService applyService = mock(AiGovernanceApplyService.class);
        GovernanceReviewWorkflow workflow = mock(GovernanceReviewWorkflow.class);
        AiGovernanceHarnessApiFacade facade = new AiGovernanceHarnessApiFacade(auditService, applyService, workflow);
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(89L);
        log.setReviewStatus("AUTO_PASSED");
        when(auditService.getHarnessById(89L)).thenReturn(log);

        assertThatThrownBy(() -> facade.updateReviewStatus(
                89L, new HarnessCheckRequest("ACCEPTED", "confirmed", null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already processed");

        verifyNoInteractions(applyService, workflow);
    }
}
