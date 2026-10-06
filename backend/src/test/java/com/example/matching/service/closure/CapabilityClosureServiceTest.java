package com.example.matching.service.closure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.entity.closure.CapabilityClosureLog;
import com.example.matching.mapper.closure.CapabilityClosureLogMapper;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.closure.impl.CapabilityClosureServiceImpl;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.service.kg.GraphChangeSetService;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.learning.LearningPathService;
import com.example.matching.service.learning.LearningPathPlanService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.schedule.SchedulerMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityClosureServiceTest {

    @Mock
    private CapabilityClosureLogMapper closureLogMapper;
    @Mock
    private MatchingRematchValidationMapper matchingRematchValidationMapper;
    @Mock
    private com.example.matching.mapper.ability.PersonAbilityProfileMapper personAbilityProfileMapper;
    @Mock
    private PostQueryPort postQueryPort;
    @Mock
    private EvolutionQueryPort evolutionQueryPort;
    @Mock
    private MatchingQueryPort matchingQueryPort;
    @Mock
    private TalentQueryPort talentQueryPort;
    @Mock
    private TagQueryPort tagQueryPort;
    @Mock
    private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock
    private AgentBusinessApplyService agentBusinessApplyService;
    @Mock
    private LearningPathService learningPathService;
    @Mock
    private LearningPathPlanService learningPathPlanService;
    @Mock
    private KnowledgeGraphBuildService knowledgeGraphBuildService;
    @Mock
    private MatchingTaskService matchingTaskService;
    @Mock
    private GraphChangeSetService graphChangeSetService;
    @Mock
    private DistributedLockService distributedLockService;
    @Mock
    private SchedulerMetrics schedulerMetrics;

    private CapabilityClosureService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CapabilityClosureServiceImpl(
                closureLogMapper,
                matchingRematchValidationMapper,
                personAbilityProfileMapper,
                postQueryPort,
                evolutionQueryPort,
                matchingQueryPort,
                talentQueryPort,
                tagQueryPort,
                abilityEvidenceIngestionService,
                agentBusinessApplyService,
                learningPathService,
                learningPathPlanService,
                objectMapper,
                knowledgeGraphBuildService,
                graphChangeSetService,
                matchingTaskService,
                distributedLockService,
                schedulerMetrics
        );
    }

    @Test
    void emergingPostClosureCreatesEvidenceForEachAbilityModelAndWritesSucceededLog() {
        when(closureLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(postQueryPort.listRequirementsByPostId(100L)).thenReturn(List.of(
                postModel(11L, 100L, 1L),
                postModel(12L, 100L, 2L)
        ));

        CapabilityClosureResult result = service.onEmergingPostConfirmed(100L);

        assertThat(result.getBusinessKey()).isEqualTo("POST_EMERGING_CONFIRMED:POST:100");
        assertThat(result.getClosureStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getEvidenceCount()).isEqualTo(2);
        assertThat(result.getKnowledgeDocCount()).isEqualTo(2);
        // 图刷新为异步变更集（PENDING 语义），与 refreshGraph 实现一致
        assertThat(result.getGraphRefreshStatus()).isEqualTo("PENDING");
        verify(abilityEvidenceIngestionService).ingestPostAbilityModel(11L, "EMERGING_POST");
        verify(abilityEvidenceIngestionService).ingestPostAbilityModel(12L, "EMERGING_POST");
        verify(graphChangeSetService).requestChange("CLOSURE", "GRAPH", 100L, "UPSERT", java.util.Map.of("action", "rebuild"), null);

        ArgumentCaptor<CapabilityClosureLog> captor = ArgumentCaptor.forClass(CapabilityClosureLog.class);
        verify(closureLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getClosureStatus()).isEqualTo("SUCCEEDED");
        assertThat(captor.getValue().getBusinessKey()).isEqualTo("POST_EMERGING_CONFIRMED:POST:100");
    }

    @Test
    void emergingPostClosureIsIdempotentWhenSucceededLogAlreadyExists() {
        CapabilityClosureLog existing = new CapabilityClosureLog();
        existing.setEventType("POST_EMERGING_CONFIRMED");
        existing.setSourceType("POST");
        existing.setSourceRefId(100L);
        existing.setBusinessKey("POST_EMERGING_CONFIRMED:POST:100");
        existing.setClosureStatus("SUCCEEDED");
        existing.setEvidenceCount(2);
        existing.setKnowledgeDocCount(2);
        existing.setGraphRefreshStatus("SUCCEEDED");
        existing.setMessage("Post closure completed");
        when(closureLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        CapabilityClosureResult result = service.onEmergingPostConfirmed(100L);

        assertThat(result.getClosureStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getEvidenceCount()).isEqualTo(2);
        assertThat(result.getGraphRefreshStatus()).isEqualTo("SUCCEEDED");
        verify(postQueryPort, never()).listRequirementsByPostId(anyLong());
        verify(abilityEvidenceIngestionService, never()).ingestPostAbilityModel(anyLong(), anyString());
        verify(graphChangeSetService, never()).requestChange(anyString(), anyString(), anyLong(),
                anyString(), any(java.util.Map.class), any());
        verify(closureLogMapper, never()).insert(any(CapabilityClosureLog.class));
    }

    @Test
    void diagnoseMatchingRecordReturnsFailedAbilityGapsAndLearningPath() throws Exception {
        MatchingReportDTO report = new MatchingReportDTO();
        MatchingReportDTO.AbilityDetail failed = new MatchingReportDTO.AbilityDetail();
        failed.setTagId(7L);
        failed.setTagName("Java");
        failed.setActualLevel(new BigDecimal("2"));
        failed.setRequiredLevel(4);
        failed.setPassed(false);
        failed.setWeakEvidence(true);
        failed.setPassedDesc("Below required level");
        report.setAbilityDetails(List.of(failed));

        MatchingRecordDTO record = new MatchingRecordDTO(
                55L, 9L, 3L, null, null, null, null, null, null, null,
                null, null, null, objectMapper.writeValueAsString(report), null);
        when(matchingQueryPort.getById(55L)).thenReturn(record);

        LearningPathItemDTO item = new LearningPathItemDTO();
        item.setAbilityName("Java");
        item.setTitle("Java practice");
        when(learningPathService.generateLearningPath(any())).thenReturn(List.of(item));

        MatchDiagnosisResult result = service.diagnoseMatchingRecord(55L);

        assertThat(result.getMatchingRecordId()).isEqualTo(55L);
        assertThat(result.getGaps()).hasSize(1);
        assertThat(result.getGaps().get(0).getAbilityName()).isEqualTo("Java");
        assertThat(result.getGaps().get(0).getRequiredLevel()).isEqualTo(4);
        assertThat(result.getGaps().get(0).isWeakEvidence()).isTrue();
        assertThat(result.getLearningPath()).hasSize(1);
        assertThat(result.getLearningPath().get(0).getTitle()).isEqualTo("Java practice");
        verify(learningPathPlanService).generateFromMatchingRecord(any());
    }

    @Test
    void learningOutcomeCreatesEmployeeAbilityEvidenceAndSucceededLog() {
        when(closureLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        EmployeeAbilityDTO writtenAbility = new EmployeeAbilityDTO(77L, 9L, 7L, 4, "MANUAL", null, null, null);
        when(talentQueryPort.getEmpAbility(9L, 7L, "MANUAL")).thenReturn(writtenAbility);
        when(agentBusinessApplyService.applyPersonAbilities(any())).thenReturn(
                new AgentBusinessApplyService.PersonAbilityApplyResult(1, 1, 0, 0, 0));

        LearningOutcomeConfirmDTO dto = new LearningOutcomeConfirmDTO();
        dto.setEmpId(9L);
        dto.setTagId(7L);
        dto.setAbilityName("Java");
        dto.setCompletedResourceId(88L);
        dto.setBeforeLevel(2);
        dto.setConfirmedLevel(4);
        dto.setConfirmationSource("MANUAL_CONFIRM");
        dto.setNote("Finished Java project");

        CapabilityClosureResult result = service.onLearningOutcomeConfirmed(dto);

        assertThat(result.getBusinessKey()).isEqualTo("LEARNING_OUTCOME_CONFIRMED:EMP:9:TAG:7:RESOURCE:88");
        assertThat(result.getClosureStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getEvidenceCount()).isEqualTo(1);
        // 图刷新为异步变更集（PENDING 语义），与 refreshGraph 实现一致
        assertThat(result.getGraphRefreshStatus()).isEqualTo("PENDING");
        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(77L, "MANUAL");
        verify(graphChangeSetService).requestChange("CLOSURE", "GRAPH", 9L, "UPSERT", java.util.Map.of("action", "rebuild"), null);
    }

    private PostAbilityDTO postModel(Long id, Long postId, Long tagId) {
        return new PostAbilityDTO(id, postId, tagId, null, null, null, null, null, null, null);
    }
}
