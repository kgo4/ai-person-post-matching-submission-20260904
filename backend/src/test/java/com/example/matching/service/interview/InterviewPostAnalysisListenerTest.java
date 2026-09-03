package com.example.matching.service.interview;

import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.event.InterviewFinishedEvent;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.dto.interview.LearningPathSuggestion;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import com.example.matching.service.learning.LearningPathPlanService;
import com.example.matching.service.employee.impl.VideoInterviewVisualAnalyzer;
import com.example.matching.service.assessment.InterviewAssessmentEvidenceService;
import com.example.matching.service.assessment.AssessmentReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class InterviewPostAnalysisListenerTest {

    @Mock
    private AIInterviewAgent aiInterviewAgent;
    @Mock
    private PersonAbilityProfileAgent personAbilityProfileAgent;
    @Mock
    private EmpVideoInterviewSessionMapper sessionMapper;
    @Mock
    private MatchingRecordMapper matchingRecordMapper;
    @Mock
    private LearningPathPlanService learningPathPlanService;
    @Mock
    private InterviewAssessmentEvidenceService interviewAssessmentEvidenceService;
    @Mock
    private AssessmentReportService assessmentReportService;
    @Mock
    private VideoInterviewVisualAnalyzer visualAnalyzer;

    @InjectMocks
    private InterviewPostAnalysisListener listener;

    @Test
    void reportLearningSuggestionsCreateMatchingLinkedPlan() {
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(21L);
        session.setEmpId(7L);
        session.setPostId(8L);
        when(sessionMapper.selectById(21L)).thenReturn(session);
        // 状态机 CAS：FINISHED(3) -> ANALYZING(4) 抢占成功；分析完成 4 -> 5 回写成功
        when(sessionMapper.transitionStatus(21L, 3, 4)).thenReturn(1);
        when(sessionMapper.completeAnalysis(org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        CompetencyReport report = new CompetencyReport(
                21L, 7L, 8L, 70, 68, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new LearningPathSuggestion(5L, "Java", 2, 4,
                        "Practice concurrent programming", "HIGH")),
                "Needs improvement", "Continue learning", false, null);
        when(aiInterviewAgent.generateCompetencyReport(21L)).thenReturn(report);

        MatchingRecord record = new MatchingRecord();
        record.setId(99L);
        when(matchingRecordMapper.selectOne(any())).thenReturn(record);

        listener.onInterviewFinished(new InterviewFinishedEvent(21L));

        org.mockito.InOrder analysisOrder = inOrder(visualAnalyzer, aiInterviewAgent);
        analysisOrder.verify(visualAnalyzer).analyzeVisualEvidence(21L);
        analysisOrder.verify(aiInterviewAgent).conductInterviewAndObserve(21L);

        ArgumentCaptor<LearningPathGenerateRequest> request =
                ArgumentCaptor.forClass(LearningPathGenerateRequest.class);
        verify(learningPathPlanService).generateFromMatchingRecord(request.capture());
        assertThat(request.getValue().getMatchingRecordId()).isEqualTo(99L);
        assertThat(request.getValue().getIncludeProjectTasks()).isTrue();
        assertThat(request.getValue().getForceRegenerate()).isFalse();
    }

    @Test
    void alreadyAnalyzingSessionSkipsDuplicateAnalysis() {
        // 回归：会话已在分析中(4)时事件重放/双跑必须被 CAS 拒绝
        when(sessionMapper.transitionStatus(21L, 3, 4)).thenReturn(0); // CAS 失败

        listener.onInterviewFinished(new InterviewFinishedEvent(21L));

        org.mockito.Mockito.verify(aiInterviewAgent, org.mockito.Mockito.never())
                .conductInterviewAndObserve(21L);
    }

    @Test
    void failedAnalysisReturnsSessionToFinishedForRetry() {
        // 回归：分析异常时回退 FINISHED(3) 供恢复调度器重试
        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setId(21L);
        session.setEmpId(7L);
        session.setPostId(8L);
        when(sessionMapper.selectById(21L)).thenReturn(session);
        when(sessionMapper.transitionStatus(21L, 3, 4)).thenReturn(1);
        when(sessionMapper.transitionStatus(21L, 4, 3)).thenReturn(1);
        org.mockito.Mockito.when(aiInterviewAgent.conductInterviewAndObserve(21L))
                .thenThrow(new RuntimeException("LLM timeout"));

        listener.onInterviewFinished(new InterviewFinishedEvent(21L));

        org.mockito.Mockito.verify(sessionMapper).transitionStatus(21L, 4, 3);
        org.mockito.Mockito.verify(sessionMapper).markAnalysisFailure(
                org.mockito.ArgumentMatchers.eq(21L), org.mockito.ArgumentMatchers.anyString());
    }
}
