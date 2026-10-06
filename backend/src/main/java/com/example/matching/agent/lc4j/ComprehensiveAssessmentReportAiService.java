package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.ComprehensiveAssessmentReportModelResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 全方位评估报告的 AI 洞察生成接口。
 *
 * <p>只生成文字：四部分（简历 / AI 测试 / AI 面试 / 最终等级）的解读，以及优势、短板、
 * 风险信号、改进建议、综合结论。**不产生任何分数** —— 输出契约里没有分数字段。
 */
public interface ComprehensiveAssessmentReportAiService {

    @SystemMessage(fromResource = "ai/prompt/comprehensive-assessment-report-system.txt")
    @UserMessage("""
        Write the insight section for this employee assessment report.

        The four evidence sections and their FINAL numbers are supplied below as JSON:

        {{context}}

        Return strict JSON matching the system output schema.
        Write text only. Do not output, recalculate, or modify any number.
        """)
    ComprehensiveAssessmentReportModelResult generate(@V("context") String context);
}
