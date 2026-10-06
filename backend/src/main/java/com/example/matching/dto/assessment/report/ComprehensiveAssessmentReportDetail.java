package com.example.matching.dto.assessment.report;

import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext.SourceWeightFact;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工全方位评估报告的**完整视图**（前端一次请求即可渲染整份报告）。
 *
 * <p>由 {@code AssessmentReportServiceImpl.getReportDetail(workflowId)} 组装：
 * <ul>
 *   <li>四部分数据直接透传已落库的 JSON 字符串（简历证据 / AI 测试 / AI 面试 / 等级与聚合结论），
 *       前端复用既有渲染器解析，避免在服务端做无意义的二次反序列化；</li>
 *   <li>AI 综合洞察（{@code insight*} 字段）—— **只有文字，没有任何分数字段**；</li>
 *   <li>来源权重实时读自既有「来源权重配置」，仅用于展示各部分占比。</li>
 * </ul>
 *
 * <p><b>注意</b>：本视图刻意**不包含** {@code overallScore} / {@code postMatchScore}
 * —— 按产品口径，报告顶部不展示汇总分（数据库字段仍由面试侧写入，只是不呈现）。
 */
@Data
public class ComprehensiveAssessmentReportDetail {

    /** 报告是否可阅读；false 时看 {@link #unavailableReason}（前端据此显示原因，而非报错） */
    private boolean available;

    /** 不可阅读原因（available=false 时非空） */
    private String unavailableReason;

    private Long workflowId;
    private Long empId;
    private String empName;
    private Long postId;
    private String postName;

    /** 报告状态：READY / FAILED */
    private String reportStatus;

    private String completedAt;
    private String generatedAt;

    /* ===================== ① 简历提取证据 ===================== */
    private String resumeSummaryJson;

    /* ===================== ② AI 测试结果 ===================== */
    private String testSummaryJson;

    /* ===================== ③ AI 面试报告（原独立面试报告的全部内容） ===================== */
    private String interviewSummaryJson;

    /** 面试会话 ID：报告内「查看面试过程记录」入口需要它跳转 */
    private Long interviewSessionId;

    /* ===================== ④ 聚合审核 / 最终等级结论 ===================== */
    private String aggregateSummaryJson;
    private String levelSummaryJson;

    /** 面试侧结论 / 建议（历史字段，保留展示；与本报告的 AI 结论区分） */
    private String conclusion;
    private String recommendation;

    /* ===================== ⑤ AI 综合洞察（无分数） ===================== */

    /** 洞察是否已生成；false 时前端提示「可点击重新生成」 */
    private boolean insightAvailable;

    private String sectionInsightsJson;
    private String strengthsJson;
    private String weaknessesJson;
    private String riskSignalsJson;
    private String suggestionsJson;
    private String aiConclusion;

    /** AI / TEMPLATE —— TEMPLATE 表示洞察文字由模板生成（数值不受影响） */
    private String insightSource;

    private String insightGeneratedAt;

    /** 洞察文字可信度 0-100（语义为「文字可信度」，不是分数可信度） */
    private Integer insightConfidence;

    /** 便捷标志：洞察文字是否降级为模板（前端只在洞察区做标注） */
    private boolean insightFallbackUsed;

    /* ===================== 各部分来源权重（只读展示） ===================== */
    private List<SourceWeightFact> sourceWeights = new ArrayList<>();
}
