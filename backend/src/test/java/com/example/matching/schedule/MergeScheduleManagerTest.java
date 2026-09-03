package com.example.matching.schedule;

import com.example.matching.entity.system.AbilityTagMergeTask;
import com.example.matching.mapper.system.AbilityTagMergeTaskMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MergeScheduleManagerTest {

    @Test
    void duePersistedTaskIsClaimedExecutedAndCompleted() {
        AbilityTagMergeTaskMapper mapper = mock(AbilityTagMergeTaskMapper.class);
        TagMergeScheduler scheduler = mock(TagMergeScheduler.class);
        AbilityTagMergeTask task = new AbilityTagMergeTask();
        task.setId(1L); task.setTaskCode("TAG_MERGE_1"); task.setThreshold(0.9d);
        when(mapper.selectDueTasks(any())).thenReturn(List.of(task));
        when(mapper.claimPendingTask(1L)).thenReturn(1);
        when(scheduler.executeMerge(0.9d)).thenReturn(Map.of("mergedCount", 2));

        MergeScheduleManager manager = new MergeScheduleManager(mapper, scheduler);
        manager.runDueTasks();

        verify(scheduler).executeMerge(0.9d);
        verify(mapper).markCompleted(eq(1L), contains("mergedCount"));
    }

    @Test
    void rejectsUnsafeThresholdBeforePersistingTask() {
        AbilityTagMergeTaskMapper mapper = mock(AbilityTagMergeTaskMapper.class);
        MergeScheduleManager manager = new MergeScheduleManager(mapper, mock(TagMergeScheduler.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.schedule(LocalDateTime.now().plusMinutes(1), 0.2d))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void scheduledTaskKeepsItsCreatorForResultNotification() {
        AbilityTagMergeTaskMapper mapper = mock(AbilityTagMergeTaskMapper.class);
        MergeScheduleManager manager = new MergeScheduleManager(mapper, mock(TagMergeScheduler.class));

        manager.schedule(LocalDateTime.now().plusMinutes(1), 0.9d, 42L);

        org.mockito.ArgumentCaptor<AbilityTagMergeTask> taskCaptor = org.mockito.ArgumentCaptor.forClass(AbilityTagMergeTask.class);
        verify(mapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getCreatedBy()).isEqualTo(42L);
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }
}
