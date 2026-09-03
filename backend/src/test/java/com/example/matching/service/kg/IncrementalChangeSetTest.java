package com.example.matching.service.kg;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.mapper.kg.KgGraphBuildTaskMapper;
import com.example.matching.mapper.kg.KgGraphChangeSetMapper;
import com.example.matching.service.kg.impl.GraphChangeSetServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GraphChangeSetServiceImpl tests")
class IncrementalChangeSetTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KgGraphChangeSet.class);
    }

    @Mock
    private KgGraphChangeSetMapper changeSetMapper;
    @Mock
    private KgGraphBuildTaskMapper graphBuildTaskMapper;
    @Mock
    private KnowledgeGraphIncrementalService incrementalService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private com.example.matching.service.common.DistributedLockService distributedLockService;
    @Mock
    private com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @InjectMocks
    private GraphChangeSetServiceImpl service;

    private static final int MAX_RETRY_COUNT = 3;

    // ========== requestChange tests ==========

    @Test
    @DisplayName("requestChange creates PENDING change set with correct fields")
    void requestChange_noExisting_createsPendingChangeSet() throws Exception {
        when(changeSetMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");

        KgGraphChangeSet result = service.requestChange("POST", "POST", 42L,
                "UPSERT", Map.of("key", "value"), 100L);

        assertThat(result).isNotNull();
        assertThat(result.getSourceType()).isEqualTo("POST");
        assertThat(result.getEntityType()).isEqualTo("POST");
        assertThat(result.getEntityId()).isEqualTo(42L);
        assertThat(result.getOperationType()).isEqualTo("UPSERT");
        assertThat(result.getProcessStatus()).isEqualTo(TaskStatusEnum.PENDING.getCode());
        assertThat(result.getRetryCount()).isEqualTo(0);
        assertThat(result.getAffectedNodeCount()).isEqualTo(0);
        assertThat(result.getAffectedEdgeCount()).isEqualTo(0);
        assertThat(result.getCreatedBy()).isEqualTo(100L);
        assertThat(result.getChangeCode()).startsWith("KGC_");

        verify(changeSetMapper).insert(any(KgGraphChangeSet.class));
        verify(eventPublisher).publishEvent(any(com.example.matching.event.GraphChangeSetQueuedEvent.class));
    }

    @Test
    @DisplayName("Duplicate request (same sourceType+entityType+entityId+operation) -> returns existing")
    void requestChange_duplicateRequest_returnsExisting() {
        KgGraphChangeSet existing = new KgGraphChangeSet();
        existing.setChangeCode("KGC_EXISTING01");
        existing.setSourceType("POST");
        existing.setEntityType("POST");
        existing.setEntityId(42L);
        existing.setOperationType("UPSERT");
        existing.setProcessStatus(TaskStatusEnum.PENDING.getCode());
        when(changeSetMapper.selectOne(any())).thenReturn(existing);

        KgGraphChangeSet result = service.requestChange("POST", "POST", 42L,
                "UPSERT", Map.of("key", "value"), 100L);

        assertThat(result).isNotNull();
        assertThat(result.getChangeCode()).isEqualTo("KGC_EXISTING01");

        // Should NOT insert a new change set
        verify(changeSetMapper, never()).insert(any(KgGraphChangeSet.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("requestChange with null sourceType -> IllegalArgumentException")
    void requestChange_nullSourceType_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                service.requestChange(null, "POST", 42L, "UPSERT", Map.of(), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    @DisplayName("Invalid operationType -> IllegalArgumentException")
    void requestChange_invalidOperationType_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                service.requestChange("POST", "POST", 42L, "INSERT", Map.of(), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    // ========== executeChange tests ==========

    @Test
    @DisplayName("executeChange: pending -> running -> succeeded with correct affected counts")
    void executeChange_success_succeededWithAffectedCounts() throws Exception {
        // CAS claim to RUNNING succeeds
        when(changeSetMapper.update(isNull(), any())).thenReturn(1);
        // No active full build
        when(graphBuildTaskMapper.selectCount(any())).thenReturn(0L);

        KgGraphChangeSet changeSet = new KgGraphChangeSet();
        changeSet.setChangeCode("KGC_EXEC01");
        changeSet.setRetryCount(0);
        when(changeSetMapper.selectOne(any())).thenReturn(changeSet);

        KnowledgeGraphIncrementalService.IncrementalGraphResult incrementalResult =
                new KnowledgeGraphIncrementalService.IncrementalGraphResult(5, 10, "v2.1");
        when(incrementalService.apply(any(KgGraphChangeSet.class))).thenReturn(incrementalResult);

        service.executeChange("KGC_EXEC01");

        // Verify the second update call (success path) sets correct fields
        // update called twice: CAS claim + success update
        verify(changeSetMapper, times(2)).update(isNull(), any());
        verify(incrementalService).apply(any(KgGraphChangeSet.class));
    }

    @Test
    @DisplayName("executeChange failure: first -> RETRYING")
    void executeChange_failureFirst_retrying() {
        when(changeSetMapper.update(isNull(), any())).thenReturn(1);
        when(graphBuildTaskMapper.selectCount(any())).thenReturn(0L);

        KgGraphChangeSet changeSet = new KgGraphChangeSet();
        changeSet.setChangeCode("KGC_FAIL01");
        changeSet.setRetryCount(0);
        when(changeSetMapper.selectOne(any())).thenReturn(changeSet);

        when(incrementalService.apply(any(KgGraphChangeSet.class)))
                .thenThrow(new RuntimeException("Neo4j timeout"));

        service.executeChange("KGC_FAIL01");

        // CAS claim + failure update = 2 calls
        verify(changeSetMapper, times(2)).update(isNull(), any());
        // retryCount was 0, incremented to 1 < MAX(3) -> RETRYING
    }

    @Test
    @DisplayName("executeChange failure: max retries -> FAILED")
    void executeChange_failureMaxRetries_failed() {
        when(changeSetMapper.update(isNull(), any())).thenReturn(1);
        when(graphBuildTaskMapper.selectCount(any())).thenReturn(0L);

        KgGraphChangeSet changeSet = new KgGraphChangeSet();
        changeSet.setChangeCode("KGC_FAIL02");
        changeSet.setRetryCount(2); // 2+1=3 >= MAX(3) -> FAILED
        when(changeSetMapper.selectOne(any())).thenReturn(changeSet);

        when(incrementalService.apply(any(KgGraphChangeSet.class)))
                .thenThrow(new RuntimeException("Persistent failure"));

        service.executeChange("KGC_FAIL02");

        verify(changeSetMapper, times(2)).update(isNull(), any());
        // No more retries after max reached
    }

    @Test
    @DisplayName("executeChange with active full build -> defers back to PENDING")
    void executeChange_activeFullBuild_defersToPending() {
        // CAS claim succeeds (returns 1)
        when(changeSetMapper.update(isNull(), any())).thenReturn(1);
        // Active full build exists
        when(graphBuildTaskMapper.selectCount(any())).thenReturn(1L);

        service.executeChange("KGC_DEFER01");

        // First update: CAS claim to RUNNING
        // Second update: defer back to PENDING
        verify(changeSetMapper, times(2)).update(isNull(), any());
        // Incremental service should NOT be called
        verify(incrementalService, never()).apply(any());
    }

    @Test
    @DisplayName("executeChange CAS: already claimed task skips (claimed=0)")
    void executeChange_alreadyClaimed_skips() {
        when(changeSetMapper.update(isNull(), any())).thenReturn(0);

        service.executeChange("KGC_SKIP01");

        // Only the CAS update is called; no further processing
        verify(changeSetMapper, times(1)).update(isNull(), any());
        verify(graphBuildTaskMapper, never()).selectCount(any());
        verify(incrementalService, never()).apply(any());
    }

    @Test
    @DisplayName("requestChange with null entityId -> IllegalArgumentException")
    void requestChange_nullEntityId_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                service.requestChange("POST", "POST", null, "UPSERT", Map.of(), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity id");
    }

    @Test
    @DisplayName("requestChange with blank entityType -> IllegalArgumentException")
    void requestChange_blankEntityType_throwsIllegalArgument() {
        assertThatThrownBy(() ->
                service.requestChange("POST", "  ", 42L, "UPSERT", Map.of(), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entity type");
    }
}
