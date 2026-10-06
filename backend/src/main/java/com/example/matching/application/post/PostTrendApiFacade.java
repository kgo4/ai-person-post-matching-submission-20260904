package com.example.matching.application.post;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.dto.post.api.TrendBatchConfirmRequest;
import com.example.matching.dto.post.api.TrendBatchConfirmResult;
import com.example.matching.dto.post.api.PostTrendLandResult;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.service.post.PostTrendConfirmService;
import com.example.matching.service.post.PostTrendQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 岗位趋势发现的应用层编排。
 * <p>
 * 批量落地刻意放在这一层而不是 Service 里：{@code land} 是带事务的，
 * 若在一个事务里循环落地，第 8 条候选失败会把前 7 条一并回滚，
 * 管理员看到的是「点了 8 个，一个都没成」而不是「成功了 7 个，第 8 个因为什么没成」。
 * 放在无事务的编排层逐个调用，才能让每条候选各自成败、各自给出原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTrendApiFacade {

    private final PostTrendQueryService queryService;
    private final PostTrendConfirmService confirmService;

    /**
     * 批量确认落地。
     * <p>
     * 只有「治理判定 PASS 且仍待确认」的候选会被落地，其余逐条跳过并给出中文原因，
     * 不做静默忽略——批量按钮不能成为绕过治理判定的后门。
     */
    public TrendBatchConfirmResult batchConfirm(TrendBatchConfirmRequest request, Long operatorId) {
        if (request == null || request.candidateIds() == null || request.candidateIds().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请先选择要确认的候选");
        }
        List<Long> candidateIds = request.candidateIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidateIds.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请先选择要确认的候选");
        }

        List<PostTrendLandResult> landed = new ArrayList<>();
        List<TrendBatchConfirmResult.Skipped> skipped = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            PostTrendCandidateResponse summary = loadSummary(candidateId);
            if (summary == null) {
                skipped.add(new TrendBatchConfirmResult.Skipped(candidateId, null, "候选不存在或已被删除"));
                continue;
            }
            String blocked = blockReason(summary);
            if (blocked != null) {
                skipped.add(new TrendBatchConfirmResult.Skipped(candidateId, summary.getPostName(), blocked));
                continue;
            }
            try {
                landed.add(confirmService.land(candidateId, request.reviewComment(), operatorId));
            } catch (BusinessException e) {
                // 业务异常带的是给管理员看的中文说明，直接透出
                skipped.add(new TrendBatchConfirmResult.Skipped(candidateId, summary.getPostName(), e.getMessage()));
            } catch (Exception e) {
                log.warn("批量确认落库失败: candidateId={}, err={}", candidateId, e.getMessage());
                skipped.add(new TrendBatchConfirmResult.Skipped(candidateId, summary.getPostName(),
                        "落地失败，请单独重试该候选"));
            }
        }
        log.info("趋势候选批量确认完成: 请求={}, 落地={}, 跳过={}", candidateIds.size(), landed.size(), skipped.size());
        return new TrendBatchConfirmResult(landed, skipped);
    }

    private PostTrendCandidateResponse loadSummary(Long candidateId) {
        try {
            return queryService.getCandidateDetail(candidateId).getSummary();
        } catch (BusinessException e) {
            return null;
        }
    }

    /** 返回不可批量落地的中文原因；可落地时返回 null。 */
    private String blockReason(PostTrendCandidateResponse summary) {
        if (!PostTrendCandidate.CONFIRM_PENDING.equals(summary.getConfirmStatus())) {
            return "候选已被处理，无需重复确认";
        }
        String decision = summary.getHarnessDecision();
        if (PostTrendCandidate.HARNESS_PASS.equals(decision)) {
            return null;
        }
        if (PostTrendCandidate.HARNESS_REVIEW.equals(decision)) {
            return "治理判定为待复核，需打开详情逐条确认后再落地";
        }
        if (PostTrendCandidate.HARNESS_BLOCK.equals(decision)) {
            return "治理判定为已拦截，证据不足或来源不可采信，不允许落地";
        }
        return StringUtils.hasText(decision)
                ? "治理判定异常（" + decision + "），需人工确认"
                : "缺少治理判定结果，需人工确认后再落地";
    }

    public PageResponse<PostTrendCandidateResponse> pageCandidates(long current, long size, Long taskId,
                                                                  String candidateType, String confirmStatus,
                                                                  String harnessDecision) {
        return queryService.pageCandidates(current, size, taskId, candidateType, confirmStatus, harnessDecision);
    }
}
