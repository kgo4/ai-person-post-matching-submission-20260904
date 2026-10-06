package com.example.matching.agent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 全方位评估报告的洞察生成结果（业务层）。
 *
 * <p>只承载**文字**：各部分解读 + 优势/短板/风险信号/建议/结论。
 * 报告中的全部数值来自既有链路，不经过本结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComprehensiveAssessmentReportResult extends AgentRunResult {

    /** 各部分解读 */
    private List<SectionInsight> sectionInsights = new ArrayList<>();

    private List<String> strengths = new ArrayList<>();

    private List<String> weaknesses = new ArrayList<>();

    private List<String> riskSignals = new ArrayList<>();

    private List<String> suggestions = new ArrayList<>();

    /** 综合结论 */
    private String conclusion;

    /** 建议 */
    private String recommendation;

    /** 洞察文字来源：AI / TEMPLATE（模板降级时） */
    private String insightSource;

    /** 单部分解读 */
    public record SectionInsight(String section, String insight) {
    }
}
