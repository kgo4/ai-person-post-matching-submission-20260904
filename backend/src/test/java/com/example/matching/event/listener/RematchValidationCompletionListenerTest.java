package com.example.matching.event.listener;

import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.event.MatchingTaskCompletedEvent;
import com.example.matching.event.MatchingTaskFailedEvent;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.MatchingCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RematchValidationCompletionListenerTest {

    private MatchingRematchValidationMapper validationMapper;
    private MatchingRecordMapper recordMapper;
    private MatchingCacheInvalidator cacheInvalidator;
    private RematchValidationCompletionListener listener;

    @BeforeEach
    void setUp() {
        // 单测环境无 MyBatis 容器：为 lambdaUpdate 初始化 TableInfo 缓存
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                MatchingRecord.class);
        validationMapper = mock(MatchingRematchValidationMapper.class);
        recordMapper = mock(MatchingRecordMapper.class);
        cacheInvalidator = mock(MatchingCacheInvalidator.class);
        listener = new RematchValidationCompletionListener(validationMapper, recordMapper, cacheInvalidator);
    }

    @Test
    void marksPendingValidationFailedWhenMatchingTaskFails() {
        MatchingRematchValidation validation = new MatchingRematchValidation();
        validation.setValidationStatus("PENDING");
        when(validationMapper.selectList(any())).thenReturn(List.of(validation));

        listener.onMatchingTaskFailed(new MatchingTaskFailedEvent("task-1", "scoring failed"));

        assertThat(validation.getValidationStatus()).isEqualTo("FAILED");
        assertThat(validation.getFailReason()).isEqualTo("scoring failed");
        verify(validationMapper).updateById(validation);
    }

    @Test
    void backfillSuccessEvictsDetailListAndDashboardCaches() {
        MatchingRematchValidation validation = new MatchingRematchValidation();
        validation.setTaskId("task-1");
        validation.setValidationStatus("PENDING");
        validation.setOriginalMatchingRecordId(100L);
        validation.setNewMatchingRecordId(200L);
        when(validationMapper.selectList(any())).thenReturn(List.of(validation));

        MatchingRecord newRecord = new MatchingRecord();
        newRecord.setId(200L);
        newRecord.setAiMatchScore(new java.math.BigDecimal("85.00"));
        newRecord.setMatchStatus(1);
        when(recordMapper.selectOne(any())).thenReturn(newRecord);
        when(recordMapper.update(any(), any())).thenReturn(1);

        listener.onMatchingTaskCompleted(new MatchingTaskCompletedEvent("task-1", "BATCH1"));

        // 回写成功后清理详情/列表/仪表盘缓存
        org.junit.jupiter.api.Assertions.assertNotNull(validation.getOriginalMatchingRecordId());
        assertThat(validation.getValidationStatus()).isEqualTo("COMPLETED");
        verify(recordMapper).selectOne(any());
        verify(recordMapper).update(any(), any());
        verify(cacheInvalidator).evictAfterAiScore(100L);
    }

    @Test
    void noEvictionWhenOriginalRecordLockedOrDeleted() {
        MatchingRematchValidation validation = new MatchingRematchValidation();
        validation.setTaskId("task-1");
        validation.setValidationStatus("PENDING");
        validation.setOriginalMatchingRecordId(100L);
        validation.setNewMatchingRecordId(200L);
        when(validationMapper.selectList(any())).thenReturn(List.of(validation));

        MatchingRecord newRecord = new MatchingRecord();
        newRecord.setId(200L);
        newRecord.setAiMatchScore(new java.math.BigDecimal("85.00"));
        when(recordMapper.selectOne(any())).thenReturn(newRecord);
        when(recordMapper.update(any(), any())).thenReturn(0); // 已锁定/删除

        listener.onMatchingTaskCompleted(new MatchingTaskCompletedEvent("task-1", "BATCH1"));

        verify(cacheInvalidator, never()).evictAfterAiScore(any());
    }
}
