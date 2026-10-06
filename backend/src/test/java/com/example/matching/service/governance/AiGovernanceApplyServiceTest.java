package com.example.matching.service.governance;

import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.service.governance.impl.AiGovernanceApplyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiGovernanceApplyServiceImpl 只更新治理记录；
 * 能力声明的采纳/驳回由应用层 GovernanceReviewWorkflow 编排（见 GovernanceReviewWorkflowTest）。
 */
@ExtendWith(MockitoExtension.class)
class AiGovernanceApplyServiceTest {

    @Mock
    private AiHarnessCheckLogMapper harnessLogMapper;

    @InjectMocks
    private AiGovernanceApplyServiceImpl service;

    @Test
    void acceptPersonnelReview_marksLogApplied() {
        AiHarnessCheckLog log = reviewLog(31L);
        when(harnessLogMapper.selectById(31L)).thenReturn(log);

        boolean accepted = service.acceptReview(31L, "verified");

        assertThat(accepted).isTrue();
        assertThat(log.getReviewStatus()).isEqualTo("ACCEPTED");
        assertThat(log.getBusinessApplyStatus()).isEqualTo("APPLIED");
        assertThat(log.getBusinessTargetType()).isEqualTo("EMP_ABILITY");
        verify(harnessLogMapper).updateById(log);
    }

    @Test
    void acceptPreservesTheEmployeeTargetRecordedByHarness() {
        AiHarnessCheckLog log = reviewLog(34L);
        log.setClaimType("PERSON_ABILITY");
        log.setBusinessTargetType("EMP_ABILITY");
        log.setBusinessTargetId(7L);
        when(harnessLogMapper.selectById(34L)).thenReturn(log);

        boolean accepted = service.acceptReview(34L, "verified");

        assertThat(accepted).isTrue();
        assertThat(log.getBusinessTargetType()).isEqualTo("EMP_ABILITY");
        assertThat(log.getBusinessTargetId()).isEqualTo(7L);
    }

    @Test
    void rejectPersonnelReview_marksLogSkipped() {
        AiHarnessCheckLog log = reviewLog(32L);
        when(harnessLogMapper.selectById(32L)).thenReturn(log);

        boolean rejected = service.rejectReview(32L, "insufficient evidence");

        assertThat(rejected).isTrue();
        assertThat(log.getReviewStatus()).isEqualTo("REJECTED");
        assertThat(log.getBusinessApplyStatus()).isEqualTo("SKIPPED");
        verify(harnessLogMapper).updateById(log);
    }

    @Test
    void acceptRejectsAlreadyProcessedLog() {
        AiHarnessCheckLog log = reviewLog(33L);
        log.setReviewStatus("ACCEPTED");
        when(harnessLogMapper.selectById(33L)).thenReturn(log);

        boolean accepted = service.acceptReview(33L, "again");

        assertThat(accepted).isFalse();
    }

    private AiHarnessCheckLog reviewLog(Long id) {
        AiHarnessCheckLog log = new AiHarnessCheckLog();
        log.setId(id);
        log.setClaimType("EMP_ABILITY");
        log.setReviewStatus("PENDING");
        return log;
    }
}
