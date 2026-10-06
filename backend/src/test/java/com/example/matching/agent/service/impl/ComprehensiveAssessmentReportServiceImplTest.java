package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportModelResult;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportModelResult.SectionInsight;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult;
import com.example.matching.agent.lc4j.ComprehensiveAssessmentReportAiService;
import com.example.matching.agent.service.ComprehensiveAssessmentReportService;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.DecisionFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.InterviewFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.ResumeFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.TestFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 全方位评估报告洞察服务测试。
 *
 * <p>核心承诺：**AI 不可用时数值部分完全不受影响**（数值本就不经本服务），
 * 只有洞察文字降级为模板；模型编造的部分会被过滤掉。
 */
class ComprehensiveAssessmentReportServiceImplTest {

    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_TEMPLATE = "TEMPLATE";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRunConfidencePolicy confidencePolicy = new AgentRunConfidencePolicy();

    @Test
    void whenAgentDisabled_fallsBackToTemplateAndNeverCallsModel() {
        ComprehensiveAssessmentReportAiService aiService = mock(ComprehensiveAssessmentReportAiService.class);
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(false), aiService);

        ComprehensiveAssessmentReportResult result = service.generateInsights(context());

        assertThat(result.getInsightSource()).isEqualTo(SOURCE_TEMPLATE);
        assertThat(result.getFallbackUsed()).isTrue();
        // 模板仍逐部分给出解读（文字可用，数值来自既有链路、不受影响）
        assertThat(result.getSectionInsights()).isNotEmpty();
        assertThat(result.getSectionInsights())
                .extracting(ComprehensiveAssessmentReportResult.SectionInsight::section)
                .contains(ComprehensiveAssessmentReportService.SECTION_AI_TEST,
                        ComprehensiveAssessmentReportService.SECTION_FINAL_LEVEL);
        verifyNoInteractions(aiService);
    }

    @Test
    void whenAiServiceBeanMissing_fallsBackToTemplate() {
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(true), null);

        ComprehensiveAssessmentReportResult result = service.generateInsights(context());

        assertThat(result.getInsightSource()).isEqualTo(SOURCE_TEMPLATE);
        assertThat(result.getFallbackUsed()).isTrue();
    }

    @Test
    void whenModelThrows_fallsBackToTemplateInsteadOfPropagating() {
        ComprehensiveAssessmentReportAiService aiService = mock(ComprehensiveAssessmentReportAiService.class);
        when(aiService.generate(anyString())).thenThrow(new RuntimeException("model unavailable"));
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(true), aiService);

        ComprehensiveAssessmentReportResult result = service.generateInsights(context());

        assertThat(result.getInsightSource()).isEqualTo(SOURCE_TEMPLATE);
        assertThat(result.getFallbackUsed()).isTrue();
    }

    @Test
    void whenModelReturnsNull_fallsBackToTemplate() {
        ComprehensiveAssessmentReportAiService aiService = mock(ComprehensiveAssessmentReportAiService.class);
        when(aiService.generate(anyString())).thenReturn(null);
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(true), aiService);

        assertThat(service.generateInsights(context()).getInsightSource()).isEqualTo(SOURCE_TEMPLATE);
    }

    @Test
    void aiResult_keepsOnlyValidSectionsAndCleansBlankItems() {
        ComprehensiveAssessmentReportAiService aiService = mock(ComprehensiveAssessmentReportAiService.class);
        ComprehensiveAssessmentReportModelResult model = new ComprehensiveAssessmentReportModelResult();
        model.setSectionInsights(List.of(
                insight(ComprehensiveAssessmentReportService.SECTION_AI_TEST, "测试表现稳定"),
                insight("INVENTED_SECTION", "模型编造的部分")));
        model.setStrengths(List.of("基础扎实", "   "));
        model.setWeaknesses(List.of());
        model.setConclusion("整体符合岗位要求");
        when(aiService.generate(anyString())).thenReturn(model);
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(true), aiService);

        ComprehensiveAssessmentReportResult result = service.generateInsights(context());

        assertThat(result.getInsightSource()).isEqualTo(SOURCE_AI);
        assertThat(result.getFallbackUsed()).isFalse();
        // 编造的 section 被过滤，只保留合法部分
        assertThat(result.getSectionInsights()).hasSize(1);
        assertThat(result.getSectionInsights().get(0).section())
                .isEqualTo(ComprehensiveAssessmentReportService.SECTION_AI_TEST);
        // 空白条目被清理
        assertThat(result.getStrengths()).containsExactly("基础扎实");
        assertThat(result.getConclusion()).isEqualTo("整体符合岗位要求");
    }

    @Test
    void nullContext_isHandledWithoutException() {
        ComprehensiveAssessmentReportServiceImpl service = service(enabled(false), null);

        ComprehensiveAssessmentReportResult result = service.generateInsights(null);

        assertThat(result.getInsightSource()).isEqualTo(SOURCE_TEMPLATE);
        assertThat(result.getSectionInsights()).isEmpty();
    }

    /* ===================== 辅助 ===================== */

    private ComprehensiveAssessmentReportServiceImpl service(LangChain4jAgentProperties properties,
                                                             ComprehensiveAssessmentReportAiService aiService) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ComprehensiveAssessmentReportAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);
        return new ComprehensiveAssessmentReportServiceImpl(confidencePolicy, properties, objectMapper, provider);
    }

    private LangChain4jAgentProperties enabled(boolean enabled) {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private SectionInsight insight(String section, String text) {
        SectionInsight insight = new SectionInsight();
        insight.setSection(section);
        insight.setInsight(text);
        return insight;
    }

    private ComprehensiveAssessmentContext context() {
        ComprehensiveAssessmentContext context = new ComprehensiveAssessmentContext();
        context.setEmpId(7L);
        context.setEmpName("张三");
        context.setResume(new ResumeFact("本科", 5, "后端工程师", 6, "SUCCESS"));
        context.setTests(List.of(new TestFact(1L, "Java", new BigDecimal("80"), 4)));
        context.setInterview(new InterviewFact(
                new BigDecimal("85"), 3, List.of("经验偏浅"), List.of("基础扎实")));
        context.setDecisions(List.of(new DecisionFact(1L, "Java", 4, 90, "HUMAN_CONFIRMED")));
        return context;
    }
}
