package com.example.matching.service.system.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SkillTaxonomyMap;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.SkillTaxonomyMapMapper;
import com.example.matching.service.system.TaxonomyClassifyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AbilityTagTaxonomyClassifier 单元测试。
 * <p>
 * 覆盖：规则表命中（RULE）、规则未命中向量命中（VECTOR）、均未命中（null）、
 * 空技能词（null）、规则命中但能力停用回退向量。
 */
class AbilityTagTaxonomyClassifierTest {

    private SkillTaxonomyMapMapper taxonomyMapMapper;
    private AbilityTagMapper abilityTagMapper;
    private VectorEmbeddingService vectorEmbeddingService;
    private AbilityTagTaxonomyClassifier classifier;

    @BeforeEach
    void setUp() {
        taxonomyMapMapper = mock(SkillTaxonomyMapMapper.class);
        abilityTagMapper = mock(AbilityTagMapper.class);
        vectorEmbeddingService = mock(VectorEmbeddingService.class);
        classifier = new AbilityTagTaxonomyClassifier(taxonomyMapMapper, abilityTagMapper, vectorEmbeddingService);
    }

    private AbilityTag competency(long id, String name, String domain) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setDomain(domain);
        tag.setTagCategory("TECHNICAL");
        tag.setTagLevel(2);
        tag.setParentId(1911L);
        tag.setStatus(1);
        return tag;
    }

    private AbilityTag domain() {
        AbilityTag domain = new AbilityTag();
        domain.setId(1911L);
        domain.setTagName("技术能力");
        domain.setTagLevel(1);
        domain.setTagCategory("TECHNICAL");
        domain.setStatus(1);
        return domain;
    }

    @Test
    @DisplayName("规则表命中返回 RULE 结果")
    void ruleMatchReturnsRuleResult() {
        AbilityTag backend = competency(2002L, "后端开发", "GENERAL");
        SkillTaxonomyMap rule = new SkillTaxonomyMap();
        rule.setSkillName("SpringBoot");
        rule.setAbilityTagId(2002L);
        rule.setConfidence(new BigDecimal("1.00"));
        rule.setStatus(1);

        when(taxonomyMapMapper.selectOne(any())).thenReturn(rule);
        when(abilityTagMapper.selectById(2002L)).thenReturn(backend);
        when(abilityTagMapper.selectById(1911L)).thenReturn(domain());

        TaxonomyClassifyResult result = classifier.classify("SpringBoot");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.abilityTag().getTagName()).isEqualTo("后端开发");
        assertThat(result.confidence()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("规则未命中、向量命中返回 VECTOR 结果")
    void vectorMatchReturnsVectorResult() {
        when(taxonomyMapMapper.selectOne(any())).thenReturn(null);

        AbilityTag frontend = competency(2001L, "前端开发", "GENERAL");
        frontend.setEmbeddingVector(List.of(0.1f, 0.2f, 0.3f));

        when(abilityTagMapper.selectList(any())).thenReturn(List.of(frontend));
        when(abilityTagMapper.selectById(1911L)).thenReturn(domain());
        when(vectorEmbeddingService.embed("Vue3")).thenReturn(List.of(0.1f, 0.2f, 0.3f));
        when(vectorEmbeddingService.cosineSimilarity(anyList(), anyList())).thenReturn(0.85f);

        TaxonomyClassifyResult result = classifier.classify("Vue3");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("VECTOR");
        assertThat(result.abilityTag().getTagName()).isEqualTo("前端开发");
    }

    @Test
    @DisplayName("规则与向量均未命中返回 null")
    void noMatchReturnsNull() {
        when(taxonomyMapMapper.selectOne(any())).thenReturn(null);
        when(vectorEmbeddingService.embed("Unknown")).thenReturn(List.of(0.1f));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of());

        assertThat(classifier.classify("Unknown")).isNull();
    }

    @Test
    @DisplayName("空技能词返回 null")
    void blankSkillNameReturnsNull() {
        assertThat(classifier.classify("  ")).isNull();
        assertThat(classifier.classify(null)).isNull();
    }

    @Test
    @DisplayName("规则命中但能力停用，回退向量（也失败则 null）")
    void ruleMatchButAbilityDisabledFallsBack() {
        SkillTaxonomyMap rule = new SkillTaxonomyMap();
        rule.setSkillName("Java");
        rule.setAbilityTagId(2002L);
        rule.setStatus(1);
        when(taxonomyMapMapper.selectOne(any())).thenReturn(rule);

        AbilityTag disabled = competency(2002L, "后端开发", "GENERAL");
        disabled.setStatus(0);
        when(abilityTagMapper.selectById(2002L)).thenReturn(disabled);

        when(vectorEmbeddingService.embed("Java")).thenReturn(List.of(0.1f));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of());

        assertThat(classifier.classify("Java")).isNull();
    }
}
