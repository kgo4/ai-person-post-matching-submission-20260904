package com.example.matching.service.impl;

import com.example.matching.service.employee.impl.PmsAbilityAnalysisServiceImpl;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.PmsAnalysisTaskMapper;
import com.example.matching.mapper.system.PmsUserMappingMapper;
import com.example.matching.repository.PmsDataRepository;
import com.example.matching.service.employee.impl.PmsAbilityImportEngine;
import com.example.matching.service.employee.PmsAbilityAnalysisAgent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsAbilityAnalysisHarnessTest {

    @InjectMocks
    private PmsAbilityAnalysisServiceImpl service;

    @Mock private PmsDataRepository pmsDataRepository;
    @Mock private PmsUserMappingMapper userMappingMapper;
    @Mock private PmsAnalysisTaskMapper analysisTaskMapper;
    @Mock private EmpEmployeeMapper employeeMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PmsAbilityAnalysisAgent pmsAbilityAnalysisAgent;
    @Mock private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        PmsAbilityImportEngine engine = new PmsAbilityImportEngine(
                userMappingMapper, analysisTaskMapper, employeeMapper, empAbilityMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "importEngine", engine);
    }

    @Test
    void importAbilitiesWritesSelectedPmsEvidenceDirectlyWithoutHarnessOrTagAdmission() throws Exception {
        PmsAnalysisTask task = new PmsAnalysisTask();
        task.setId(30L);
        task.setEmpId(9L);
        task.setAnalysisStatus(2);
        task.setAiRawResponse("{\"abilities\":[{\"tagName\":\"Java\",\"tagCategory\":\"TECHNICAL\",\"level\":4,\"confidence\":0.9,\"evidence\":\"负责Java服务改造\"}]}");
        when(analysisTaskMapper.selectById(30L)).thenReturn(task);

        when(objectMapper.readValue(eq(task.getAiRawResponse()), any(TypeReference.class))).thenReturn(Map.of(
                "abilities", List.of(Map.of(
                        "tagName", "Java",
                        "tagCategory", "TECHNICAL",
                        "level", 4,
                        "confidence", 0.9,
                        "evidence", "负责Java服务改造",
                        "sourceRefs", List.of("source:PMS_TASK:101")
                ))
        ));

        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        int imported = service.importAbilities(9L, 30L, null);

        assertThat(imported).isEqualTo(1);
        assertThat(task.getAnalysisStatus()).isEqualTo(6);
        org.mockito.ArgumentCaptor<EmpAbility> captor = org.mockito.ArgumentCaptor.forClass(EmpAbility.class);
        verify(empAbilityMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvaluationSource()).isEqualTo("AI_PROJECT");
        assertThat(captor.getValue().getAbilityName()).isEqualTo("Java");
        assertThat(captor.getValue().getTagId()).isNull();
        assertThat(captor.getValue().getGovernanceAdmissionId()).isNull();
    }

    @Test
    void analysisPassesCurrentRawPmsRecordsToTheAgent() {
        PmsUserMapping mapping = new PmsUserMapping();
        mapping.setEmpId(9L);
        mapping.setPmsUserId(99L);
        when(userMappingMapper.selectByEmpId(9L)).thenReturn(mapping);
        org.mockito.Mockito.doAnswer(invocation -> {
            PmsAnalysisTask task = invocation.getArgument(0);
            task.setId(30L);
            return 1;
        }).when(analysisTaskMapper).insert(any(PmsAnalysisTask.class));
        when(pmsDataRepository.getWorkOrders(99L, 6)).thenReturn(List.of(Map.of("id", 1L, "title", "实现支付接口")));
        when(pmsDataRepository.getBugs(99L, 6)).thenReturn(List.of(Map.of("id", 2L, "title", "修复并发问题")));
        when(pmsDataRepository.getTestCases(99L, 6)).thenReturn(List.of());
        when(pmsDataRepository.getProjectParticipation(99L)).thenReturn(List.of(Map.of("id", 3L, "name", "支付平台")));
        com.example.matching.agent.dto.person.PersonAbilityExtractionResult extraction =
                new com.example.matching.agent.dto.person.PersonAbilityExtractionResult();
        extraction.setClaims(new ArrayList<>());
        when(pmsAbilityAnalysisAgent.extractCombined(eq(9L), eq(30L), any())).thenReturn(extraction);

        service.analyzeEmployee(9L, 6);

        org.mockito.ArgumentCaptor<Map<String, Object>> payload = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(pmsAbilityAnalysisAgent).extractCombined(eq(9L), eq(30L), payload.capture());
        assertThat(payload.getValue()).containsKeys("workOrders", "bugs", "testCases", "projects");
        assertThat((List<?>) payload.getValue().get("workOrders")).hasSize(1);
    }
}
