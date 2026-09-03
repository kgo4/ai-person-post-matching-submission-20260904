package com.example.matching.listener;

import com.example.matching.common.enums.MatchingTaskStatus;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MatchingTaskListener heartbeat (N8)")
class MatchingTaskListenerHeartbeatTest {

    private MatchingRecordService matchingRecordService;
    private MatchingTaskService matchingTaskService;
    private MatchingTaskListener listener;

    @BeforeEach
    void setUp() {
        matchingRecordService = mock(MatchingRecordService.class);
        matchingTaskService = mock(MatchingTaskService.class);
        // 心跳间隔 10ms，远小于执行时长，确保测试中至少触发一次
        listener = new MatchingTaskListener(matchingRecordService, matchingTaskService,
                new ObjectMapper(), mock(ApplicationEventPublisher.class), 10L);
    }

    @Test
    @DisplayName("长任务执行期间心跳定期刷新 updatedTime，防止被僵尸扫描误杀")
    void heartbeatKeepsLongRunningTaskAlive() throws Exception {
        MatchingTask task = new MatchingTask();
        task.setTaskId("task-1");
        task.setStatus(MatchingTaskStatus.PENDING.getCode());
        MatchingExecuteDTO dto = new MatchingExecuteDTO();
        dto.setMode("BATCH");
        task.setMatchingConfig(new ObjectMapper().writeValueAsString(dto));
        when(matchingTaskService.getTaskStatus("task-1")).thenReturn(task);
        when(matchingTaskService.claimTask("task-1")).thenReturn(true);

        doAnswer(inv -> {
            Thread.sleep(300);
            return List.of();
        }).when(matchingRecordService).executeMatching(any(MatchingExecuteDTO.class));

        listener.handleMatchingTask("task-1");

        verify(matchingTaskService, atLeastOnce()).touchTask("task-1");
        verify(matchingTaskService).completeTask(org.mockito.ArgumentMatchers.eq("task-1"), anyString());
    }
}
