package com.example.matching.controller.matching;

import com.example.matching.application.matching.ApprovalFlowApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.dto.matching.api.ApprovalFlowResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingApprovalFlowControllerTest {

    @Test
    void initiateCallsFacadeAndReturnsOk() {
        ApprovalFlowApiFacade facade = mock(ApprovalFlowApiFacade.class);
        MatchingApprovalFlowController controller = new MatchingApprovalFlowController(facade);

        R<Void> response = controller.initiate(5001L, 9001L);

        verify(facade).initiate(5001L, 9001L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void approveCallsFacadeAndReturnsOk() {
        ApprovalFlowApiFacade facade = mock(ApprovalFlowApiFacade.class);
        MatchingApprovalFlowController controller = new MatchingApprovalFlowController(facade);

        MatchingApprovalDTO dto = new MatchingApprovalDTO();
        dto.setMatchingRecordId(5001L);
        dto.setApprovalStatus(1);
        dto.setApprovalRemark("同意");

        R<Void> response = controller.approve(dto);

        verify(facade).approve(dto);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void listByRecordIdReturnsFacadeFlowList() {
        ApprovalFlowApiFacade facade = mock(ApprovalFlowApiFacade.class);
        MatchingApprovalFlowController controller = new MatchingApprovalFlowController(facade);

        ApprovalFlowResponse flow = new ApprovalFlowResponse(
                1L, 5001L, 9001L, 1, "初审", 0,
                null, null, LocalDateTime.of(2024, 1, 10, 9, 0));
        when(facade.listByRecordId(5001L)).thenReturn(List.of(flow));

        R<List<ApprovalFlowResponse>> response = controller.listByRecordId(5001L);

        assertThat(response.getData()).containsExactly(flow);
        assertThat(response.getData().get(0).matchingRecordId()).isEqualTo(5001L);
    }

    @Test
    void pendingTasksReturnsFacadeTasks() {
        ApprovalFlowApiFacade facade = mock(ApprovalFlowApiFacade.class);
        MatchingApprovalFlowController controller = new MatchingApprovalFlowController(facade);

        Map<String, Object> task = Map.of("matchingRecordId", 5001L, "nodeName", "初审");
        when(facade.pendingTasks(9001L)).thenReturn(List.of(task));

        R<List<Map<String, Object>>> response = controller.pendingTasks(9001L);

        assertThat(response.getData()).containsExactly(task);
    }
}
