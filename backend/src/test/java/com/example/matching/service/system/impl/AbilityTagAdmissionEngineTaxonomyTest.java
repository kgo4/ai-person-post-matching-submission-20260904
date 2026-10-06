package com.example.matching.service.system.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagNormalizer;
import com.example.matching.service.system.AbilityTagVectorOperations;
import com.example.matching.service.system.TaxonomyClassifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AbilityTagAdmissionEngine 标签入库挂层级集成测试。
 * <p>
 * 验证 applyTaxonomy 在 createFormalTag 中的行为：
 * 归类成功 → 挂 parent_id + tag_level=2 + 继承父能力 domain；
 * 归类失败 → 保持顶层能力层（parent_id=0, tag_level=1），domain 保留人工指定值。
 */
class AbilityTagAdmissionEngineTaxonomyTest {

    private AbilityTagTaxonomyClassifier taxonomyClassifier;
    private VectorEmbeddingService vectorEmbeddingService;
    private AbilityTagAdmissionEngine engine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taxonomyClassifier = mock(AbilityTagTaxonomyClassifier.class);
        vectorEmbeddingService = mock(VectorEmbeddingService.class);

        ObjectProvider<KnowledgeDocumentService> knowledgeProvider = mock(ObjectProvider.class);
        when(knowledgeProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<AbilityTagAdmissionPipeline> admissionPipelineProvider = mock(ObjectProvider.class);

        engine = spy(new AbilityTagAdmissionEngine(
                mock(AbilityTagAliasMapper.class),
                mock(AbilityTagRelationMapper.class),
                mock(AbilityTagCandidateMapper.class),
                mock(AbilityTagVectorOperations.class),
                vectorEmbeddingService,
                mock(AbilityTagCandidateService.class),
                mock(AbilityTagNormalizer.class),
                taxonomyClassifier,
                knowledgeProvider,
                mock(ApplicationEventPublisher.class),
                admissionPipelineProvider
        ));

        // 绕过真实持久化
        doReturn(true).when(engine).save(any(AbilityTag.class));
        doReturn(true).when(engine).updateById(any(AbilityTag.class));
        when(vectorEmbeddingService.embed(anyString())).thenReturn(java.util.List.of(0.1f, 0.2f));
    }

    private AbilityTag competency(long id, String name, String domain) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setDomain(domain);
        tag.setTagCategory("TECHNICAL");
        tag.setStatus(1);
        tag.setTagLevel(2);
        tag.setParentId(1001L);
        return tag;
    }

    @Test
    @DisplayName("归类为技能时挂到同一 L1 域：parent_id=L1域, tag_level=2, 继承分类与领域")
    void createFormalTagAttachesToCompetencyWhenClassified() {
        AbilityTag backend = competency(2002L, "后端开发", "GENERAL");
        when(taxonomyClassifier.classify("SpringBoot"))
                .thenReturn(TaxonomyClassifyResult.of(backend, "RULE", new BigDecimal("1.00")));

        engine.createFormalTag("SpringBoot", "TECHNICAL", "CUSTOM_DOMAIN", "desc", "MANUAL");

        ArgumentCaptor<AbilityTag> captor = ArgumentCaptor.forClass(AbilityTag.class);
        verify(engine).save(captor.capture());
        AbilityTag saved = captor.getValue();
        assertThat(saved.getParentId()).isEqualTo(1001L);
        assertThat(saved.getTagLevel()).isEqualTo(2);
        assertThat(saved.getTagCategory()).isEqualTo("TECHNICAL");
        assertThat(saved.getDomain()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("未能归属到有效 L1 域时拒绝创建正式标签")
    void createFormalTagRejectsWhenNotClassified() {
        when(taxonomyClassifier.classify("新兴能力")).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> engine.createFormalTag("新兴能力", "TECHNICAL", "AI", "desc", "MANUAL"))
                .isInstanceOf(IllegalStateException.class);

        verify(engine, org.mockito.Mockito.never()).save(any(AbilityTag.class));
    }

    @Test
    void createFormalTagRejectsInvalidClassifierResult() {
        AbilityTag invalid = competency(2002L, "后端开发", "GENERAL");
        invalid.setTagLevel(1);
        when(taxonomyClassifier.classify("SpringBoot"))
                .thenReturn(TaxonomyClassifyResult.of(invalid, "RULE", new BigDecimal("1.00")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> engine.createFormalTag("SpringBoot", "TECHNICAL", "GENERAL", "desc", "MANUAL"))
                .isInstanceOf(IllegalStateException.class);

        verify(engine, org.mockito.Mockito.never()).save(any(AbilityTag.class));
    }
    @Test
    void createAiTagDoesNotPersistWhenNoL2ClassificationExists() {
        when(taxonomyClassifier.classify("unclassified")).thenReturn(null);

        AbilityTag created = engine.createAiTag("unclassified", "TECHNICAL", "AI_JD");

        assertThat(created).isNull();
        verify(engine, org.mockito.Mockito.never()).save(any(AbilityTag.class));
    }

    @Test
    void createAiTagReusesL2WhenSkillTaxonomyRuleMatches() {
        AbilityTag backend = competency(2002L, "backend", "GENERAL");
        backend.setTagLevel(2);
        backend.setParentId(1911L);
        when(taxonomyClassifier.classify("Java")).thenReturn(
                TaxonomyClassifyResult.of(backend, "RULE", new BigDecimal("1.00")));

        AbilityTag resolved = engine.createAiTag("Java", "TECHNICAL", "AI_JD");

        assertThat(resolved).isSameAs(backend);
        verify(engine, org.mockito.Mockito.never()).save(any(AbilityTag.class));
    }
}
