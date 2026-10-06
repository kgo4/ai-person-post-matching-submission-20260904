package com.example.matching.event.listener;

import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.event.VectorSyncCompletedEvent;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.service.matching.MatchingTaskService;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RematchValidationVectorSyncListenerTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                MatchingRematchValidation.class);
    }

    @Test
    void submitsRematchOnlyAfterEmployeeVectorSyncCompletes() {
        MatchingRematchValidationMapper mapper = mock(MatchingRematchValidationMapper.class);
        MatchingTaskService taskService = mock(MatchingTaskService.class);
        MatchingRematchValidation validation = new MatchingRematchValidation();
        validation.setId(10L);
        validation.setEmpId(9L);
        validation.setPostId(100L);
        validation.setValidationStatus("WAIT_VECTOR_SYNC");
        when(mapper.selectList(any())).thenReturn(List.of(validation));
        when(mapper.update(any(), any())).thenReturn(1);
        when(taskService.submitTask(any())).thenReturn("task-1");

        RematchValidationVectorSyncListener listener = new RematchValidationVectorSyncListener(mapper, taskService);
        listener.onVectorSyncCompleted(new VectorSyncCompletedEvent("EMPLOYEE", 9L));

        assertThat(validation.getValidationStatus()).isEqualTo("PENDING");
        assertThat(validation.getTaskId()).isEqualTo("task-1");
        verify(taskService).submitTask(any());
        verify(mapper).updateById(validation);
    }

    @Test
    void ignoresPostVectorSyncEvents() {
        MatchingRematchValidationMapper mapper = mock(MatchingRematchValidationMapper.class);
        MatchingTaskService taskService = mock(MatchingTaskService.class);

        new RematchValidationVectorSyncListener(mapper, taskService)
                .onVectorSyncCompleted(new VectorSyncCompletedEvent("POST", 100L));

        org.mockito.Mockito.verifyNoInteractions(mapper, taskService);
    }
}
