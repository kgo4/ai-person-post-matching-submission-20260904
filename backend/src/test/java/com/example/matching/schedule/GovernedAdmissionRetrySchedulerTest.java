package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.service.governance.GovernedAdmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GovernedAdmissionRetryScheduler")
class GovernedAdmissionRetrySchedulerTest {

    @Mock private GovernanceAdmissionMapper admissionMapper;
    @Mock private GovernedAdmissionService governedAdmissionService;

    private GovernedAdmissionRetryScheduler scheduler;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                GovernanceAdmissionRecord.class);
    }

    @BeforeEach
    void setUp() {
        scheduler = new GovernedAdmissionRetryScheduler(
                admissionMapper, governedAdmissionService);
    }

    @Test
    @DisplayName("查询条件：RETRYABLE + 已到期 + retry_count <= 上限（含恰好 10 次可转 RETRY_EXHAUSTED）")
    void queriesDueRetryableRecordsIncludingMaxRetryCount() {
        when(admissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        scheduler.retryDueAdmissions();

        ArgumentCaptor<Wrapper<GovernanceAdmissionRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(admissionMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("apply_status = ");
        assertThat(sql).contains("retry_count <= ");
        assertThat(sql).contains("next_retry_time <= ");
    }

    @Test
    @DisplayName("对每条到期记录调用重试服务")
    void retriesEachDueRecord() {
        GovernanceAdmissionRecord r1 = record(1L);
        GovernanceAdmissionRecord r2 = record(2L);
        when(admissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1, r2));
        when(governedAdmissionService.retryDueAdmission(1L)).thenReturn(null);
        when(governedAdmissionService.retryDueAdmission(2L)).thenReturn(null);

        scheduler.retryDueAdmissions();

        verify(governedAdmissionService).retryDueAdmission(1L);
        verify(governedAdmissionService).retryDueAdmission(2L);
    }

    @Test
    @DisplayName("查询异常时被捕获，不中断调度")
    void queryFailureIsSwallowedAndSchedulerKeepsRunning() {
        when(admissionMapper.selectList(any(Wrapper.class))).thenThrow(new RuntimeException("DB down"));

        scheduler.retryDueAdmissions();

        verify(governedAdmissionService, never()).retryDueAdmission(any());
    }

    private GovernanceAdmissionRecord record(Long id) {
        GovernanceAdmissionRecord record = new GovernanceAdmissionRecord();
        record.setId(id);
        record.setApplyStatus("RETRYABLE");
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now().minusMinutes(1));
        return record;
    }
}
