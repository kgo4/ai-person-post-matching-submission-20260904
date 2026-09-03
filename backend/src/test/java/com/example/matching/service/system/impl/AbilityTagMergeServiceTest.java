package com.example.matching.service.system.impl;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagVectorOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AbilityTagMergeServiceTest {

    private static final Long SOURCE_TAG_ID = 10L;
    private static final Long TARGET_TAG_ID = 20L;

    private AbilityTagMapper tagMapper;
    private EmpAbilityMapper empAbilityMapper;
    private AbilityTagServiceImpl service;

    @BeforeEach
    void setUp() {
        tagMapper = mock(AbilityTagMapper.class);
        AbilityTagAliasMapper aliasMapper = mock(AbilityTagAliasMapper.class);
        PostAbilityModelMapper postAbilityModelMapper = mock(PostAbilityModelMapper.class);
        empAbilityMapper = mock(EmpAbilityMapper.class);

        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(tagMapper.updateById(any(AbilityTag.class))).thenReturn(1);

        service = spy(new AbilityTagServiceImpl(
                aliasMapper,
                postAbilityModelMapper,
                empAbilityMapper,
                mock(AbilityTagVectorOperations.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(AbilityTagAdmissionEngine.class),
                mock(AbilityTagTaxonomyClassifier.class)));
        ReflectionTestUtils.setField(service, "baseMapper", tagMapper);
    }

    @Test
    void higherSourceWeightWinsAndCarriesItsEvaluationSource() {
        AbilityTag sourceTag = tag(SOURCE_TAG_ID, "source");
        AbilityTag targetTag = tag(TARGET_TAG_ID, "target");
        doReturn(sourceTag).when(service).getById(SOURCE_TAG_ID);
        doReturn(targetTag).when(service).getById(TARGET_TAG_ID);

        EmpAbility source = ability(1L, new BigDecimal("0.90"), "CERTIFICATION");
        EmpAbility target = ability(2L, new BigDecimal("0.60"), "MANUAL");
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.singletonList(source));
        when(empAbilityMapper.selectOne(any())).thenReturn(target);

        service.mergeTags(SOURCE_TAG_ID, TARGET_TAG_ID);

        assertThat(target.getSourceWeight()).isEqualByComparingTo("0.90");
        assertThat(target.getEvaluationSource()).isEqualTo("CERTIFICATION");
    }

    @Test
    void lowerOrEqualSourceWeightKeepsTargetEvaluationSource() {
        AbilityTag sourceTag = tag(SOURCE_TAG_ID, "source");
        AbilityTag targetTag = tag(TARGET_TAG_ID, "target");
        doReturn(sourceTag).when(service).getById(SOURCE_TAG_ID);
        doReturn(targetTag).when(service).getById(TARGET_TAG_ID);

        EmpAbility source = ability(1L, new BigDecimal("0.60"), "CERTIFICATION");
        EmpAbility target = ability(2L, new BigDecimal("0.90"), "MANUAL");
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.singletonList(source));
        when(empAbilityMapper.selectOne(any())).thenReturn(target);

        service.mergeTags(SOURCE_TAG_ID, TARGET_TAG_ID);

        assertThat(target.getSourceWeight()).isEqualByComparingTo("0.90");
        assertThat(target.getEvaluationSource()).isEqualTo("MANUAL");
    }

    @Test
    void nonNullSourceWeightFillsMissingTargetWeightAndSource() {
        AbilityTag sourceTag = tag(SOURCE_TAG_ID, "source");
        AbilityTag targetTag = tag(TARGET_TAG_ID, "target");
        doReturn(sourceTag).when(service).getById(SOURCE_TAG_ID);
        doReturn(targetTag).when(service).getById(TARGET_TAG_ID);

        EmpAbility source = ability(1L, new BigDecimal("0.70"), "CERTIFICATION");
        EmpAbility target = ability(2L, null, null);
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.singletonList(source));
        when(empAbilityMapper.selectOne(any())).thenReturn(target);

        service.mergeTags(SOURCE_TAG_ID, TARGET_TAG_ID);

        assertThat(target.getSourceWeight()).isEqualByComparingTo("0.70");
        assertThat(target.getEvaluationSource()).isEqualTo("CERTIFICATION");
    }

    private static AbilityTag tag(Long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setCanonicalTagId(id);
        return tag;
    }

    private static EmpAbility ability(Long id, BigDecimal sourceWeight, String evaluationSource) {
        EmpAbility ability = new EmpAbility();
        ability.setId(id);
        ability.setEmpId(100L);
        ability.setTagId(SOURCE_TAG_ID);
        ability.setMasteryLevel(2);
        ability.setAbilityLevel(2);
        ability.setSourceWeight(sourceWeight);
        ability.setEvaluationSource(evaluationSource);
        return ability;
    }
}
