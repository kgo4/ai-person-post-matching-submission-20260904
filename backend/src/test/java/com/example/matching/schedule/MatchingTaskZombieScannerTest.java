package com.example.matching.schedule;

import com.example.matching.service.matching.MatchingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingTaskZombieScanner")
class MatchingTaskZombieScannerTest {

    @Mock private MatchingTaskService matchingTaskService;

    private MatchingTaskZombieScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new MatchingTaskZombieScanner(matchingTaskService);
    }

    @Test
    @DisplayName("执行僵尸恢复（30 分钟无状态变更阈值）")
    void recoversZombiesWhenScanRuns() {
        when(matchingTaskService.recoverZombieTasks(any(Duration.class))).thenReturn(2);

        scanner.scanZombieTasks();

        verify(matchingTaskService).recoverZombieTasks(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("恢复异常被捕获，调度不中断")
    void failureIsSwallowedAndSchedulerKeepsRunning() {
        when(matchingTaskService.recoverZombieTasks(any(Duration.class)))
                .thenThrow(new RuntimeException("DB down"));

        scanner.scanZombieTasks();
    }

    @Test
    @DisplayName("无僵尸时不重复告警")
    void noZombiesDoesNotFail() {
        when(matchingTaskService.recoverZombieTasks(any(Duration.class))).thenReturn(0);

        scanner.scanZombieTasks();

        verify(matchingTaskService).recoverZombieTasks(Duration.ofMinutes(30));
    }
}
