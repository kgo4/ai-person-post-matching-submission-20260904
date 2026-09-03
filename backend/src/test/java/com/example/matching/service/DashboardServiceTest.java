package com.example.matching.service;

import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostPostMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void readsAllMatchingCountersFromOneAggregateQuery() {
        EmpEmployeeMapper employeeMapper = mock(EmpEmployeeMapper.class);
        PostPostMapper postMapper = mock(PostPostMapper.class);
        MatchingRecordMapper matchingMapper = mock(MatchingRecordMapper.class);
        when(employeeMapper.selectCount(any())).thenReturn(12L);
        when(postMapper.selectCount(any())).thenReturn(4L);
        when(matchingMapper.selectDashboardSummary()).thenReturn(Map.of(
                "totalCount", 9L,
                "score90", 2L, "score75", 3L, "score60", 1L, "scoreBelow60", 3L,
                "status0", 1L, "status1", 2L, "status2", 3L, "status3", 2L, "status4", 1L));

        DashboardStatsCacheService statsCacheService = new DashboardStatsCacheService(
                employeeMapper, postMapper, matchingMapper);
        Map<String, Object> stats = new DashboardService(statsCacheService).getDashboardStats();

        assertThat(stats).containsEntry("employeeCount", 12L).containsEntry("postCount", 4L)
                .containsEntry("recordCount", 9L);
        assertThat((Map<String, Long>) stats.get("scoreDistribution"))
                .containsEntry("strong", 2L).containsEntry("match", 3L)
                .containsEntry("observe", 1L).containsEntry("reject", 3L);
        verify(matchingMapper).selectDashboardSummary();
    }
}
