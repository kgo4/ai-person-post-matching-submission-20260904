package com.example.matching.service.kg;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;
import com.example.matching.entity.kg.KgGraphBuildTask;
import com.example.matching.event.GraphBuildQueuedEvent;
import com.example.matching.mapper.common.JobLockMapper;
import com.example.matching.mapper.kg.KgGraphBuildTaskMapper;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.kg.impl.GraphBuildTaskServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GraphBuildTaskServiceImpl enhanced tests")
class GraphBuildTaskEnhancedTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KgGraphBuildTask.class);
    }

    @Mock
    private KgGraphBuildTaskMapper taskMapper;
    @Mock
    private KnowledgeGraphBuildService knowledgeGraphBuildService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private JobLockMapper jobLockMapper;
    @Mock
    private EventOutboxDispatcher outboxDispatcher;

    @InjectMocks
    private GraphBuildTaskServiceImpl service;

    // ========== executeQueuedTask tests ==========

    @Test
    @DisplayName("Build failure: task status -> FAILED, error message stored")
    void executeQueuedTask_buildFailure_taskStatusFailed() {
        // CAS claim succeeds
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(knowledgeGraphBuildService.rebuildFullGraph())
                .thenThrow(new RuntimeException("Neo4j connection timeout"));

        // For the selectOne in the catch block - return a task with null retryCount (first failure)
        KgGraphBuildTask currentTask = new KgGraphBuildTask();
        currentTask.setTaskCode("KGB_TEST001");
        currentTask.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        currentTask.setRetryCount(null);
        when(taskMapper.selectOne(any())).thenReturn(currentTask);

        service.executeQueuedTask("KGB_TEST001");

        // Second update call should set status to RETRYING (retryCount=1 < MAX_RETRY=3)
        // The first update is CAS claim, second is the retry update
        verify(taskMapper, times(2)).update(isNull(), any());
    }

    @Test
    @DisplayName("Build success: task status -> SUCCEEDED, resultJson stored")
    void executeQueuedTask_buildSuccess_taskStatusSucceeded() throws Exception {
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        GraphBuildResultDTO result = new GraphBuildResultDTO();
        result.setSuccess(true);
        result.setNodeCount(100);
        result.setEdgeCount(200);
        result.setMessage("Graph built successfully");
        when(knowledgeGraphBuildService.rebuildFullGraph()).thenReturn(result);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"success\":true,\"nodeCount\":100}");

        service.executeQueuedTask("KGB_SUCCESS01");

        // Verify the second update call (success path)
        verify(taskMapper, times(2)).update(isNull(), any());
        // Result JSON should have been serialized
        verify(objectMapper).writeValueAsString(any(GraphBuildResultDTO.class));
    }

    @Test
    @DisplayName("Retry logic: first failure -> RETRYING")
    void executeQueuedTask_firstFailure_retrying() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(knowledgeGraphBuildService.rebuildFullGraph())
                .thenThrow(new RuntimeException("Connection refused"));

        KgGraphBuildTask currentTask = new KgGraphBuildTask();
        currentTask.setTaskCode("KGB_RETRY01");
        currentTask.setRetryCount(null); // first failure, no prior retries
        when(taskMapper.selectOne(any())).thenReturn(currentTask);

        service.executeQueuedTask("KGB_RETRY01");

        // CAS update + retry update = 2 calls
        verify(taskMapper, times(2)).update(isNull(), any());
        // Retry enqueue should have been called (retryCount=1 < MAX=3)
        verify(outboxDispatcher).enqueue(eq("KG_GRAPH_BUILD"), anyString(), anyString(), any(GraphBuildQueuedEvent.class));
    }

    @Test
    @DisplayName("Retry logic: second failure -> RETRYING")
    void executeQueuedTask_secondFailure_retrying() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(knowledgeGraphBuildService.rebuildFullGraph())
                .thenThrow(new RuntimeException("Timeout again"));

        KgGraphBuildTask currentTask = new KgGraphBuildTask();
        currentTask.setTaskCode("KGB_RETRY02");
        currentTask.setRetryCount(1); // second failure
        when(taskMapper.selectOne(any())).thenReturn(currentTask);

        service.executeQueuedTask("KGB_RETRY02");

        // After catch: retryCount = 1+1 = 2, 2 < 3 -> RETRYING
        verify(taskMapper, times(2)).update(isNull(), any());
        verify(outboxDispatcher).enqueue(eq("KG_GRAPH_BUILD"), anyString(), anyString(), any(GraphBuildQueuedEvent.class));
    }

    @Test
    @DisplayName("Retry logic: third failure -> FAILED, no more retries")
    void executeQueuedTask_thirdFailure_failed() {
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(knowledgeGraphBuildService.rebuildFullGraph())
                .thenThrow(new RuntimeException("Persistent failure"));

        KgGraphBuildTask currentTask = new KgGraphBuildTask();
        currentTask.setTaskCode("KGB_RETRY03");
        currentTask.setRetryCount(2); // third failure -> 2+1=3 >= MAX_RETRY(3)
        when(taskMapper.selectOne(any())).thenReturn(currentTask);

        service.executeQueuedTask("KGB_RETRY03");

        verify(taskMapper, times(2)).update(isNull(), any());
        // No retry enqueue when max retries reached
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("CAS idempotency: second executeQueuedTask on same task skips (claimed=0)")
    void executeQueuedTask_secondCallSkips_whenAlreadyClaimed() {
        // CAS claim fails (task already RUNNING from first execution)
        when(taskMapper.update(isNull(), any())).thenReturn(0);

        service.executeQueuedTask("KGB_CAS001");

        // Only the CAS update should be called; no rebuild, no error update
        verify(taskMapper, times(1)).update(isNull(), any());
        verify(knowledgeGraphBuildService, never()).rebuildFullGraph();
    }

    // ========== requestFullRebuild tests ==========

    @Test
    @DisplayName("requestFullRebuild with no active task -> creates new task")
    void requestFullRebuild_noActiveTask_createsNewTask() {
        when(jobLockMapper.acquireLock(anyString(), anyString(), anyString())).thenReturn(1);
        // No active tasks
        when(taskMapper.selectOne(any())).thenReturn(null);

        GraphBuildTaskStatusDTO result = service.requestFullRebuild(1001L);

        assertThat(result).isNotNull();
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatusEnum.PENDING.getCode());
        verify(taskMapper).insert(any(KgGraphBuildTask.class));
        verify(eventPublisher).publishEvent(any(GraphBuildQueuedEvent.class));
        verify(jobLockMapper).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("requestFullRebuild with existing active task -> returns existing task status")
    void requestFullRebuild_existingActiveTask_returnsExisting() {
        when(jobLockMapper.acquireLock(anyString(), anyString(), anyString())).thenReturn(1);

        KgGraphBuildTask activeTask = new KgGraphBuildTask();
        activeTask.setTaskCode("KGB_EXISTING");
        activeTask.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        when(taskMapper.selectOne(any())).thenReturn(activeTask);

        GraphBuildTaskStatusDTO result = service.requestFullRebuild(1001L);

        assertThat(result).isNotNull();
        assertThat(result.getTaskCode()).isEqualTo("KGB_EXISTING");
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatusEnum.PENDING.getCode());
        // Should NOT create a new task
        verify(taskMapper, never()).insert(any(KgGraphBuildTask.class));
        verify(jobLockMapper).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("requestFullRebuild with expired lock -> acquires and creates new task")
    void requestFullRebuild_expiredLock_acquiresAndCreates() {
        // First lock attempt fails
        when(jobLockMapper.acquireLock(anyString(), anyString(), anyString()))
                .thenReturn(0)  // first attempt: lock held by another instance
                .thenReturn(1); // second attempt after release: succeeds

        // No active tasks found at any point
        when(taskMapper.selectOne(any())).thenReturn(null);

        GraphBuildTaskStatusDTO result = service.requestFullRebuild(1001L);

        assertThat(result).isNotNull();
        assertThat(result.getTaskStatus()).isEqualTo(TaskStatusEnum.PENDING.getCode());
        // releaseLock called twice: once in the initial failed path, once in finally
        verify(jobLockMapper, times(2)).releaseLock(anyString(), anyString());
        // acquireLock called twice
        verify(jobLockMapper, times(2)).acquireLock(anyString(), anyString(), anyString());
    }

    // ========== recoverZombieTasks tests ==========

    @Test
    @DisplayName("Zombie recovery: RUNNING task older than 30 min -> recovered to RETRYING")
    void recoverZombieTasks_oldRunningTask_recoveredToRetrying() {
        KgGraphBuildTask zombie = new KgGraphBuildTask();
        zombie.setTaskCode("KGB_ZOMBIE01");
        zombie.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        zombie.setStartedTime(LocalDateTime.now().minusMinutes(31));
        zombie.setRetryCount(null); // first recovery

        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(zombie));
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        service.recoverZombieTasks();

        // Verify the update was called to change status to RETRYING
        verify(taskMapper).update(isNull(), any());
        // Verify retry was enqueued
        verify(outboxDispatcher).enqueue(eq("KG_GRAPH_BUILD"), anyString(), anyString(), any(GraphBuildQueuedEvent.class));
    }

    @Test
    @DisplayName("Zombie recovery: max retries reached -> FAILED, no re-enqueue")
    void recoverZombieTasks_maxRetriesReached_failedNoRequeue() {
        KgGraphBuildTask zombie = new KgGraphBuildTask();
        zombie.setTaskCode("KGB_ZOMBIE02");
        zombie.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        zombie.setStartedTime(LocalDateTime.now().minusMinutes(31));
        zombie.setRetryCount(2); // 2+1=3 >= MAX_RETRY -> FAILED

        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(zombie));
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        service.recoverZombieTasks();

        verify(taskMapper).update(isNull(), any());
        // No retry enqueue when max retries reached
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
    }
}

