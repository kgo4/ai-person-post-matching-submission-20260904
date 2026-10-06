package com.example.matching.service.post.support;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.config.PostTrendProperties;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.service.evolution.support.EvolutionAbilityTagCatalog;
import com.example.matching.service.post.support.TrendAbilityResolver.TagResolution;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.support.AbilityTagMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力名归位单测。
 * <p>
 * 核心回归点：**未归位的能力只能进候选池，绝不能直通正式标签库**。
 * `ability_tag` 是全系统能力统计与评估的锚点，一次解析若能往里灌标签，
 * 几十个未经治理的新标签会立刻污染能力分布与匹配口径。
 */
@DisplayName("趋势能力标签归位（S3）")
class TrendAbilityResolverTest {

    private final AbilityTagMatchService abilityTagMatchService = mock(AbilityTagMatchService.class);
    private final EvolutionAbilityTagCatalog abilityTagCatalog = mock(EvolutionAbilityTagCatalog.class);
    private final VectorEmbeddingService vectorEmbeddingService = mock(VectorEmbeddingService.class);
    private final AbilityTagCandidateService abilityTagCandidateService = mock(AbilityTagCandidateService.class);

    private PostTrendProperties properties;
    private TrendAbilityResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new PostTrendProperties();
        resolver = new TrendAbilityResolver(abilityTagMatchService, abilityTagCatalog,
                vectorEmbeddingService, abilityTagCandidateService, properties);
    }

    @Test
    @DisplayName("确定性命中时直接用既有标签，不产生候选、不做向量调用")
    void deterministicMatchReusesExistingTag() {
        AbilityTag tag = tag(11L, "内容安全", 12L);
        when(abilityTagMatchService.matchByName("内容安全审查")).thenReturn(tag);

        TagResolution resolution = resolver.resolve("内容安全审查", 100L, "证据");

        assertThat(resolution.resolved()).isTrue();
        // 归位到 canonicalTagId，避免同一能力散落在多个标签上
        assertThat(resolution.tagId()).isEqualTo(12L);
        assertThat(resolution.matchedTagName()).isEqualTo("内容安全");
        verify(abilityTagCandidateService, never()).addCandidate(any());
        verify(vectorEmbeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("向量高相似时归位到既有标签，不产生候选")
    void highVectorSimilarityResolvesToExistingTag() {
        AbilityTag tag = tag(21L, "大模型应用开发", null);
        stubVector(List.of(1F, 0F), tag, 0.95F);

        TagResolution resolution = resolver.resolve("LLM应用开发", 100L, "证据");

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.tagId()).isEqualTo(21L);
        assertThat(resolution.similarity().doubleValue()).isEqualTo(0.95D);
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    @Test
    @DisplayName("向量相似但未达标：只进候选池，且带上最相似标签供人工判断")
    void belowThresholdGoesToCandidatePool() {
        AbilityTag tag = tag(31L, "数据标注", null);
        stubVector(List.of(1F, 0F), tag, 0.80F);
        when(abilityTagCandidateService.addCandidate(any())).thenReturn(555L);

        TagResolution resolution = resolver.resolve("多模态数据标注", 100L, "材料原文片段");

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.tagId()).isNull();
        assertThat(resolution.similarTagId()).isEqualTo(31L);
        assertThat(resolution.similarTagName()).isEqualTo("数据标注");
        assertThat(resolution.tagCandidateId()).isEqualTo(555L);

        ArgumentCaptor<AbilityTagCandidate> captor = ArgumentCaptor.forClass(AbilityTagCandidate.class);
        verify(abilityTagCandidateService).addCandidate(captor.capture());
        AbilityTagCandidate candidate = captor.getValue();
        assertThat(candidate.getCandidateName()).isEqualTo("多模态数据标注");
        assertThat(candidate.getSourceType()).isEqualTo(TrendAbilityResolver.SOURCE_TYPE_POST_TREND);
        assertThat(candidate.getSourceRefId()).isEqualTo(100L);
        assertThat(candidate.getEvidenceText()).isEqualTo("材料原文片段");
    }

    @Test
    @DisplayName("相似度过低时不带「最相似标签」，避免给出误导性建议")
    void weakSimilarityIsNotReported() {
        AbilityTag tag = tag(41L, "Java", null);
        stubVector(List.of(1F, 0F), tag, 0.35F);
        when(abilityTagCandidateService.addCandidate(any())).thenReturn(556L);

        TagResolution resolution = resolver.resolve("量子计算运维", 100L, "证据");

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.similarTagId()).isNull();
        assertThat(resolution.similarTagName()).isNull();
        // 未归位时仍要有可展示文本
        assertThat(resolution.matchedTagName()).isEqualTo("量子计算运维");
    }

    @Test
    @DisplayName("候选池写入失败不影响归位结果，能力仍以未归位形式返回")
    void candidateWriteFailureDoesNotBreakResolution() {
        when(abilityTagMatchService.matchByName(anyString())).thenReturn(null);
        when(vectorEmbeddingService.embed(anyString())).thenReturn(List.of());
        when(abilityTagCandidateService.addCandidate(any())).thenThrow(new RuntimeException("db down"));

        TagResolution resolution = resolver.resolve("新型能力", 100L, "证据");

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.matchedTagName()).isEqualTo("新型能力");
        assertThat(resolution.tagCandidateId()).isNull();
    }

    @Test
    @DisplayName("空白能力名直接返回未归位，不触碰任何依赖")
    void blankNameShortCircuits() {
        TagResolution resolution = resolver.resolve("   ", 100L, "证据");

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.matchedTagName()).isNull();
        verify(abilityTagMatchService, never()).matchByName(anyString());
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    private void stubVector(List<Float> queryVector, AbilityTag tag, float similarity) {
        when(abilityTagMatchService.matchByName(anyString())).thenReturn(null);
        when(vectorEmbeddingService.embed(anyString())).thenReturn(queryVector);
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(similarity);
        tag.setEmbeddingVector(List.of(0.5F, 0.5F));
        when(abilityTagCatalog.activeTags()).thenReturn(List.of(tag));
    }

    private AbilityTag tag(Long id, String name, Long canonicalTagId) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setCanonicalTagId(canonicalTagId);
        return tag;
    }
}
