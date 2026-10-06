package com.example.matching.application.post;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.api.PostTrendCandidateDetailResponse;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.dto.post.api.PostTrendLandResult;
import com.example.matching.dto.post.api.TrendBatchConfirmRequest;
import com.example.matching.dto.post.api.TrendBatchConfirmResult;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.service.post.PostTrendConfirmService;
import com.example.matching.service.post.PostTrendQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量确认单测（S4）。
 * <p>
 * 核心回归点：**批量按钮不得成为绕过治理判定的后门**。
 * REVIEW / BLOCK 的候选必须逐条落到人工确认，且被跳过的候选要带中文原因返回，
 * 不能出现「点了 5 个只成功 2 个、界面什么也没说」。
 */
@DisplayName("趋势候选批量确认（S4）")
class PostTrendApiFacadeTest {

    private final PostTrendQueryService queryService = mock(PostTrendQueryService.class);
    private final PostTrendConfirmService confirmService = mock(PostTrendConfirmService.class);

    private final PostTrendApiFacade facade = new PostTrendApiFacade(queryService, confirmService);

    @Test
    @DisplayName("只落地 PASS 且待确认的候选，REVIEW / BLOCK 逐条跳过并说明原因")
    void batchConfirmSkipsNonPassCandidatesWithReason() {
        when(queryService.getCandidateDetail(1L)).thenReturn(detail(summary(1L, "AI 训练师",
                PostTrendCandidate.HARNESS_PASS, PostTrendCandidate.CONFIRM_PENDING)));
        when(queryService.getCandidateDetail(2L)).thenReturn(detail(summary(2L, "数据合规官",
                PostTrendCandidate.HARNESS_REVIEW, PostTrendCandidate.CONFIRM_PENDING)));
        when(queryService.getCandidateDetail(3L)).thenReturn(detail(summary(3L, "量子算法工程师",
                PostTrendCandidate.HARNESS_BLOCK, PostTrendCandidate.CONFIRM_PENDING)));
        when(queryService.getCandidateDetail(4L)).thenReturn(detail(summary(4L, "已处理岗位",
                PostTrendCandidate.HARNESS_PASS, PostTrendCandidate.CONFIRM_APPROVED)));
        when(confirmService.land(eq(1L), any(), any()))
                .thenReturn(new PostTrendLandResult(1L, PostTrendCandidate.TYPE_NEW_POST, 100L, "AI 训练师", 4, true));

        TrendBatchConfirmResult result = facade.batchConfirm(
                new TrendBatchConfirmRequest(List.of(1L, 2L, 3L, 4L), "批量确认"), 9L);

        assertThat(result.landed()).hasSize(1);
        assertThat(result.landed().get(0).postId()).isEqualTo(100L);
        assertThat(result.skipped()).hasSize(3);
        assertThat(result.skipped()).extracting(TrendBatchConfirmResult.Skipped::reason)
                .anySatisfy(reason -> assertThat(reason).contains("待复核"))
                .anySatisfy(reason -> assertThat(reason).contains("已拦截"))
                .anySatisfy(reason -> assertThat(reason).contains("已被处理"));
        verify(confirmService, never()).land(eq(2L), any(), any());
        verify(confirmService, never()).land(eq(3L), any(), any());
    }

    @Test
    @DisplayName("单条落地失败不影响本批其它候选：逐条独立事务，各自给出原因")
    void oneFailureDoesNotAbortTheBatch() {
        when(queryService.getCandidateDetail(1L)).thenReturn(detail(summary(1L, "会失败的岗位",
                PostTrendCandidate.HARNESS_PASS, PostTrendCandidate.CONFIRM_PENDING)));
        when(queryService.getCandidateDetail(2L)).thenReturn(detail(summary(2L, "能成功的岗位",
                PostTrendCandidate.HARNESS_PASS, PostTrendCandidate.CONFIRM_PENDING)));
        when(confirmService.land(eq(1L), any(), any()))
                .thenThrow(new BusinessException(ErrorCodeEnum.STATE_CONFLICT, "该候选已被其他操作处理"));
        when(confirmService.land(eq(2L), any(), any()))
                .thenReturn(new PostTrendLandResult(2L, PostTrendCandidate.TYPE_NEW_POST, 101L, "能成功的岗位", 3, true));

        TrendBatchConfirmResult result = facade.batchConfirm(
                new TrendBatchConfirmRequest(List.of(1L, 2L), null), 9L);

        assertThat(result.landed()).hasSize(1);
        assertThat(result.landed().get(0).postId()).isEqualTo(101L);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).contains("已被其他操作处理");
    }

    @Test
    @DisplayName("候选不存在时按跳过处理，不抛异常打断整批")
    void missingCandidateIsSkipped() {
        when(queryService.getCandidateDetail(1L))
                .thenThrow(new BusinessException(ErrorCodeEnum.NOT_FOUND, "趋势候选不存在: 1"));

        TrendBatchConfirmResult result = facade.batchConfirm(
                new TrendBatchConfirmRequest(List.of(1L), null), 9L);

        assertThat(result.landed()).isEmpty();
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).contains("不存在");
    }

    @Test
    @DisplayName("空选择直接报参数错误，不做无意义的全表遍历")
    void emptySelectionIsRejected() {
        assertThatThrownBy(() -> facade.batchConfirm(new TrendBatchConfirmRequest(List.of(), null), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先选择");

        assertThatThrownBy(() -> facade.batchConfirm(null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先选择");
    }

    @Test
    @DisplayName("重复ID只处理一次，避免同一候选被并发落地两次")
    void duplicateIdsAreDeduplicated() {
        when(queryService.getCandidateDetail(1L)).thenReturn(detail(summary(1L, "AI 训练师",
                PostTrendCandidate.HARNESS_PASS, PostTrendCandidate.CONFIRM_PENDING)));
        when(confirmService.land(eq(1L), any(), any()))
                .thenReturn(new PostTrendLandResult(1L, PostTrendCandidate.TYPE_NEW_POST, 100L, "AI 训练师", 4, true));

        TrendBatchConfirmResult result = facade.batchConfirm(
                new TrendBatchConfirmRequest(List.of(1L, 1L, 1L), null), 9L);

        assertThat(result.landed()).hasSize(1);
        verify(confirmService).land(eq(1L), any(), any());
    }

    private PostTrendCandidateDetailResponse detail(PostTrendCandidateResponse summary) {
        PostTrendCandidateDetailResponse detail = new PostTrendCandidateDetailResponse();
        detail.setSummary(summary);
        return detail;
    }

    private PostTrendCandidateResponse summary(Long id, String postName, String harnessDecision, String confirmStatus) {
        PostTrendCandidateResponse response = new PostTrendCandidateResponse();
        response.setId(id);
        response.setPostName(postName);
        response.setHarnessDecision(harnessDecision);
        response.setConfirmStatus(confirmStatus);
        return response;
    }
}
