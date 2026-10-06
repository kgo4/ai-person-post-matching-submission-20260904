package com.example.matching.service.post.impl;

import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.dto.post.api.PostTrendCandidateDetailResponse;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.post.PostTrendTaskMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.service.post.support.TrendCandidatePayloadCodec;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 趋势候选读取单测（S4）。
 * <p>
 * 核心回归点：**列表保持轻量**。首屏卡片只给岗位名 / 相似度 / 徽标 / 前几个能力名，
 * 一旦这里把完整能力清单带出去，界面就会回到「巨量信息全部展示」的老问题。
 * 其次是来源标题必须按 RAG 文档ID 回查，sourceRef 末段的 chunk 只是定位、不是文档标识。
 */
@DisplayName("趋势候选读取（S4）")
class PostTrendQueryServiceImplTest {

    private final PostTrendCandidateMapper candidateMapper = mock(PostTrendCandidateMapper.class);
    private final PostTrendTaskMapper taskMapper = mock(PostTrendTaskMapper.class);
    private final RagKnowledgeDocumentMapper ragKnowledgeDocumentMapper = mock(RagKnowledgeDocumentMapper.class);
    private final AbilityTagCandidateService abilityTagCandidateService = mock(AbilityTagCandidateService.class);

    private final TrendCandidatePayloadCodec codec = new TrendCandidatePayloadCodec(new ObjectMapper());

    private final PostTrendQueryServiceImpl service = new PostTrendQueryServiceImpl(
            candidateMapper, taskMapper, ragKnowledgeDocumentMapper, abilityTagCandidateService, codec);

    @Test
    @DisplayName("卡片摘要：预览能力名截断、未归位与变更数量单独计数")
    void summaryKeepsCardLight() {
        PostTrendCandidateResponse summary = service.toSummary(candidate(payload()));

        assertThat(summary.getAbilityCount()).isEqualTo(6);
        assertThat(summary.getPreviewAbilities()).hasSize(4);
        assertThat(summary.getUnresolvedAbilityCount()).isEqualTo(2);
        // 6 项里只有「数据标注」是 UNCHANGED，其余 5 项都算需要人工过目的变更
        assertThat(summary.getChangeCount()).isEqualTo(5);
        assertThat(summary.getPostName()).isEqualTo("人工智能训练师");
    }

    @Test
    @DisplayName("来源标题按 sourceRef 第三段（RAG 文档ID）回查并去重保序")
    void detailResolvesSourceTitlesByDocumentId() {
        PostTrendCandidate candidate = candidate(payload());
        candidate.setSourceRefs("[\"source:INDUSTRY_WHITEPAPER:11:0\","
                + "\"source:POLICY_DOCUMENT:12:3\",\"source:INDUSTRY_WHITEPAPER:11:7\"]");
        when(candidateMapper.selectById(1L)).thenReturn(candidate);
        when(ragKnowledgeDocumentMapper.selectBatchIds(any())).thenReturn(List.of(
                document(11L, "某省人工智能人才发展规划"),
                document(12L, "职业分类大典（2025）")));

        PostTrendCandidateDetailResponse detail = service.getCandidateDetail(1L);

        assertThat(detail.getSummary().getId()).isEqualTo(1L);
        assertThat(detail.getSourceRefs()).hasSize(3);
        // 同一文档的两段引用只回查一次，避免标题重复
        assertThat(detail.getSourceTitles()).containsExactly("某省人工智能人才发展规划", "职业分类大典（2025）");
        assertThat(detail.getPayload().getAbilities()).hasSize(6);
    }

    @Test
    @DisplayName("来源材料查不到时给出占位标题，不因为标题缺失让详情页整体失败")
    void detailFallsBackToPlaceholderTitle() {
        PostTrendCandidate candidate = candidate(payload());
        candidate.setSourceRefs("[\"source:POLICY_DOCUMENT:99:0\"]");
        when(candidateMapper.selectById(1L)).thenReturn(candidate);
        when(ragKnowledgeDocumentMapper.selectBatchIds(any())).thenReturn(List.of());

        PostTrendCandidateDetailResponse detail = service.getCandidateDetail(1L);

        assertThat(detail.getSourceTitles()).containsExactly("材料 #99");
    }

