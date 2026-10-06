package com.example.matching.ai.service.impl;

import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.service.MatchingAnalysisAgentService;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingFeedbackDatasetService;
import com.example.matching.service.matching.MatchingReportService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.post.PostAbilityModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * AiMatchingServiceImpl 单元测试
 * <p>
 * 覆盖路径：generateAnalysisReport / generateStructuredScore 均委托 MatchingAnalysisAgent 链路
 * （该链路负责 context package + 岗位范围内 RAG + graph context + GroundedAgentOutputValidator）。
 */
@ExtendWith(MockitoExtension.class)
class AiMatchingServiceImplTest {

    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private AiServiceResilience aiServiceResilience;
    @Mock private MatchingRecordMapper matchingRecordMapper;
    @Mock private MatchingDataQueryService dataQuery;
    @Mock private MatchingFeedbackDatasetService feedbackDatasetService;
    @Mock private MatchingAlgorithmService matchingAlgorithmService;
    @Mock private PostAbilityModelService postAbilityModelService;
    @Mock private MatchingReportService matchingReportService;
    @Mock private MatchingAnalysisAgentService matchingAnalysisAgentService;

    private AiMatchingServiceImpl service;

    private static final Long RECORD_ID = 100L;
    private static final Long EMP_ID = 1L;
    private static final Long POST_ID = 2L;

    @BeforeEach
    void setUp() {
        service = new AiMatchingServiceImpl(
                langChain4jChatService, promptTemplateService, aiServiceResilience,
                matchingRecordMapper, dataQuery, feedbackDatasetService,
                matchingAlgorithmService, postAbilityModelService,
                matchingReportService, new ObjectMapper(),
                matchingAnalysisAgentService
        );
    }

    // ==================== generateAnalysisReport ====================

    @Test
    void generateAnalysisReport_delegatesToGroundedAgentChain() {
        stubBasicData();
        when(matchingAnalysisAgentService.analyze(argThat(r -> RECORD_ID.equals(r.getMatchingRecordId()))))
                .thenReturn(agentResult(85, "适配"));

        String report = service.generateAnalysisReport(RECORD_ID);

        verify(matchingAnalysisAgentService).analyze(argThat(r -> RECORD_ID.equals(r.getMatchingRecordId())));
        assertThat(report).contains("\"overallScore\":85");
        assertThat(report).contains("\"conclusion\":\"适配\"");
        assertThat(report).contains("\"fallbackUsed\":false");
        assertThat(report).contains("\"sourceRefs\"");
    }

    @Test
    void generateAnalysisReport_returnsErrorReportWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        String report = service.generateAnalysisReport(RECORD_ID);

