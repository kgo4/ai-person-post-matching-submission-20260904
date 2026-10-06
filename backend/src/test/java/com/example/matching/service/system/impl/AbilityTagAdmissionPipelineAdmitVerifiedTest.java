package com.example.matching.service.system.impl;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagNormalizer;
import com.example.matching.service.system.AbilityTagVectorOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AbilityTagAdmissionPipeline.admitVerifiedNewTag 单测：
 * 已验证决策分流（PASS>=80 建正式标签 / PASS 低分建候选 / REVIEW 建候选 / BLOCK/RETRY/证据/来源拒绝），
 * 且不再触发第二次自动可信度校验。
 */
@ExtendWith(MockitoExtension.class)
class AbilityTagAdmissionPipelineAdmitVerifiedTest {

    @Mock private AbilityTagAdmissionEngine engine;
    @Mock private AbilityTagVectorOperations vectorOperations;
    @Mock private AbilityTagNormalizer abilityTagNormalizer;
    @Mock private AbilityTagCandidateService abilityTagCandidateService;
    @Mock private AbilityTagCandidateMapper tagCandidateMapper;
    @Mock private AbilityTagAliasMapper tagAliasMapper;

    private AbilityTagAdmissionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new AbilityTagAdmissionPipeline(engine, vectorOperations, abilityTagNormalizer,
                abilityTagCandidateService, tagCandidateMapper, tagAliasMapper);
        lenient().when(abilityTagNormalizer.normalize(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(abilityTagNormalizer.isLowQualityName(any())).thenReturn(false);
        lenient().when(abilityTagNormalizer.isSentenceLike(any())).thenReturn(false);
        lenient().when(abilityTagNormalizer.getQualityScore(any())).thenReturn(60);
        lenient().when(engine.findByName(any())).thenReturn(null);
        lenient().when(engine.findByAlias(any())).thenReturn(null);
    }

    @Test
    void passWithHighScore_createsFormalTagWithoutSecondHarness() {
        AbilityTag created = new AbilityTag();
        created.setId(777L);
        created.setTagName("向量数据库");
        when(engine.createAiTag(any(), any(), any())).thenReturn(created);

        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("PASS", new BigDecimal("90")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResolvedTagId()).isEqualTo(777L);
        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.FORMAL_TAG_CREATED);
        verify(engine).createAiTag("向量数据库", "TECHNICAL", "MARKET_JD");
    }

    @Test
    void passWithLowScore_createsCandidateNotFormalTag() {
        when(abilityTagCandidateService.addCandidate(any())).thenReturn(1L);

        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("PASS", new BigDecimal("70")));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED);
        assertThat(result.getResolvedTagId()).isNull();
        verify(engine, never()).createAiTag(any(), any(), any());
    }

    @Test
    void review_createsCandidate() {
        when(abilityTagCandidateService.addCandidate(any())).thenReturn(1L);

        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("REVIEW", new BigDecimal("60")));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED);
        verify(engine, never()).createAiTag(any(), any(), any());
    }

    @Test
    void block_rejected() {
        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("BLOCK", null));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(engine, never()).createAiTag(any(), any(), any());
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    @Test
    void retry_rejected() {
        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("RETRY", null));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(engine, never()).createAiTag(any(), any(), any());
        verify(abilityTagCandidateService, never()).addCandidate(any());
    }

    @Test
    void missingEvidence_rejected() {
        TagAdmissionResult result = pipeline.admitVerifiedNewTag(
                contextWithoutEvidence("向量数据库"), decision("PASS", new BigDecimal("90")));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(engine, never()).createAiTag(any(), any(), any());
    }

    @Test
    void untrustedSource_rejected() {
        TagAdmissionContext ctx = context("向量数据库");
        ctx.setSourceType("UNKNOWN_SOURCE");

        TagAdmissionResult result = pipeline.admitVerifiedNewTag(ctx, decision("PASS", new BigDecimal("90")));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(engine, never()).createAiTag(any(), any(), any());
    }

    @Test
    void exactMatchExistingTag_reusedWithoutCreating() {
        AbilityTag existing = new AbilityTag();
        existing.setId(10L);
        existing.setTagName("向量数据库");
        when(engine.findByName("向量数据库")).thenReturn(existing);

        TagAdmissionResult result = pipeline.admitVerifiedNewTag(context("向量数据库"), decision("PASS", new BigDecimal("90")));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.EXISTING_TAG_REUSED);
        assertThat(result.getResolvedTagId()).isEqualTo(10L);
        verify(engine, never()).createAiTag(any(), any(), any());
    }

    // ==================== helpers ====================

    private TagAdmissionContext context(String tagName) {
        return TagAdmissionContext.builder()
                .tagName(tagName)
                .tagCategory("TECHNICAL")
                .sourceType("MARKET_JD")
                .sourceRefId(1L)
                .evidenceText("负责向量数据库开发")
                .contextText("负责向量数据库开发")
                .build();
    }

    private TagAdmissionContext contextWithoutEvidence(String tagName) {
        return TagAdmissionContext.builder()
                .tagName(tagName)
                .tagCategory("TECHNICAL")
                .sourceType("MARKET_JD")
                .sourceRefId(1L)
                .build();
    }

    private AiHarnessDecisionDTO decision(String decision, BigDecimal score) {
        AiHarnessDecisionDTO d = new AiHarnessDecisionDTO();
        d.setDecision(decision);
        d.setSupportScore(score);
        return d;
    }
}