    @Test
    @DisplayName("新能力候选走「来源标识 + 任务」过滤，不带任务地全表取会把别的链路候选混进来")
    void newTagCandidatesGoThroughScopedQuery() {
        AbilityTagCandidate scoped = new AbilityTagCandidate();
        scoped.setId(500L);
        scoped.setCandidateName("大模型对齐训练");
        when(abilityTagCandidateService.list(any(Wrapper.class))).thenReturn(List.of(scoped));

        List<com.example.matching.dto.post.api.TrendNewTagCandidateResponse> result =
                service.listNewTagCandidates(41L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(500L);
        assertThat(result.get(0).getCandidateName()).isEqualTo("大模型对齐训练");
        verify(abilityTagCandidateService).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("详情里的待归位能力候选ID按能力名与候选池对齐")
    void detailCollectsPendingTagCandidateIds() {
        PostTrendCandidate candidate = candidate(payload());
        when(candidateMapper.selectById(1L)).thenReturn(candidate);
        AbilityTagCandidate pending = new AbilityTagCandidate();
        pending.setId(88L);
        pending.setCandidateName("大模型对齐训练");
        when(abilityTagCandidateService.list(any(Wrapper.class))).thenReturn(List.of(pending));

        PostTrendCandidateDetailResponse detail = service.getCandidateDetail(1L);

        assertThat(detail.getNewTagCandidateIds()).contains(88L);
    }

    @Test
    @DisplayName("能力全部归位时不查候选池，省掉一次无意义查询")
    void detailSkipsTagCandidateQueryWhenEverythingResolved() {
        TrendCandidatePayload allResolved = new TrendCandidatePayload();
        allResolved.setAbilities(new ArrayList<>(List.of(item("数据标注", true, "UNCHANGED"))));
        PostTrendCandidate candidate = candidate(allResolved);
        when(candidateMapper.selectById(1L)).thenReturn(candidate);

        PostTrendCandidateDetailResponse detail = service.getCandidateDetail(1L);

        assertThat(detail.getNewTagCandidateIds()).isEmpty();
        verify(abilityTagCandidateService, org.mockito.Mockito.never()).list(any(Wrapper.class));
    }

    // ---------------------------------------------------------------- 工作台待办汇总

    @Test
    @DisplayName("待办汇总只数 PENDING：已通过/已驳回不该继续占待办位，否则待办永远清不掉")
    void pendingSummaryCountsOnlyPending() {
        when(candidateMapper.selectCount(any(Wrapper.class))).thenReturn(3L, 5L);
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        var summary = service.pendingSummary();

        assertThat(summary.getPendingNewPostCount()).isEqualTo(3);
        assertThat(summary.getPendingChangeCount()).isEqualTo(5);
        assertThat(summary.getPendingCandidateCount()).isEqualTo(8);
        assertThat(summary.getAwaitingTaskCount()).isEqualTo(2);
        verify(candidateMapper, org.mockito.Mockito.times(2)).selectCount(any(Wrapper.class));
    }

    @Test
    @DisplayName("统计失败不让工作台整页报错：查不到就是 0，待办区自然不出现")
    void pendingSummaryDegradesToZeroOnFailure() {
        when(candidateMapper.selectCount(any(Wrapper.class))).thenThrow(new RuntimeException("boom"));
        when(taskMapper.selectCount(any(Wrapper.class))).thenThrow(new RuntimeException("boom"));

        var summary = service.pendingSummary();

        assertThat(summary.getPendingCandidateCount()).isZero();
        assertThat(summary.getAwaitingTaskCount()).isZero();
    }

    // ---------------------------------------------------------------- 夹具

    private PostTrendCandidate candidate(TrendCandidatePayload payload) {
        PostTrendCandidate candidate = new PostTrendCandidate();
        candidate.setId(1L);
        candidate.setTaskId(41L);
        candidate.setCandidateType(PostTrendCandidate.TYPE_NEW_POST);
        candidate.setPostName("人工智能训练师");
        candidate.setConfirmStatus(PostTrendCandidate.CONFIRM_PENDING);
        candidate.setHarnessDecision(PostTrendCandidate.HARNESS_REVIEW);
        candidate.setCandidatePayload(codec.write(payload));
        return candidate;
    }

    /** 6 项能力：4 项已归位 + 2 项未归位；其中 5 项为实质变更（仅「数据标注」未变）。 */
    private TrendCandidatePayload payload() {
        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.setAbilities(new ArrayList<>(List.of(
                item("数据标注", true, "UNCHANGED"),
                item("内容安全", true, "UPGRADE_LEVEL"),
                item("模型评测", true, "ADD"),
                item("提示工程", true, "UPDATE_WEIGHT"),
                item("大模型对齐训练", false, "ADD"),
                item("多模态语料治理", false, "ADD"))));
        return payload;
    }

    private TrendCandidatePayload.TrendAbilityItem item(String name, boolean resolved, String changeType) {
        TrendCandidatePayload.TrendAbilityItem item = new TrendCandidatePayload.TrendAbilityItem();
        item.setAbilityName(name);
        item.setResolved(resolved);
        item.setChangeType(changeType);
        return item;
    }

    private RagKnowledgeDocument document(Long id, String title) {
        RagKnowledgeDocument document = new RagKnowledgeDocument();
        document.setId(id);
        document.setTitle(title);
        return document;
    }
}
