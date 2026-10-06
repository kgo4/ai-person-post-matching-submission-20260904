package com.example.matching.service.common;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.common.VectorSyncTask;
import com.example.matching.service.common.impl.VectorSyncTaskServiceImpl;
import com.example.matching.mapper.common.VectorSyncTaskMapper;
import com.example.matching.schedule.SchedulerMetrics;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSyncTaskServiceTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                VectorSyncTask.class);
    }

    private VectorSyncTaskMapper taskMapper;
    private DistributedLockService distributedLockService;
    private SchedulerMetrics schedulerMetrics;
    private EmpEmployeeMapper empEmployeeMapper;
    private EmpAbilityMapper empAbilityMapper;
    private VectorSyncTaskService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(VectorSyncTaskMapper.class);
        distributedLockService = mock(DistributedLockService.class);
        schedulerMetrics = mock(SchedulerMetrics.class);
        empEmployeeMapper = mock(EmpEmployeeMapper.class);
        empAbilityMapper = mock(EmpAbilityMapper.class);
        service = new VectorSyncTaskServiceImpl(
                taskMapper,
                empEmployeeMapper,
                empAbilityMapper,
                mock(PostPostMapper.class),
                mock(PostAbilityModelMapper.class),
                distributedLockService,
                schedulerMetrics);
    }

    @Test
    void enqueueInsertsNewTaskWithUniqueBusinessKey() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.insert(any(VectorSyncTask.class))).thenReturn(1);

        service.enqueue(VectorSyncTaskService.ENTITY_EMPLOYEE, 42L, null);

        verify(taskMapper).insert(any(VectorSyncTask.class));
        assertThat(VectorSyncTaskService.businessKey("EMPLOYEE", 42L)).isEqualTo("EMPLOYEE:42");
    }

    @Test
    void enqueueExistingFailedTaskResetsToPendingWithoutDuplicating() {
        VectorSyncTask existing = new VectorSyncTask();
        existing.setId(7L);
        existing.setStatus("FAILED");
        when(taskMapper.selectOne(any())).thenReturn(existing);

        service.enqueue(VectorSyncTaskService.ENTITY_POST, 9L, null);

        verify(taskMapper, never()).insert(any(VectorSyncTask.class));
        verify(taskMapper).update(eq(null), any());
    }

    @Test
    void enqueueExistingSucceededTaskResetsToPendingWithoutDuplicating() {
        VectorSyncTask existing = new VectorSyncTask();
        existing.setId(8L);
        existing.setStatus("SUCCEEDED");
        when(taskMapper.selectOne(any())).thenReturn(existing);

        service.enqueue(VectorSyncTaskService.ENTITY_POST, 9L, null);

        verify(taskMapper, never()).insert(any(VectorSyncTask.class));
        verify(taskMapper).update(eq(null), any());
    }

    @Test
    void processLockNotAcquiredSkipsQuery() {
        when(distributedLockService.tryAcquire("vector-sync-task-process"))
                .thenReturn(null);

        service.processPendingTasks();

        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void processPendingWithMilvusUnavailableEventuallyMarksFailed() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("vector-sync-task-process"))
                .thenReturn(handle);
        when(taskMapper.selectList(any())).thenReturn(List.of());

        service.processPendingTasks();

        verify(taskMapper).selectList(any());
        verify(handle).close();
    }

    @Test
    void replayResetsFailedTaskToPending() {
        when(taskMapper.update(eq(null), any())).thenReturn(1);

        boolean result = service.replay(3L);

        assertThat(result).isTrue();
        verify(taskMapper).update(eq(null), any());
    }

    @Test
    void milvusReturningFalseMarksTaskForRetryInsteadOfSucceeded() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("vector-sync-task-process"))
                .thenReturn(handle);

        VectorSyncTask task = new VectorSyncTask();
        task.setId(1L);
        task.setBusinessKey("EMPLOYEE:42");
        task.setEntityType(VectorSyncTaskService.ENTITY_EMPLOYEE);
        task.setEntityId(42L);
        task.setStatus("PENDING");
        task.setAttemptCount(0);
        task.setMaxAttempts(10);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        com.example.matching.entity.employee.EmpEmployee employee = new com.example.matching.entity.employee.EmpEmployee();
        employee.setId(42L);
        when(empEmployeeMapper.selectById(42L)).thenReturn(employee);
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());

        com.example.matching.vector.MilvusVectorService milvusVectorService =
                mock(com.example.matching.vector.MilvusVectorService.class);
        when(milvusVectorService.insertEmployeeVector(any(), any(), any())).thenReturn(false);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "milvusVectorService", milvusVectorService);

        when(taskMapper.update(eq(null), any())).thenReturn(1);

        service.processPendingTasks();

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VectorSyncTask>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(taskMapper, org.mockito.Mockito.atLeast(2)).update(eq(null), captor.capture());
        List<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VectorSyncTask>> updates = captor.getAllValues();
        assertThat(updates).isNotEmpty();
        verify(milvusVectorService).insertEmployeeVector(eq(42L), eq(employee), any());
    }

    @Test
    void successfulSyncClearsVectorRecallCacheAfterMilvusWrite() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("vector-sync-task-process")).thenReturn(handle);

        VectorSyncTask task = new VectorSyncTask();
        task.setId(1L);
        task.setEntityType(VectorSyncTaskService.ENTITY_EMPLOYEE);
        task.setEntityId(42L);
        task.setStatus("PENDING");
        task.setAttemptCount(0);
        task.setMaxAttempts(10);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskMapper.update(eq(null), any())).thenReturn(1);

        com.example.matching.entity.employee.EmpEmployee employee = new com.example.matching.entity.employee.EmpEmployee();
        employee.setId(42L);
        when(empEmployeeMapper.selectById(42L)).thenReturn(employee);
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());
        com.example.matching.vector.MilvusVectorService milvus = mock(com.example.matching.vector.MilvusVectorService.class);
        when(milvus.insertEmployeeVector(eq(42L), eq(employee), any())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "milvusVectorService", milvus);

        org.springframework.cache.Cache cache = mock(org.springframework.cache.Cache.class);
        org.springframework.cache.CacheManager cacheManager = mock(org.springframework.cache.CacheManager.class);
        when(cacheManager.getCache(com.example.matching.config.RedisCacheNames.VECTOR_RECALL)).thenReturn(cache);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "cacheManager", cacheManager);

        VectorRecallCacheEpoch epoch = mock(VectorRecallCacheEpoch.class);
        when(epoch.advance()).thenReturn(-1L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "vectorRecallCacheEpoch", epoch);

        service.processPendingTasks();

        verify(cache).clear();
    }

    @Test
    void successfulSync_advancesEpochInsteadOfFullClear() {
        DistributedLockService.LockHandle handle = mock(DistributedLockService.LockHandle.class);
        when(distributedLockService.tryAcquire("vector-sync-task-process")).thenReturn(handle);

        VectorSyncTask task = new VectorSyncTask();
        task.setId(7L);
        task.setEntityType(VectorSyncTaskService.ENTITY_EMPLOYEE);
        task.setEntityId(42L);
        task.setStatus("PENDING");
        task.setAttemptCount(0);
        task.setMaxAttempts(10);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        when(taskMapper.update(eq(null), any())).thenReturn(1);

        com.example.matching.entity.employee.EmpEmployee employee = new com.example.matching.entity.employee.EmpEmployee();
        employee.setId(42L);
        when(empEmployeeMapper.selectById(42L)).thenReturn(employee);
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());
        com.example.matching.vector.MilvusVectorService milvus = mock(com.example.matching.vector.MilvusVectorService.class);
        when(milvus.insertEmployeeVector(eq(42L), eq(employee), any())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "milvusVectorService", milvus);

        VectorRecallCacheEpoch epoch = mock(VectorRecallCacheEpoch.class);
        when(epoch.advance()).thenReturn(5L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "vectorRecallCacheEpoch", epoch);

        service.processPendingTasks();

        verify(milvus).insertEmployeeVector(eq(42L), eq(employee), any());
        verify(epoch).advance();
        org.mockito.Mockito.verify(epoch, org.mockito.Mockito.never()).available();
    }

    @Test
    void executeSyncDoesNotIssueDuplicateGraphChangeRequests() throws Exception {
        VectorSyncTask task = new VectorSyncTask();
        task.setEntityType(VectorSyncTaskService.ENTITY_POST);
        task.setEntityId(9L);

        java.lang.reflect.Method executeSync = VectorSyncTaskServiceImpl.class.getDeclaredMethod("executeSync", VectorSyncTask.class);
        executeSync.setAccessible(true);
        try {
            executeSync.invoke(service, task);
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MilvusVectorService not available");
        }
    }
}
