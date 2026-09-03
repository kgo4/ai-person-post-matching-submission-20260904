package com.example.matching.controller.assessment;

import com.example.matching.application.assessment.CapabilityAssessmentFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.assessment.CreateAssessmentInterviewResponse;
import com.example.matching.dto.assessment.EligibilityPrecheckRequest;
import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.GenerateVerificationTestResponse;
import com.example.matching.dto.assessment.HarnessBatchItemResultDTO;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.dto.assessment.SubmitTestRequest;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapabilityAssessmentControllerTest {

    private static final int SUCCESS_CODE = 200;

    private static CapabilityAssessmentVO.WorkflowView workflowView(Long workflowId) {
        CapabilityAssessmentVO.WorkflowView view = new CapabilityAssessmentVO.WorkflowView();
        view.setWorkflowId(workflowId);
        view.setEmpId(100L);
        view.setWorkflowStatus("RESUME_EVIDENCE_READY");
        view.setCurrentStage("AI_TEST");
        return view;
    }

    private static CapabilityAssessmentVO.StageRunView stageRunView(Long stageRunId) {
        CapabilityAssessmentVO.StageRunView view = new CapabilityAssessmentVO.StageRunView();
        view.setStageRunId(stageRunId);
        view.setStageType("AI_TEST");
        view.setStatus("SUCCEEDED");
        return view;
    }

    private static PersonCapabilityStageRun stageRun(Long id) {
        PersonCapabilityStageRun run = new PersonCapabilityStageRun();
        run.setId(id);
        run.setStageType("AI_TEST");
        run.setStatus("SUCCEEDED");
        return run;
    }

    private static PersonAbilityLevelDecision decision(Long id, Integer finalLevel) {
        PersonAbilityLevelDecision decision = new PersonAbilityLevelDecision();
        decision.setId(id);
        decision.setWorkflowId(1L);
        decision.setFinalLevel(finalLevel);
        decision.setFinalConfidence(80);
        decision.setDecisionStatus("AUTO_CONFIRMED");
        return decision;
    }

    @Test
    void getOrCreateActiveReturnsWorkflowView() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.getOrCreateWorkflow(anyLong(), any())).thenReturn(workflowView(10L));

        R<CapabilityAssessmentVO.WorkflowView> response = controller.getOrCreateActive(100L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getWorkflowId()).isEqualTo(10L);
        assertThat(response.getData().getEmpId()).isEqualTo(100L);
    }

    @Test
    void getActiveReturnsWorkflowView() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.getActiveWorkflow(anyLong())).thenReturn(workflowView(11L));

        R<CapabilityAssessmentVO.WorkflowView> response = controller.getActive(100L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getWorkflowId()).isEqualTo(11L);
    }

    @Test
    void getWorkflowReturnsWorkflowView() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.getWorkflow(anyLong())).thenReturn(workflowView(12L));

        R<CapabilityAssessmentVO.WorkflowView> response = controller.getWorkflow(12L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getWorkflowId()).isEqualTo(12L);
    }

    @Test
    void submitResumeEvidenceReturnsSavedCount() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        ResumeAbilityClaimDTO claim = new ResumeAbilityClaimDTO();
        claim.setAbilityName("Java");
        claim.setClaimedLevel(3);
        when(facade.submitResumeEvidence(anyLong(), anyLong(), anyList(), any())).thenReturn(3);

        R<Integer> response = controller.submitResumeEvidence(100L, 500L, List.of(claim));

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).isEqualTo(3);
        assertThat(response.getMessage()).contains("3");
    }

    @Test
    void generateTestReturnsTestResponse() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        GenerateVerificationTestResponse resp = new GenerateVerificationTestResponse();
        resp.setTestId(5L);
        resp.setPostId(6L);
        resp.setStageRun(stageRunView(20L));
        when(facade.generateTest(anyLong(), anyLong(), any())).thenReturn(resp);

        R<GenerateVerificationTestResponse> response = controller.generateTest(1L, 6L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getTestId()).isEqualTo(5L);
        assertThat(response.getData().getPostId()).isEqualTo(6L);
    }

    @Test
    void submitTestReturnsStageRunView() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        SubmitTestRequest request = new SubmitTestRequest();
        request.setAnswers(Map.of("q1", "A", "q2", "B"));
        when(facade.submitTest(anyLong(), anyLong(), anyMap(), any())).thenReturn(stageRun(88L));
        when(facade.toStageRunView(any())).thenReturn(stageRunView(88L));

        R<CapabilityAssessmentVO.StageRunView> response = controller.submitTest(1L, 2L, request);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getStageRunId()).isEqualTo(88L);
        assertThat(response.getData().getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void submitTestWithNullStageRunReturnsEmptyView() {
        // CALLS_REAL_METHODS 使接口 default 方法 toStageRunView 真实执行，
        // 验证其对 null 入参返回空 view 的分支逻辑。
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class, CALLS_REAL_METHODS);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        SubmitTestRequest request = new SubmitTestRequest();
        request.setAnswers(Map.of("q1", "A"));
        when(facade.submitTest(anyLong(), anyLong(), anyMap(), any())).thenReturn(null);

        R<CapabilityAssessmentVO.StageRunView> response = controller.submitTest(1L, 2L, request);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getStageRunId()).isNull();
    }

    @Test
    void createInterviewReturnsInterviewResponse() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        CreateAssessmentInterviewResponse resp = new CreateAssessmentInterviewResponse();
        resp.setSessionId(9L);
        resp.setPostId(6L);
        resp.setStageRun(stageRunView(30L));
        when(facade.createInterview(anyLong(), any())).thenReturn(resp);

        R<CreateAssessmentInterviewResponse> response = controller.createInterview(1L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getSessionId()).isEqualTo(9L);
        assertThat(response.getData().getPostId()).isEqualTo(6L);
    }

    @Test
    void finishInterviewReturnsStageRunView() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.finishInterview(anyLong(), anyLong(), any())).thenReturn(stageRun(99L));
        when(facade.toStageRunView(any())).thenReturn(stageRunView(99L));

        R<CapabilityAssessmentVO.StageRunView> response = controller.finishInterview(1L, 9L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getStageRunId()).isEqualTo(99L);
    }

    @Test
    void getHarnessResultsReturnsBatchItems() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        HarnessBatchItemResultDTO item = new HarnessBatchItemResultDTO();
        item.setClaimGroupId(1L);
        item.setDecision("PASS");
        when(facade.getHarnessResults(anyLong())).thenReturn(List.of(item));

        R<List<HarnessBatchItemResultDTO>> response = controller.getHarnessResults(1L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getDecision()).isEqualTo("PASS");
    }

    @Test
    void listDecisionsReturnsVos() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.listDecisions(anyLong())).thenReturn(List.of(decision(3L, 4)));

        R<List<PersonAbilityLevelDecisionVO>> response = controller.listDecisions(1L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getId()).isEqualTo(3L);
        assertThat(response.getData().get(0).getFinalLevel()).isEqualTo(4);
    }

    @Test
    void confirmDecisionReturnsVo() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        PersonAbilityLevelDecision confirmed = decision(4L, 5);
        confirmed.setFinalConfidence(90);
        confirmed.setDecisionStatus("HUMAN_CONFIRMED");
        when(facade.humanConfirmDecision(anyLong(), any(), any(), any(), any()))
                .thenReturn(confirmed);

        R<PersonAbilityLevelDecisionVO> response = controller.confirmDecision(4L, 5, 90, "人工复核通过");

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getId()).isEqualTo(4L);
        assertThat(response.getData().getFinalLevel()).isEqualTo(5);
        assertThat(response.getData().getFinalConfidence()).isEqualTo(90);
        assertThat(response.getData().getDecisionStatus()).isEqualTo("HUMAN_CONFIRMED");
    }

    @Test
    void rejectDecisionReturnsVo() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        PersonAbilityLevelDecision rejected = decision(5L, null);
        rejected.setDecisionStatus("REJECTED");
        when(facade.humanRejectDecision(anyLong(), any(), any())).thenReturn(rejected);

        R<PersonAbilityLevelDecisionVO> response = controller.rejectDecision(5L, "证据不足");

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getId()).isEqualTo(5L);
        assertThat(response.getData().getDecisionStatus()).isEqualTo("REJECTED");
    }

    @Test
    void recalculateDecisionsReturnsOk() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);

        R<Void> response = controller.recalculateDecisions(1L, "v2");

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).isNull();
    }

    @Test
    void retryStageReturnsOk() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);

        R<Void> response = controller.retryStage(1L, "AI_TEST");

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).isNull();
    }

    @Test
    void getProfileReturnsMap() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        when(facade.getProfile(anyLong())).thenReturn(Map.of("formal", 2, "provisional", 1));

        R<Map<String, Object>> response = controller.getProfile(100L);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).containsEntry("formal", 2);
    }

    @Test
    void precheckEligibilityReturnsResults() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        EligibilityPrecheckResult result = new EligibilityPrecheckResult();
        result.setEmpId(100L);
        result.setDefaultAction("NORMAL_MATCH");
        EligibilityPrecheckRequest request = new EligibilityPrecheckRequest();
        request.setEmpIds(List.of(100L));
        request.setPostIds(List.of(200L));
        when(facade.precheckEligibility(anyList(), anyList())).thenReturn(List.of(result));

        R<List<EligibilityPrecheckResult>> response = controller.precheckEligibility(request);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getDefaultAction()).isEqualTo("NORMAL_MATCH");
    }

    @Test
    void buildProvisionalSnapshotReturnsSnapshot() {
        CapabilityAssessmentFacade facade = mock(CapabilityAssessmentFacade.class);
        CapabilityAssessmentController controller = new CapabilityAssessmentController(facade);
        ProvisionalAbilitySnapshotDTO snapshot = new ProvisionalAbilitySnapshotDTO();
        snapshot.setSnapshotToken("TK-001");
        snapshot.setEmpId(100L);
        when(facade.buildProvisionalSnapshot(anyLong(), anyBoolean(), any())).thenReturn(snapshot);

        R<ProvisionalAbilitySnapshotDTO> response = controller.buildProvisionalSnapshot(100L, true);

        assertThat(response.getCode()).isEqualTo(SUCCESS_CODE);
        assertThat(response.getData().getSnapshotToken()).isEqualTo("TK-001");
    }
}
