package com.example.matching.service.learning.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.learning.LearningAssessmentGenerateRequest;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningPathPlan;
import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.mapper.learning.LearningAssessmentItemMapper;
import com.example.matching.mapper.learning.LearningPathPlanMapper;
import com.example.matching.mapper.learning.LearningPathStepMapper;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.service.closure.CapabilityClosureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习测评服务单测：AI 题目生成/评分成功路径 + 失败降级模板/关键词 + 确认提升前置校验。
 */
@ExtendWith(MockitoExtension.class)
class LearningAssessmentServiceImplTest {

    @Mock private LearningAssessmentItemMapper assessmentItemMapper;
    @Mock private LearningPathPlanMapper planMapper;
    @Mock private LearningPathStepMapper stepMapper;
    @Mock private CapabilityClosureService capabilityClosureService;
    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private LlmResponseParser llmResponseParser;

    private LearningAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LearningAssessmentServiceImpl(
                assessmentItemMapper, planMapper, stepMapper, capabilityClosureService,
                langChain4jChatService, llmResponseParser, new ObjectMapper());
    }

    private LearningPathPlan newPlan() {
        LearningPathPlan plan = new LearningPathPlan();
        plan.setId(1L);
        plan.setEmpId(100L);
        plan.setIsDeleted(0);
        return plan;
    }

    private LearningPathStep newStep() {
        LearningPathStep step = new LearningPathStep();
        step.setId(10L);
        step.setPlanId(1L);
        step.setAbilityName("云原生架构");
        step.setAbilityTagId(11L);
        step.setCurrentLevel(2);
        step.setTargetLevel(3);
        step.setGapType("LEVEL_GAP");
        step.setPriority("HIGH");
        step.setIsDeleted(0);
        return step;
    }

    @Test
    void generateAssessmentsUsesAiQuestionWhenAvailable() {
        when(planMapper.selectById(1L)).thenReturn(newPlan());
        when(stepMapper.selectList(any())).thenReturn(List.of(newStep()));
        String aiJson = "{\"questionText\":\"请描述一次生产环境服务降级的排查过程\",\"referenceAnswer\":\"场景+方案+验证\",\"difficultyLevel\":\"HARD\",\"scoringPoints\":\"排查思路/证据\"}";
        when(langChain4jChatService.chat(eq("learning-assessment-question"), any(), any(), any())).thenReturn(aiJson);
        when(llmResponseParser.extractJson(aiJson)).thenReturn(aiJson);

        LearningAssessmentGenerateRequest request = new LearningAssessmentGenerateRequest();
        request.setPlanId(1L);
        List<LearningAssessmentItem> items = service.generateAssessments(request);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSource()).isEqualTo("AI_LEARNING");
        assertThat(items.get(0).getQuestionText()).contains("服务降级");
        assertThat(items.get(0).getReferenceAnswer()).contains("评分要点");
        verify(assessmentItemMapper, times(1)).insert(any(LearningAssessmentItem.class));
    }

    @Test
    void generateAssessmentsFallsBackToTemplateWhenAiUnavailable() {
        when(planMapper.selectById(1L)).thenReturn(newPlan());
        when(stepMapper.selectList(any())).thenReturn(List.of(newStep()));
        when(langChain4jChatService.chat(eq("learning-assessment-question"), any(), any(), any())).thenReturn(null);

        LearningAssessmentGenerateRequest request = new LearningAssessmentGenerateRequest();
        request.setPlanId(1L);
        List<LearningAssessmentItem> items = service.generateAssessments(request);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSource()).isEqualTo("SYSTEM_TEMPLATE");
        assertThat(items.get(0).getQuestionText()).contains("云原生架构");
    }

    @Test
    void answerUsesAiScoreWhenAvailable() {
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setId(50L);
        item.setPlanId(1L);
        item.setStepId(10L);
        item.setQuestionText("请说明如何在项目中应用云原生架构");
        item.setReferenceAnswer("参考答案要点");
        when(assessmentItemMapper.selectById(50L)).thenReturn(item);
        String aiJson = "{\"score\":85,\"passed\":true,\"feedback\":\"回答完整，包含验证证据。\"}";
        when(langChain4jChatService.chat(eq("learning-assessment-score"), any(), any(), any())).thenReturn(aiJson);
        when(llmResponseParser.extractJson(aiJson)).thenReturn(aiJson);

        LearningAssessmentItem result = service.answer(50L, "我在项目中采用K8s部署，验证了滚动发布与回滚，监控指标正常。");

        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.getAssessmentStatus()).isEqualTo("PASSED");
        assertThat(result.getScoringFeedback()).contains("回答完整");
    }

    @Test
    void answerFallsBackToKeywordScoringWhenAiUnavailable() {
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setId(50L);
        item.setPlanId(1L);
        item.setStepId(10L);
        item.setQuestionText("请说明如何在项目中应用云原生架构");
        when(assessmentItemMapper.selectById(50L)).thenReturn(item);
        when(langChain4jChatService.chat(eq("learning-assessment-score"), any(), any(), any())).thenReturn(null);

        String longAnswer = "我在项目中负责云原生架构设计。业务场景是微服务拆分，技术方案是Kubernetes与容器化。实现时引入了服务网格，通过测试验证了流量治理效果，监控指标显示稳定性提升，并排查了若干性能问题。";
        LearningAssessmentItem result = service.answer(50L, longAnswer);

        assertThat(result.getAssessmentStatus()).isEqualTo("PASSED");
        assertThat(result.getScore()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void confirmAbilityImprovementRequiresPassedAssessment() {
        when(planMapper.selectById(1L)).thenReturn(newPlan());
        when(stepMapper.selectById(10L)).thenReturn(newStep());
        when(assessmentItemMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.confirmAbilityImprovement(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测评");
    }
}
