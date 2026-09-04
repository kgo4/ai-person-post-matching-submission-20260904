package com.example.matching.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.system.AbilityTagGovernanceApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.schedule.PostAbilityTagGovernanceBackfillScheduler;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.system.AbilityTagRelationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityTagGovernanceControllerTest {

    private AbilityTagGovernanceApiFacade facade;
    private PostAbilityTagGovernanceBackfillScheduler backfillScheduler;
    private AbilityTagGovernanceController controller;

    @BeforeEach
    void setUp() {
        facade = mock(AbilityTagGovernanceApiFacade.class);
        backfillScheduler = mock(PostAbilityTagGovernanceBackfillScheduler.class);
        controller = new AbilityTagGovernanceController(facade, backfillScheduler);
        SecurityUtils.setCurrentUserId(7L);
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Test
    void pageCandidatesReturnsPage() {
        Page<Object> page = new Page<>(1, 10);
        when(facade.pageCandidates(1, 10, "PENDING", "MATCH_RESULT"))
                .thenAnswer(invocation -> new Page<>(1, 10));

        R<?> response = controller.pageCandidates(1, 10, "PENDING", "MATCH_RESULT");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void legacyApproveCandidateUsesDefaultRootDomain() {
        when(facade.approveCandidate(1L, "TECHNICAL", 0L)).thenReturn(5L);

        R<Long> response = controller.approveCandidate(1L, "TECHNICAL");

        assertThat(response.getData()).isEqualTo(5L);
        verify(facade).approveCandidate(1L, "TECHNICAL", 0L);
    }

    @Test
    void rejectCandidateUsesSecurityUserId() {
        R<Void> response = controller.rejectCandidate(1L, "理由");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).rejectCandidate(1L, 7L, "理由");
    }

    @Test
    void mergeCandidateUsesSecurityUserId() {
        R<Void> response = controller.mergeCandidate(1L, 2L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).mergeCandidate(1L, 2L, 7L);
    }

    @Test
    void computeStatsReturnsOk() {
        R<Void> response = controller.computeStats();

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).computeStats();
    }

    @Test
    void getUsageStatsReturnsStats() {
        when(facade.getUsageStats(10)).thenAnswer(invocation -> List.of(Map.of("tag", "Java")));

        R<?> response = controller.getUsageStats(10);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void pageRelationsReturnsPage() {
        IPage<AbilityTagRelationVO> page = new Page<>(1, 100);
        when(facade.pageRelations(1, 100, null, null, null, null)).thenReturn(page);

        R<IPage<AbilityTagRelationVO>> response = controller.pageRelations(1, 100, null, null, null, null);

        assertThat(response.getData()).isNotNull();
    }

    @Test
    void createRelationReturnsCreatedRelation() {
        Object relation = new Object();
        when(facade.createRelation(1L, 2L, "SYNONYM", 0.9, "备注")).thenReturn(relation);

        R<?> response = controller.createRelation(1L, 2L, "SYNONYM", 0.9, "备注");

        assertThat(response.getData()).isSameAs(relation);
    }

    @Test
    void approveRelationUsesSecurityUserId() {
        R<Void> response = controller.approveRelation(3L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).approveRelation(3L, 7L);
    }

    @Test
    void rejectRelationUsesSecurityUserId() {
        R<Void> response = controller.rejectRelation(3L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).rejectRelation(3L, 7L);
    }

    @Test
    void discoverRelationsReturnsCount() {
        when(facade.discoverRelations(0.7)).thenReturn(4);

        R<Integer> response = controller.discoverRelations(0.7);

        assertThat(response.getData()).isEqualTo(4);
    }

    @Test
    void executeMergeReturnsResult() {
        when(facade.executeMerge(0.9)).thenReturn(Map.of("merged", 2));

        R<Map<String, Object>> response = controller.executeMerge(0.9);

        assertThat(response.getData()).containsEntry("merged", 2);
    }

    @Test
    void scheduleMergeWithFutureTimeReturnsOk() {
        LocalDateTime future = LocalDateTime.now().plusMinutes(5);
        when(facade.scheduleMerge(0.9, future, 7L)).thenReturn(Map.of("taskId", "t1"));

        R<Map<String, Object>> response = controller.scheduleMerge(0.9, future);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("taskId", "t1");
    }

    @Test
    void scheduleMergeWithPastTimeFails() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(5);

        R<Map<String, Object>> response = controller.scheduleMerge(0.9, past);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("晚于");
        verify(facade, never()).scheduleMerge(anyDouble(), any(), any());
    }

    @Test
    void cancelMergeReturnsOkWhenCancelled() {
        when(facade.cancelMerge("t1")).thenReturn(true);

        R<Void> response = controller.cancelMerge("t1");

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void cancelMergeFailsWhenTaskMissing() {
        when(facade.cancelMerge("missing")).thenReturn(false);

        R<Void> response = controller.cancelMerge("missing");

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("不存在");
    }

    @Test
    void listPendingMergesReturnsTasks() {
        when(facade.listPendingMerges()).thenReturn(List.of(Map.of("taskId", "t1")));

        R<List<Map<String, Object>>> response = controller.listPendingMerges();

        assertThat(response.getData()).hasSize(1);
    }
}
