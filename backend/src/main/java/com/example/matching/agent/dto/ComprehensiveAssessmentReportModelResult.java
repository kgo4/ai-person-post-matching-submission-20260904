package com.example.matching.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 全方位评估报告「洞察文字」的模型输出契约。
 *
 * <p><b>本契约刻意不含任何分数字段</b> —— 报告中的全部数值（测试分、面试分、最终等级、置信度、
 * 来源权重）均由既有链路产出，AI 只负责解读。<b>从数据结构上杜绝模型改分或重算</b>，
 * 而不是仅靠 prompt 约束。
 *
 * <p>字段一律使用具体类型（LangChain4j 0.35.0 无法为嵌套泛型集合生成 JSON schema）。
 */
@Data
public class ComprehensiveAssessmentReportModelResult {

    /** 各部分解读：每个有数据的部分一条 */
    private List<SectionInsight> sectionInsights;

    private List<String> strengths;

    private List<String> weaknesses;

    private List<String> riskSignals;

    private List<String> suggestions;

    /** 综合结论（文字） */
    private String conclusion;

    /** 建议（文字） */
    private String recommendation;

    /** 单部分解读。section 取值：RESUME / AI_TEST / AI_INTERVIEW / FINAL_LEVEL */
    @Data
    public static class SectionInsight {
        /** RESUME / AI_TEST / AI_INTERVIEW / FINAL_LEVEL */
        private String section;
        /** 该部分的解读文字（不得包含自行计算的分数） */
        private String insight;
    }
}
