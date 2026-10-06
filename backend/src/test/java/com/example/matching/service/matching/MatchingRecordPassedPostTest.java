package com.example.matching.service.matching;

import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.impl.MatchingRecordServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 员工「已通过岗位」聚合测试（人岗匹配闭环设计 P3）。
 *
 * <p>锁定的口径：只取「HR 审核通过 + 已推送」的记录（与员工侧可见闸门一致），
 * 且同一岗位的多条历史记录只保留最新一条。</p>
 */
@ExtendWith(MockitoExtension.class)
class MatchingRecordPassedPostTest {

    @Mock
    private MatchingRecordMapper matchingRecordMapper;

    @Mock
    private MatchingDataQueryService dataQuery;

    /** 本测试只关心「已通过岗位」聚合口径，不涉及结果推送通知 */
    @Mock
    private com.example.matching.service.notification.SysNotificationService notificationService;

    private MatchingRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MatchingRecordServiceImpl(
                mock(MatchingExecuteService.class),
                mock(MatchingEvidenceScoreCalculator.class),
                dataQuery,
                mock(MatchingAiAnalysisService.class),
                new ObjectMapper(),
                mock(MatchingApprovalFlowMapper.class),
                mock(MatchingFeedbackDatasetMapper.class),
                notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", matchingRecordMapper);
        // 名称补全走主数据；本测试只关心去重与口径，主数据统一返回空，让兜底名称生效
        lenient().when(dataQuery.findEmployeesForMatching(any())).thenReturn(Collections.emptyList());
        lenient().when(dataQuery.findPostsForMatching(any())).thenReturn(Collections.emptyList());
    }

    private static MatchingRecord record(Long id, Long postId, Integer matchStatus, LocalDateTime updatedTime) {
        MatchingRecord record = new MatchingRecord();
        record.setId(id);
        record.setEmpId(7L);
        record.setPostId(postId);
        record.setMatchStatus(matchStatus);
        record.setFinalMatchScore(new java.math.BigDecimal("88.5"));
        record.setUpdatedTime(updatedTime);
        return record;
    }

    @Test
    @DisplayName("empId 为空：直接返回空列表，不查库")
    void returnsEmptyForNullEmpId() {
        assertThat(service.listPassedPosts(null)).isEmpty();
        verifyNoInteractions(matchingRecordMapper);
    }

    @Test
    @DisplayName("同一岗位多条历史记录：只保留最新一条")
    void deduplicatesByPostKeepingLatest() {
        MatchingRecord otherPost = record(3L, 20L, 1, LocalDateTime.of(2026, 3, 1, 10, 0));
        MatchingRecord newer = record(2L, 10L, 2, LocalDateTime.of(2026, 2, 1, 10, 0));
        MatchingRecord older = record(1L, 10L, 1, LocalDateTime.of(2026, 1, 1, 10, 0));
        // 查询已按 updatedTime 倒序返回
        when(matchingRecordMapper.selectList(any())).thenReturn(List.of(otherPost, newer, older));

        List<MatchingRecord> result = service.listPassedPosts(7L);

        assertThat(result).extracting(MatchingRecord::getId).containsExactly(3L, 2L);
        // 兜底名称在无主数据时仍然可读，不会出现 null 岗位名
        assertThat(result).extracting(MatchingRecord::getPostName)
                .containsExactly("Post#20", "Post#10");
    }

    @Test
    @DisplayName("无已通过记录：返回空列表")
    void returnsEmptyWhenNoPassedRecord() {
        when(matchingRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThat(service.listPassedPosts(7L)).isEmpty();
    }
}
