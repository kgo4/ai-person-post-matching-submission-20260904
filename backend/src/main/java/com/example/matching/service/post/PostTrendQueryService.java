package com.example.matching.service.post;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.post.api.PostTrendCandidateDetailResponse;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.dto.post.api.TrendNewTagCandidateResponse;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.vo.post.TrendPendingSummaryVO;

import java.util.List;

/**
 * 岗位趋势候选的读取侧。
 * <p>
 * 与确认侧分开是为了让「首屏轻、详情重」这个约束有地方落：列表只组装卡片需要的字段，
 * 能力清单 / 证据原文 / 来源标题只在打开抽屉时查。若合成一个服务，
 * 列表很容易顺手把 payload 整段带出来，界面上就会再次出现「巨量信息全展示」。
 */
public interface PostTrendQueryService {

    /** 候选分页；筛选条件为 null 表示不限制。 */
    PageResponse<PostTrendCandidateResponse> pageCandidates(long current, long size, Long taskId,
                                                            String candidateType, String confirmStatus,
                                                            String harnessDecision);

    /** 候选摘要（卡片字段），供列表与详情复用同一组装口径。 */
    PostTrendCandidateResponse toSummary(PostTrendCandidate candidate);

    /** 候选详情：摘要 + 完整载荷 + 证据 + 来源标题 + 待归位能力候选ID。 */
    PostTrendCandidateDetailResponse getCandidateDetail(Long candidateId);

    /**
     * 某次解析提出的新能力标签候选。
     *
     * @param taskId 趋势任务ID；为 null 时返回最近的待审核候选
     */
    List<TrendNewTagCandidateResponse> listNewTagCandidates(Long taskId);

    /**
     * 跨任务统计「还有多少候选待人工审核」，供工作台待办使用。
     * <p>
     * 工作台待办必须是真实待处理项：候选分页必须带 taskId，回答不了「全站还剩多少没审」，
     * 所以这里单独出一个只读汇总，避免工作台拉全部任务再逐个查候选。
     */
    TrendPendingSummaryVO pendingSummary();
}
