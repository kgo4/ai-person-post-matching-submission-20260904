package com.example.matching.service.system.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagNormalizer;
import com.example.matching.service.system.AbilityTagVectorOperations;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityTagAdmissionWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AbilityTagMapper.class, () -> mock(AbilityTagMapper.class))
            .withBean(AbilityTagAliasMapper.class, () -> mock(AbilityTagAliasMapper.class))
            .withBean(AbilityTagRelationMapper.class, () -> mock(AbilityTagRelationMapper.class))
            .withBean(AbilityTagCandidateMapper.class, () -> mock(AbilityTagCandidateMapper.class))
            .withBean(AbilityTagVectorOperations.class, () -> mock(AbilityTagVectorOperations.class))
            .withBean(VectorEmbeddingService.class, () -> mock(VectorEmbeddingService.class))
            .withBean(AbilityTagCandidateService.class, () -> mock(AbilityTagCandidateService.class))
            .withBean(AbilityTagNormalizer.class, () -> mock(AbilityTagNormalizer.class))
            .withBean(AbilityTagTaxonomyClassifier.class, () -> mock(AbilityTagTaxonomyClassifier.class))
            .withBean(AbilityTagAdmissionEngine.class)
            .withBean(AbilityTagAdmissionPipeline.class);

    @Test
    void createsAdmissionComponentsWithoutCircularDependency() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AbilityTagAdmissionEngine.class);
            assertThat(context).hasSingleBean(AbilityTagAdmissionPipeline.class);
        });
    }


    // ==================== Task5：开放词表准入行为 ====================

    private AbilityTagAdmissionPipeline pipeline(AbilityTagAdmissionEngine engine,
                                                 AbilityTagNormalizer normalizer,
                                                 AbilityTagVectorOperations vectorOps,
                                                 AbilityTagCandidateService candidateService) {
        return new AbilityTagAdmissionPipeline(
                engine, vectorOps, normalizer, candidateService,
                mock(AbilityTagCandidateMapper.class), mock(AbilityTagAliasMapper.class));
    }

    private TagAdmissionContext context(String tagName, String sourceType, String evidence,
                                        Float confidence) {
        return TagAdmissionContext.builder()
                .tagName(tagName)
                .tagCategory("TECHNICAL")
                .domain("GENERAL")
                .sourceType(sourceType)
                .sourceRefId(8L)
                .evidenceText(evidence)
                .confidenceScore(confidence)
                .build();
    }

    @Test
    void personnelSourceIsRejectedBeforeEnteringTagGovernanceCandidate() {
        AbilityTagAdmissionEngine engine = mock(AbilityTagAdmissionEngine.class);
        when(engine.findByName(any())).thenReturn(null);
        when(engine.findByAlias(any())).thenReturn(null);
        when(engine.findSimilarTags(any(), anyFloat())).thenReturn(List.of());
        AbilityTagNormalizer normalizer = mock(AbilityTagNormalizer.class);
        when(normalizer.normalize(any())).thenReturn("Rust高性能并发编程");
        when(normalizer.isLowQualityName(any())).thenReturn(false);
        when(normalizer.isSentenceLike(any())).thenReturn(false);
        when(normalizer.getQualityScore(any())).thenReturn(80);

        AbilityTagCandidateService candidateService = mock(AbilityTagCandidateService.class);
        AbilityTagAdmissionPipeline pipeline = pipeline(engine, normalizer,
                mock(AbilityTagVectorOperations.class), candidateService);

        TagAdmissionResult result = pipeline.admitNewTag(
                context("Rust高性能并发编程", "RESUME_PARSE", "使用 Rust 编写了高并发网关服务", 0.9f));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(candidateService, never()).addCandidate(any());
    }

    @Test
    void personnelSourceWithoutEvidenceIsRejected() {
        AbilityTagAdmissionEngine engine = mock(AbilityTagAdmissionEngine.class);
        when(engine.findByName(any())).thenReturn(null);
        when(engine.findByAlias(any())).thenReturn(null);
        when(engine.findSimilarTags(any(), anyFloat())).thenReturn(List.of());
        AbilityTagNormalizer normalizer = mock(AbilityTagNormalizer.class);
        when(normalizer.normalize(any())).thenReturn("模糊新能力");
        when(normalizer.isLowQualityName(any())).thenReturn(false);
        when(normalizer.isSentenceLike(any())).thenReturn(false);
        when(normalizer.getQualityScore(any())).thenReturn(80);
        AbilityTagCandidateService candidateService = mock(AbilityTagCandidateService.class);
        when(candidateService.addCandidate(any())).thenReturn(9001L);

        AbilityTagAdmissionPipeline pipeline = pipeline(engine, normalizer,
                mock(AbilityTagVectorOperations.class), candidateService);

        TagAdmissionResult result = pipeline.admitNewTag(
                context("模糊新能力", "RESUME_PARSE", null, 0.9f));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
        verify(candidateService, never()).addCandidate(any());
    }

    @Test
    void personnelSourceWithLowConfidenceIsRejected() {
        AbilityTagAdmissionEngine engine = mock(AbilityTagAdmissionEngine.class);
        when(engine.findByName(any())).thenReturn(null);
        when(engine.findByAlias(any())).thenReturn(null);
        when(engine.findSimilarTags(any(), anyFloat())).thenReturn(List.of());
        AbilityTagNormalizer normalizer = mock(AbilityTagNormalizer.class);
        when(normalizer.normalize(any())).thenReturn("低置信度新能力");
        when(normalizer.isLowQualityName(any())).thenReturn(false);
        when(normalizer.isSentenceLike(any())).thenReturn(false);
        when(normalizer.getQualityScore(any())).thenReturn(80);

        AbilityTagAdmissionPipeline pipeline = pipeline(engine, normalizer,
                mock(AbilityTagVectorOperations.class),
                mock(AbilityTagCandidateService.class));

        TagAdmissionResult result = pipeline.admitNewTag(
                context("低置信度新能力", "RESUME_PARSE", "有证据文本", 0.4f));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.REJECTED);
    }

    @Test
    void emergingPostSourceWithOriginalEvidenceEntersTagGovernanceCandidate() {
        AbilityTagAdmissionEngine engine = mock(AbilityTagAdmissionEngine.class);
        when(engine.findByName(any())).thenReturn(null);
        when(engine.findByAlias(any())).thenReturn(null);
        when(engine.findSimilarTags(any(), anyFloat())).thenReturn(List.of());
        AbilityTagNormalizer normalizer = mock(AbilityTagNormalizer.class);
        when(normalizer.normalize(any())).thenReturn("智能体工作流编排");
        when(normalizer.isLowQualityName(any())).thenReturn(false);
        when(normalizer.isSentenceLike(any())).thenReturn(false);
        when(normalizer.getQualityScore(any())).thenReturn(80);
        AbilityTagCandidateService candidateService = mock(AbilityTagCandidateService.class);
        when(candidateService.addCandidate(any())).thenReturn(9001L);

        AbilityTagAdmissionPipeline pipeline = pipeline(engine, normalizer,
                mock(AbilityTagVectorOperations.class), candidateService);

        TagAdmissionResult result = pipeline.admitNewTag(
                context("智能体工作流编排", "EMERGING_POST", "JD 要求具备智能体工作流编排能力", 0.9f));

        assertThat(result.getDecision()).isEqualTo(TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED);
        verify(candidateService).addCandidate(any());
    }
}
