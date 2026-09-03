package com.example.matching.application.matching;

import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.dto.matching.api.ApprovalFlowResponse;
import com.example.matching.entity.matching.MatchingApprovalFlow;
import com.example.matching.service.matching.MatchingApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApprovalFlowApiFacade {

    private final MatchingApprovalFlowService matchingApprovalFlowService;

    public void initiate(Long matchingRecordId, Long adminApproverId) {
        matchingApprovalFlowService.initiateApproval(matchingRecordId, adminApproverId);
    }

    public void approve(MatchingApprovalDTO dto) {
        matchingApprovalFlowService.approve(dto);
    }

    public List<ApprovalFlowResponse> listByRecordId(Long matchingRecordId) {
        List<MatchingApprovalFlow> entities = matchingApprovalFlowService.listByRecordId(matchingRecordId);
        return entities.stream().map(this::toResponse).toList();
    }

    public List<Map<String, Object>> pendingTasks(Long userId) {
        return matchingApprovalFlowService.getPendingTasks(userId);
    }

    private ApprovalFlowResponse toResponse(MatchingApprovalFlow e) {
        return new ApprovalFlowResponse(
            e.getId(), e.getMatchingRecordId(), e.getApproverId(),
            e.getNodeOrder(), e.getNodeName(), e.getApprovalStatus(),
            e.getApprovalRemark(), e.getApprovalTime(), e.getCreatedTime()
        );
    }
}
