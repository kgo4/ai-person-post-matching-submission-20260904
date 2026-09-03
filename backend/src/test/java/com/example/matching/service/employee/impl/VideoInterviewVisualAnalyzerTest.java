package com.example.matching.service.employee.impl;

import com.example.matching.integration.volcengine.DoubaoChatClient;
import com.example.matching.mapper.employee.EmpVideoInterviewEvidenceMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.List;
import com.example.matching.entity.employee.EmpVideoInterviewEvidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VideoInterviewVisualAnalyzerTest {

    private final VideoInterviewVisualAnalyzer analyzer = new VideoInterviewVisualAnalyzer(
            mock(EmpVideoInterviewEvidenceMapper.class), mock(EmpVideoInterviewQuestionMapper.class),
            mock(InterviewFollowUpQuestionMapper.class),
            (Executor) Runnable::run,
            mock(com.example.matching.integration.volcengine.VideoInterviewPromptBuilder.class),
            mock(DoubaoChatClient.class), new ObjectMapper());

    @Test
    void extractsModelVisualScoreInsteadOfUsingAConstant() {
        assertThat(analyzer.extractVisualScore("{\"visualScore\":63}")).isEqualByComparingTo(BigDecimal.valueOf(63));
    }

    @Test
    void ignoresMissingOrInvalidVisualScore() {
        assertThat(analyzer.extractVisualScore("{\"summary\":\"ok\"}")).isNull();
        assertThat(analyzer.extractVisualScore("not-json")).isNull();
    }

    @Test
    void selectsExactlyOneMiddleFrameForEachVisualUnit() throws Exception {
        EmpVideoInterviewEvidence first = evidence(1);
        EmpVideoInterviewEvidence middle = evidence(5);
        EmpVideoInterviewEvidence last = evidence(9);
        List<EmpVideoInterviewEvidence> selected = analyzer.selectKeyFrames(List.of(first, middle, last));
        assertThat(selected).containsExactly(middle);
    }

    private EmpVideoInterviewEvidence evidence(int second) throws Exception {
        EmpVideoInterviewEvidence evidence = new EmpVideoInterviewEvidence();
        evidence.setFrameRefsJson(new ObjectMapper().writeValueAsString(java.util.Map.of("captureSecond", second)));
        return evidence;
    }
}
