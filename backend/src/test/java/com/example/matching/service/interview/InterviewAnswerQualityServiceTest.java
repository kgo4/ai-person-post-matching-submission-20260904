package com.example.matching.service.interview;

import com.example.matching.agent.dto.interview.InterviewAnswerQualityDTO;
import com.example.matching.agent.lc4j.InterviewAnswerQualityAiService;
import com.example.matching.agent.service.impl.AgentOutputValidator;
import com.example.matching.ai.validation.InterviewAnswerQualityValidator;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewAnswerQualityServiceTest {

    private ObjectProvider<InterviewAnswerQualityAiService> aiServiceProvider;
    private InterviewAnswerQualityAiService aiService;
    private InterviewAnswerQualityService service;
    private EmpVideoInterviewQuestion question;

    @BeforeEach
    void setUp() {
        aiServiceProvider = mock(ObjectProvider.class);
        aiService = mock(InterviewAnswerQualityAiService.class);
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        service = new InterviewAnswerQualityService(
                null, null, new ObjectMapper(), aiServiceProvider,
                new InterviewAnswerQualityValidator(), mock(AgentOutputValidator.class),
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()));
        question = new EmpVideoInterviewQuestion();
        question.setId(23L);
        question.setQuestionText("请描述一次项目经历");
    }

    @Test
    void ruleEvaluationUsesServerDerivedSourceRefs() {
        AnswerQualityEvaluation evaluation = service.evaluate(17L, question, "", null, null, null);

        assertThat(evaluation.sourceRefs()).containsExactly(
                "fact:INTERVIEW_SESSION:17",
                "fact:INTERVIEW_QUESTION:23");
    }

    @Test
    void explicitFakeProjectStatementReceivesZeroAndNeverTriggersFollowUp() {
        AnswerQualityEvaluation evaluation = service.evaluate(17L, question,
                "这个项目是我造假的，不好意思，我骗你的。", "Java", null, null);

        assertThat(evaluation.overallScore()).isZero();
        assertThat(evaluation.needFollowUp()).isFalse();
        assertThat(evaluation.conclusion()).contains("否认");
    }

    @Test
    void unknownOrIrrelevantShortAnswerReceivesZeroAndNeverTriggersFollowUp() {
        AnswerQualityEvaluation unknown = service.evaluate(17L, question, "我不知道。", "Java", null, null);
        AnswerQualityEvaluation irrelevant = service.evaluate(17L, question, "我长得帅不帅？", "Java", null, null);

        assertThat(unknown.overallScore()).isZero();
        assertThat(unknown.needFollowUp()).isFalse();
        assertThat(irrelevant.overallScore()).isZero();
        assertThat(irrelevant.needFollowUp()).isFalse();
    }

    @Test
    void validLlmDtoIsPreservedWithServerSourceRefs() {
        InterviewAnswerQualityDTO dto = validDto();
        dto.setSpecificityScore(77);
        dto.setNeedFollowUp(false);
        when(aiService.evaluate(org.mockito.ArgumentMatchers.eq(17L), org.mockito.ArgumentMatchers.anyString())).thenReturn(dto);

        AnswerQualityEvaluation evaluation = service.evaluate(17L, question, longSubstantiveAnswer(), null, null, null);

        // 合法 LLM 输出不被默认评估覆盖
        assertThat(evaluation.specificityScore()).isEqualTo(77);
        assertThat(evaluation.needFollowUp()).isFalse();
        assertThat(evaluation.conclusion()).isEqualTo("answer is partially supported");
        // sourceRefs 只由服务端生成，包含当前 session 与 question
        assertThat(evaluation.sourceRefs()).containsExactly(
                "fact:INTERVIEW_SESSION:17",
                "fact:INTERVIEW_QUESTION:23");
    }

    @Test
    void outOfRangeLlmDtoFallsBackToDefaultEvaluation() {
        InterviewAnswerQualityDTO dto = validDto();
        dto.setSpecificityScore(150);
        when(aiService.evaluate(org.mockito.ArgumentMatchers.eq(17L), org.mockito.ArgumentMatchers.anyString())).thenReturn(dto);

        AnswerQualityEvaluation evaluation = service.evaluate(17L, question, longSubstantiveAnswer(), null, null, null);

        // 越界字段触发确定性默认评估，而不是抛出异常或采纳非法值
        assertThat(evaluation.conclusion()).contains("不可用");
        assertThat(evaluation.overallScore()).isZero();
        assertThat(evaluation.needFollowUp()).isFalse();
        assertThat(evaluation.sourceRefs()).containsExactly(
                "fact:INTERVIEW_SESSION:17",
                "fact:INTERVIEW_QUESTION:23");
    }

    @Test
    void llmFailureDefaultEvaluationCarriesSourceRefs() {
        when(aiService.evaluate(org.mockito.ArgumentMatchers.eq(17L), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("model unavailable"));

        AnswerQualityEvaluation evaluation = service.evaluate(17L, question, longSubstantiveAnswer(), null, null, null);

        assertThat(evaluation.conclusion()).contains("不可用");
        assertThat(evaluation.overallScore()).isZero();
        assertThat(evaluation.needFollowUp()).isFalse();
        assertThat(evaluation.sourceRefs()).containsExactly(
                "fact:INTERVIEW_SESSION:17",
                "fact:INTERVIEW_QUESTION:23");
    }

    @Test
    void longSubstantiveAnswerReachesLlmBranchInsteadOfShortAnswerRule() {
        InterviewAnswerQualityDTO dto = validDto();
        dto.setNeedFollowUp(false);
        when(aiService.evaluate(org.mockito.ArgumentMatchers.eq(17L), org.mockito.ArgumentMatchers.anyString())).thenReturn(dto);

        AnswerQualityEvaluation evaluation = service.evaluate(17L, question, longSubstantiveAnswer(), null, null, null);

        // 长回答不应被"回答过短"规则拦截，而是进入 LLM 分支（needFollowUp/followUpReason 来自 LLM）
        assertThat(evaluation.needFollowUp()).isFalse();
        assertThat(evaluation.followUpReason()).isEqualTo("missing quantified result");
    }

    private InterviewAnswerQualityDTO validDto() {
        return new InterviewAnswerQualityDTO(
                new InterviewAnswerQualityDTO.StarCompleteness(true, true, true, false),
                60, 55, 50, 70,
                true, "missing quantified result", "result", "STAR_MISSING",
                List.of("quantified result"), List.of(), "answer is partially supported",
                null);
    }

    private String longSubstantiveAnswer() {
        return "我在校期间负责过一个校园二手交易平台项目，我带领 5 人团队完成了需求调研、"
                + "数据库设计和后端开发，最终上线后月活跃用户达到 2000 人，订单转化率提升了 15%。"
                + "我个人的主要贡献是设计了订单状态机并解决了并发扣库存的问题，"
                + "同时在项目复盘时推动团队引入了自动化测试，把回归测试时间从 3 小时缩短到 20 分钟。"
                + "这个经历让我对高并发场景下的数据一致性有了比较深的理解。";
    }
}
