package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportModelResult;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult;
import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult.SectionInsight;
import com.example.matching.agent.lc4j.ComprehensiveAssessmentReportAiService;
import com.example.matching.agent.service.ComprehensiveAssessmentReportService;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.DecisionFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.InterviewFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.ResumeFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.SourceWeightFact;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.TestFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全方位评估报告的 AI 洞察实现。
 *
 * <p>流程：组装四部分摘要 JSON → 调 AI 写文字 → 校验 → 返回。
 * AI 不可用（未启用 / Bean 缺失 / 调用异常 / 校验失败）时降级为模板文字，
 * <b>数值部分不受任何影响</b>（本就来自既有链路，本服务不碰）。
 */
@Slf4j
@Service
public class ComprehensiveAssessmentReportServiceImpl extends AbstractAgentService
        implements ComprehensiveAssessmentReportService {

    /** JSON 契约场景名（注册在 LangChain4jAgentConfig 与 JsonFewShotRegistry） */
    public static final String SCENE = "COMPREHENSIVE_ASSESSMENT_REPORT";

    /** 合法的 section 取值，用于过滤模型可能编造的部分名 */
    private static final Set<String> VALID_SECTIONS = Set.of(
            SECTION_RESUME, SECTION_AI_TEST, SECTION_AI_INTERVIEW, SECTION_FINAL_LEVEL);

    private final LangChain4jAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final ComprehensiveAssessmentReportAiService aiService;

    public ComprehensiveAssessmentReportServiceImpl(
            AgentRunConfidencePolicy confidencePolicy,
            LangChain4jAgentProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<ComprehensiveAssessmentReportAiService> aiServiceProvider) {
        super(confidencePolicy);
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.aiService = aiServiceProvider.getIfAvailable();
    }

    @Override
    public ComprehensiveAssessmentReportResult generateInsights(ComprehensiveAssessmentContext context) {
        ComprehensiveAssessmentContext safeContext = context == null ? new ComprehensiveAssessmentContext() : context;

        if (!properties.isEnabled() || aiService == null) {
            log.info("LangChain4j 未启用，评估报告洞察使用模板文字: empId={}", safeContext.getEmpId());
            return templateResult(safeContext);
        }

        return runWithFallback(() -> {
            String contextJson = objectMapper.writeValueAsString(buildPromptPayload(safeContext));
            ComprehensiveAssessmentReportModelResult modelResult = aiService.generate(contextJson);
            if (modelResult == null) {
                throw new IllegalStateException("评估报告洞察未返回结构化结果");
            }
            ComprehensiveAssessmentReportResult result = toResult(modelResult);
            result.setInsightSource(SOURCE_AI);
            String rawOutput = objectMapper.writeValueAsString(result);
            log.info("评估报告洞察生成完成: empId={}, sections={}",
                    safeContext.getEmpId(), result.getSectionInsights().size());
            return finalizeRun(result, List.of(), false, rawOutput);
        }, e -> {
            log.warn("LangChain4j 调用失败，评估报告洞察降级为模板文字: empId={}", safeContext.getEmpId(), e);
            return templateResult(safeContext);
        });
    }

    /* ===================== 输入组装（方案 A：四部分摘要，不投喂长原文） ===================== */

    private Map<String, Object> buildPromptPayload(ComprehensiveAssessmentContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empName", context.getEmpName());

        ResumeFact resume = context.getResume();
        payload.put("resume", resume == null
                ? map("available", false)
                : map("available", true,
                        "degree", resume.degree(),
                        "yearsOfWork", resume.yearsOfWork(),
                        "currentTitle", resume.currentTitle(),
                        "claimCount", resume.claimCount(),
                        "parseStatus", resume.status()));

        List<TestFact> tests = context.getTests();
        payload.put("aiTest", tests.isEmpty()
                ? map("available", false)
                : map("available", true,
                        "count", tests.size(),
                        "averageScore", averageScore(tests),
                        "items", tests.stream()
                                .map(t -> map("ability", t.abilityName(),
                                        "score", t.score(),
                                        "masteryLevel", t.masteryLevel()))
                                .toList()));

        InterviewFact interview = context.getInterview();
        payload.put("aiInterview", interview == null
                ? map("available", false)
                : map("available", true,
                        "overallScore", interview.overallScore(),
                        "observationCount", interview.observationCount(),
                        "riskSignals", interview.riskSignals(),
                        "conclusions", interview.conclusions()));

        List<DecisionFact> decisions = context.getDecisions();
        payload.put("finalLevels", decisions.isEmpty()
                ? map("available", false)
                : map("available", true,
                        "count", decisions.size(),
                        "items", decisions.stream()
                                .map(d -> map("ability", d.abilityName(),
                                        "finalLevel", d.finalLevel(),
                                        "confidence", d.finalConfidence(),
                                        "status", d.decisionStatus()))
                                .toList()));

        // 来源权重仅为展示口径，供模型理解各部分相对重要性（数值只读）
        payload.put("sourceWeights", context.getSourceWeights().stream()
                .map(w -> map("source", w.sourceLabel(), "weight", w.weight()))
                .toList());

        return payload;
    }

    private BigDecimal averageScore(List<TestFact> tests) {
        return tests.stream()
                .map(TestFact::score)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(tests.size()), 2, RoundingMode.HALF_UP);
    }

    /** 允许 null 值的有序 Map（Map.of 不接受 null） */
    private Map<String, Object> map(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    /* ===================== 模型结果转换 ===================== */

    private ComprehensiveAssessmentReportResult toResult(ComprehensiveAssessmentReportModelResult model) {
        ComprehensiveAssessmentReportResult result = new ComprehensiveAssessmentReportResult();
        result.setSectionInsights(model.getSectionInsights() == null
                ? List.of()
                : model.getSectionInsights().stream()
                        .filter(insight -> insight != null
                                && insight.getSection() != null
                                && VALID_SECTIONS.contains(insight.getSection()))
                        .map(insight -> new SectionInsight(insight.getSection(), insight.getInsight()))
                        .toList());
        result.setStrengths(clean(model.getStrengths()));
        result.setWeaknesses(clean(model.getWeaknesses()));
        result.setRiskSignals(clean(model.getRiskSignals()));
        result.setSuggestions(clean(model.getSuggestions()));
        result.setConclusion(model.getConclusion());
        result.setRecommendation(model.getRecommendation());
        return result;
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    /* ===================== 模板降级（AI 不可用时） ===================== */

    /**
     * 模板文字：只用既有事实拼装，**不产生任何新数值**。
     * 与 AI 产出的差别仅在于措辞，数值部分完全一致。
     */
    private ComprehensiveAssessmentReportResult templateResult(ComprehensiveAssessmentContext context) {
        ComprehensiveAssessmentReportResult result = new ComprehensiveAssessmentReportResult();
        result.setInsightSource(SOURCE_TEMPLATE);

        List<SectionInsight> sections = new ArrayList<>();
        if (context.getResume() != null) {
            ResumeFact resume = context.getResume();
            sections.add(new SectionInsight(SECTION_RESUME, String.format(
                    "简历解析：学历 %s、工作年限 %s、提取能力声明 %d 项。",
                    StringUtils.hasText(resume.degree()) ? resume.degree() : "未识别",
                    resume.yearsOfWork() == null ? "未识别" : resume.yearsOfWork() + " 年",
                    resume.claimCount())));
        }
        if (!context.getTests().isEmpty()) {
            List<TestFact> tests = context.getTests();
            sections.add(new SectionInsight(SECTION_AI_TEST, String.format(
                    "AI 测试：%d 次测试均分 %s，覆盖 %d 项能力。",
                    tests.size(), averageScore(tests).toPlainString(), tests.size())));
        }
        if (context.getInterview() != null) {
            InterviewFact interview = context.getInterview();
            sections.add(new SectionInsight(SECTION_AI_INTERVIEW, String.format(
                    "AI 面试：综合分 %s，观察项 %d 条，风险信号 %d 条。",
                    interview.overallScore() == null ? "暂无" : interview.overallScore().toPlainString(),
                    interview.observationCount(),
                    interview.riskSignals() == null ? 0 : interview.riskSignals().size())));
        }
        if (!context.getDecisions().isEmpty()) {
            sections.add(new SectionInsight(SECTION_FINAL_LEVEL, String.format(
                    "最终等级：%d 项能力已确认最终等级，详见「最终等级结论」部分。",
                    context.getDecisions().size())));
        }
        result.setSectionInsights(sections);
        result.setConclusion("本报告由模板生成（AI 洞察服务当前不可用），各部分数值与结论均来自既有评估链路。");
        result.setRecommendation("可在 AI 服务恢复后点击「重新生成报告」以获得更详细的解读。");

        // fallbackUsed=true → 置信度由 AgentRunConfidencePolicy 封顶（语义：洞察文字可信度）
        return finalizeRun(result, List.of(), true, null);
    }
}