        assertThat(report).contains("匹配记录不存在");
        verify(matchingAnalysisAgentService, never()).analyze(any());
    }

    @Test
    void generateAnalysisReport_fallsBackToQuantitativeWhenAgentFails() {
        stubBasicData();
        when(matchingAnalysisAgentService.analyze(any()))
                .thenThrow(new RuntimeException("AI service timeout"));

        String report = service.generateAnalysisReport(RECORD_ID);

        assertThat(report).contains("overallScore");
    }

    // ==================== generateStructuredScore ====================

    @Test
    void generateStructuredScore_returnsFullResultOnSuccess() {
        stubBasicData();
        when(matchingAnalysisAgentService.analyze(any())).thenReturn(agentResult(82, "适配"));
        when(matchingReportService.extractGapAbilities(any())).thenReturn(List.of());

        Map<String, Object> result = service.generateStructuredScore(RECORD_ID);

        assertThat(result.get("aiScore")).isEqualTo(new BigDecimal("82.00"));
        assertThat(result.get("conclusion")).isEqualTo("适配");
        assertThat(result.get("dimensionScores")).isInstanceOf(List.class);
        assertThat(result.get("dimensionScoresComputed")).isEqualTo(true);
        assertThat(result.get("fallbackUsed")).isEqualTo(false);
    }

    @Test
    void generateStructuredScore_returnsErrorWhenRecordNotFound() {
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(null);

        Map<String, Object> result = service.generateStructuredScore(RECORD_ID);

        assertThat(result.get("aiScore")).isEqualTo(BigDecimal.ZERO);
        assertThat(result.get("conclusion")).isEqualTo("错误");
    }

    @Test
    void generateStructuredScore_fallsBackWhenAgentThrowsException() {
        stubBasicData();
        when(matchingAnalysisAgentService.analyze(any()))
                .thenThrow(new RuntimeException("DeepSeek rate limit"));
        lenient().when(matchingReportService.extractGapAbilities(any())).thenReturn(List.of());

        Map<String, Object> result = service.generateStructuredScore(RECORD_ID);

        assertThat(result.get("aiScore")).isEqualTo(new BigDecimal("75.00"));
        assertThat(result.get("conclusion")).isEqualTo("AI服务不可用");
        assertThat(result.get("dimensionScoresComputed")).isEqualTo(false);
    }

    @Test
    void generateStructuredScore_usesL2ScoreWhenAgentOmitsScore() {
        stubBasicData();
        MatchingAnalysisAgentResult agentResult = new MatchingAnalysisAgentResult();
        agentResult.setConclusion("待观察");
        agentResult.setDimensionScores(List.of());
        when(matchingAnalysisAgentService.analyze(any())).thenReturn(agentResult);
        when(matchingReportService.extractGapAbilities(any())).thenReturn(List.of());

        Map<String, Object> result = service.generateStructuredScore(RECORD_ID);

        assertThat(result.get("aiScore")).isEqualTo(new BigDecimal("75.00"));
    }

    @Test
    void generateStructuredScore_marksFallbackUsedWhenAgentFellBack() {
        stubBasicData();
        MatchingAnalysisAgentResult agentResult = agentResult(60, "适配");
        agentResult.setFallbackUsed(true);
        when(matchingAnalysisAgentService.analyze(any())).thenReturn(agentResult);
        when(matchingReportService.extractGapAbilities(any())).thenReturn(List.of());

        Map<String, Object> result = service.generateStructuredScore(RECORD_ID);

        assertThat(result.get("fallbackUsed")).isEqualTo(true);
        assertThat(result.get("aiScore")).isEqualTo(new BigDecimal("60.00"));
    }

    // ==================== executeMatching ====================

    @Test
    void executeMatching_returnsEmptyListAsDeprecated() {
        List<Map<String, Object>> result = service.executeMatching(1L, List.of(1L), "default");
        assertThat(result).isEmpty();
    }

    // ==================== helpers ====================

    private MatchingAnalysisAgentResult agentResult(int score, String conclusion) {
        MatchingAnalysisAgentResult result = new MatchingAnalysisAgentResult();
        result.setSuggestedLlmScore(BigDecimal.valueOf(score));
        result.setConclusion(conclusion);
        result.setStrengths(List.of("Java"));
        result.setGaps(List.of());
        result.setRiskSignals(List.of());
        result.setHumanAttentionPoints(List.of());
        result.setDimensionScores(List.of(Map.of("dimension", "technical", "score", score)));
        result.setFallbackUsed(false);
        result.setSourceRefs(List.of(new AgentSourceRef()));
        return result;
    }

    private MatchingRecord buildRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(RECORD_ID);
        record.setEmpId(EMP_ID);
        record.setPostId(POST_ID);
        record.setL2Score(new BigDecimal("75.00"));
        record.setAiMatchScore(new BigDecimal("78.00"));
        record.setMatchStatus(2);
        return record;
    }

    private void stubBasicData() {
        MatchingRecord record = buildRecord();
        when(matchingRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        lenient().when(matchingRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        emp.setRealName("张三");
        emp.setLevel("P6");
        lenient().when(dataQuery.getEmployeeById(EMP_ID)).thenReturn(emp);

        PostPost post = new PostPost();
        post.setId(POST_ID);
        post.setPostName("Java开发");
        post.setJobDescription("负责后端开发");
        post.setPostLevel("P6");
        lenient().when(dataQuery.getPostById(POST_ID)).thenReturn(post);

        lenient().when(dataQuery.batchLoadAbilities(any())).thenReturn(Map.of());

        PostAbilityModel req = new PostAbilityModel();
        req.setTagId(10L);
        req.setPostId(POST_ID);
        req.setMinRequiredLevel(3);
        req.setWeight(new BigDecimal("0.5"));
        req.setIsRequired(1);
        req.setIsCore(1);
        lenient().when(dataQuery.listRequirementsByPostId(POST_ID)).thenReturn(List.of(req));

        AbilityTag tag = new AbilityTag();
        tag.setId(10L);
        tag.setTagName("Java");
        lenient().when(dataQuery.getTagById(10L)).thenReturn(tag);

        lenient().when(postAbilityModelService.calculateQualityScore(POST_ID)).thenReturn(new BigDecimal("85.00"));

        lenient().when(feedbackDatasetService.getFeedbackSummary(20)).thenReturn(Map.of());
        lenient().when(matchingAlgorithmService.fuseAbilityLevel(any())).thenReturn(Map.of());
        lenient().when(matchingAlgorithmService.generateEvidenceDetail(any())).thenReturn(Map.of());
    }
}