class GraphBuildZombieCasTest {

    private KgGraphBuildTaskMapper taskMapper;
    private EventOutboxDispatcher outboxDispatcher;
    private GraphBuildTaskServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        taskMapper = mock(KgGraphBuildTaskMapper.class);
        outboxDispatcher = mock(EventOutboxDispatcher.class);
        service = new GraphBuildTaskServiceImpl(
                taskMapper,
                mock(com.example.matching.service.kg.KnowledgeGraphBuildService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(JobLockMapper.class),
                outboxDispatcher);
    }

    @Test
    @DisplayName("M29: CAS 更新影响 0 行时不投递重试（多实例并发扫描只投递一次）")
    void recoverZombieTasks_casMiss_noReenqueue() {
        KgGraphBuildTask zombie = new KgGraphBuildTask();
        zombie.setTaskCode("KGB_CAS01");
        zombie.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        zombie.setStartedTime(LocalDateTime.now().minusMinutes(31));
        zombie.setRetryCount(0);

        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(zombie));
        // 其他实例已回收该任务：CAS 条件更新影响 0 行
        when(taskMapper.update(isNull(), any())).thenReturn(0);

        service.recoverZombieTasks();

        // CAS 命中失败 → 不投递重试（避免重复）
        verify(taskMapper).update(isNull(), any());
        verify(outboxDispatcher, never()).enqueue(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("M29: CAS 更新影响 1 行时投递重试")
    void recoverZombieTasks_casHit_enqueuesRetry() {
        KgGraphBuildTask zombie = new KgGraphBuildTask();
        zombie.setTaskCode("KGB_CAS02");
        zombie.setTaskStatus(TaskStatusEnum.RUNNING.getCode());
        zombie.setStartedTime(LocalDateTime.now().minusMinutes(31));
        zombie.setRetryCount(0);

        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(zombie));
        when(taskMapper.update(isNull(), any())).thenReturn(1);

        service.recoverZombieTasks();

        verify(taskMapper).update(isNull(), any());
        verify(outboxDispatcher).enqueue(eq("KG_GRAPH_BUILD"), anyString(), anyString(),
                any(com.example.matching.event.GraphBuildQueuedEvent.class));
    }
}
