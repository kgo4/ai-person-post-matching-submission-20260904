package com.example.matching.service.ability.impl;

import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityCrossValidationService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbilityQualityMonitorServiceImplTest {

    @Mock
    private EmpAbilityMapper empAbilityMapper;
    @Mock
    private EmpEmployeeMapper empEmployeeMapper;
    @Mock
    private AbilityTagMapper abilityTagMapper;
    @Mock
    private ContestEvidenceItemMapper evidenceItemMapper;
    @Mock
    private RagRetrievalService ragContextService;
    @Mock
    private AbilityCrossValidationService crossValidationService;

    @Test
    void scanEmployeeAbilities_batchesSharedTagsAndEvidenceInsteadOfQueryingPerAbility() {
        EmpAbility java = ability(101L, 10L);
        EmpAbility spring = ability(102L, 11L);
        EmpEmployee employee = new EmpEmployee();
        employee.setId(1L);
        employee.setRealName("Alice");
        ContestEvidenceItem javaEvidence = new ContestEvidenceItem();
        javaEvidence.setTargetRefId(101L);

        when(empAbilityMapper.selectList(any())).thenReturn(List.of(java, spring));
        when(empEmployeeMapper.selectById(1L)).thenReturn(employee);
        when(abilityTagMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(tag(10L, "Java"), tag(11L, "Spring")));
        when(evidenceItemMapper.selectList(any())).thenReturn(List.of(javaEvidence));

        assertThat(newService().detectIsolatedAbilities(1L))
                .extracting(issue -> issue.tagId())
                .containsExactly(11L);

        verify(empAbilityMapper).selectList(any());
        verify(empEmployeeMapper).selectById(1L);
        verify(abilityTagMapper).selectBatchIds(argThat(ids -> ids.containsAll(List.of(10L, 11L)) && ids.size() == 2));
        verify(evidenceItemMapper).selectList(any());
        verify(abilityTagMapper, never()).selectById(any());
        verify(evidenceItemMapper, never()).selectCount(any());
    }

    @Test
    void scanEmployeeAbilities_reusesOnePreloadedContextAcrossAllDetectors() {
        EmpAbility java = ability(101L, 10L);
        EmpAbility spring = ability(102L, 11L);
        EmpEmployee employee = new EmpEmployee();
        employee.setId(1L);
        employee.setRealName("Alice");
        ContestEvidenceItem javaEvidence = new ContestEvidenceItem();
        javaEvidence.setTargetRefId(101L);
        ContestEvidenceItem springEvidence = new ContestEvidenceItem();
        springEvidence.setTargetRefId(102L);

        when(empAbilityMapper.selectList(any())).thenReturn(List.of(java, spring));
        when(empEmployeeMapper.selectById(1L)).thenReturn(employee);
        when(abilityTagMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(tag(10L, "Java"), tag(11L, "Spring")));
        when(evidenceItemMapper.selectList(any())).thenReturn(List.of(javaEvidence, springEvidence));
        when(crossValidationService.validateAbility(any(), any(), any(), any(), any()))
                .thenReturn(new AbilityCrossValidationService.ValidationResult(100, "CONSISTENT", 1, "", "ACCEPT"));
        when(ragContextService.retrieveContext(any(), any(), anyInt())).thenReturn("evidence");

        newService().scanEmployeeAbilities(1L);

        verify(empAbilityMapper, times(1)).selectList(any());
        verify(empEmployeeMapper, times(1)).selectById(1L);
        verify(abilityTagMapper, times(1)).selectBatchIds(anyCollection());
        verify(evidenceItemMapper, times(1)).selectList(any());
        verify(abilityTagMapper, never()).selectById(any());
        verify(evidenceItemMapper, never()).selectCount(any());
    }

    private AbilityQualityMonitorServiceImpl newService() {
        return new AbilityQualityMonitorServiceImpl(
                empAbilityMapper,
                empEmployeeMapper,
                abilityTagMapper,
                evidenceItemMapper,
                ragContextService,
                crossValidationService
        );
    }

    private EmpAbility ability(Long id, Long tagId) {
        EmpAbility ability = new EmpAbility();
        ability.setId(id);
        ability.setTagId(tagId);
        ability.setEvaluationDate(LocalDate.now());
        ability.setMasteryLevel(3);
        ability.setEvaluationSource("MANUAL");
        return ability;
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        return tag;
    }
}
