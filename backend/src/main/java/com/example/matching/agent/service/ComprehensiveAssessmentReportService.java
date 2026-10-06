package com.example.matching.agent.service;

import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;

/**
 * 全方位评估报告的 AI 洞察服务。
 *
 * <p>输入是**已聚合好的四部分事实包**（简历 / AI 测试 / AI 面试 / 最终等级 + 来源权重），
 * 输出是**纯文字洞察**。本服务不产生、不修改任何数值，
 * 也不重新执行 AI 测试、AI 面试或等级确认。
 *
 * <p>LLM 不可用时自动降级为模板文字，数值部分不受任何影响。
 */
public interface ComprehensiveAssessmentReportService {

    String SECTION_RESUME = "RESUME";
    String SECTION_AI_TEST = "AI_TEST";
    String SECTION_AI_INTERVIEW = "AI_INTERVIEW";
    String SECTION_FINAL_LEVEL = "FINAL_LEVEL";

    String SOURCE_AI = "AI";
    String SOURCE_TEMPLATE = "TEMPLATE";

    /**
     * 生成洞察文字。
     *
     * @param context 已聚合的事实包（由 {@code ComprehensiveAssessmentContextAssembler} 产出）
     * @return 洞察文字结果；调用方据 {@code fallbackUsed} / {@code insightSource} 判断是否降级
     */
    ComprehensiveAssessmentReportResult generateInsights(ComprehensiveAssessmentContext context);
}
