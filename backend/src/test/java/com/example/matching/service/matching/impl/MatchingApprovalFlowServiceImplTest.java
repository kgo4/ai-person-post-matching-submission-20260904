package com.example.matching.service.matching.impl;

import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.entity.matching.MatchingApprovalFlow;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingApprovalFlowServiceImplTest {

    @Mock private MatchingApprovalFlowMapper approvalFlowMapper;
    @Mock private MatchingRecordMapper matchingRecordMapper;

    @Test
    void approveClaimsPendingNodeWithConditionalUpdate() {
        MatchingApprovalFlowServiceImpl service = new MatchingApprovalFlowServiceImpl(matchingRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", approvalFlowMapper);

        MatchingRecord record = new MatchingRecord();
        record.setId(11L);
        MatchingApprovalFlow flow = new MatchingApprovalFlow();
        flow.setId(22L);
        flow.setMatchingRecordId(11L);
        flow.setApprovalStatus(0);
        when(matchingRecordMapper.selectById(11L)).thenReturn(record);
        when(approvalFlowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalFlowMapper.update(isNull(), any())).thenReturn(1);

        MatchingApprovalDTO dto = new MatchingApprovalDTO();
        dto.setMatchingRecordId(11L);
        dto.setApprovalStatus(2);
        dto.setApprovalRemark("approved");
        service.approve(dto);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<MatchingApprovalFlow>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(approvalFlowMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("approval_status");
        verify(matchingRecordMapper).updateById(record);
    }

    @Test
    void approveRejectsUnsupportedDecisionStatus() {
        MatchingApprovalFlowServiceImpl service = new MatchingApprovalFlowServiceImpl(matchingRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", approvalFlowMapper);

        MatchingApprovalDTO dto = new MatchingApprovalDTO();
        dto.setMatchingRecordId(11L);
        dto.setApprovalStatus(99);

        assertThatThrownBy(() -> service.approve(dto))
                .hasMessageContaining("approval status");
    }

    @Test
    void initiateApprovalRejectsTerminalApprovalRecord() {
        MatchingApprovalFlowServiceImpl service = new MatchingApprovalFlowServiceImpl(matchingRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", approvalFlowMapper);

        MatchingRecord record = new MatchingRecord();
        record.setId(11L);
        record.setApprovalStatus(2);
        when(matchingRecordMapper.selectById(11L)).thenReturn(record);

        assertThatThrownBy(() -> service.initiateApproval(11L, 99L))
                .hasMessageContaining("already finalized");
    }
}
