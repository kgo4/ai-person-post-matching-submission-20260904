package com.example.matching.dto.assessment.report;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 全方位评估报告的事实包（四类数据 + 能力画像）。
 *
 * <p>由 {@code ComprehensiveAssessmentContextAssembler} 按 empId 聚合产出，只包含
 * **已经落库的结构化事实**，不做任何 AI 调用、推断或重新打分。两个消费方：
 * <ul>
 *   <li>报告组装 —— 按此渲染各部分内容（简历证据 / AI 测试 / AI 面试 / 最终等级 / 来源权重）；</li>
 *   <li>AI 洞察 agent —— 按此生成洞察文字，<b>不打分、不改分</b>。</li>
 * </ul>
 *
 * <p>字段刻意保持「扁平 + 摘要」：既够规则算分，也够模型理解，避免投喂长原文。
 */
@Data
public class ComprehensiveAssessmentContext {

    private Long empId;
    private String empName;
    /** 关联评估工作流（可选；报告按员工聚合，不强制绑定某个 workflow） */
    private Long workflowId;
    /** 目标岗位（可选；无岗位时不计算「岗位匹配度」维度） */
    private Long postId;
    private String postName;

    /** 简历解析事实；员工未上传简历时为 null */
    private ResumeFact resume;
    /** AI 测试事实（可为多条） */
    private List<TestFact> tests = new ArrayList<>();
    /** AI 面试事实；无面试记录时为 null */
    private InterviewFact interview;
    /** 人员最终等级决策（同一能力只保留最新一条） */
    private List<DecisionFact> decisions = new ArrayList<>();
    /** 已确立能力（能力画像） */
    private List<AbilityFact> abilities = new ArrayList<>();
    /**
     * 各部分（来源）权重，实时读自既有「来源权重配置」（`source_weight_config`）。
     * <p><b>本报告不新建权重体系</b>：这里只是把既有配置读出来供展示，
     * 证据加权与最终等级的判定口径仍由 {@code SourceWeightResolver} 唯一负责。
     */
    private List<SourceWeightFact> sourceWeights = new ArrayList<>();

    /** 简历解析摘要。学历/工作年限/当前职位来自 aiAnalysisResult 的 BasicInfo。 */
    public record ResumeFact(
            String degree,
            Integer yearsOfWork,
            String currentTitle,
            /** 简历中提取到的能力声明条数 */
            int claimCount,
            /** 解析状态：SUCCESS/FAILED/… 便于判断该维度是否可用 */
            String status) {
    }

    /** 单次 AI 测试摘要。 */
    public record TestFact(
            Long tagId,
            String abilityName,
            BigDecimal score,
            Integer masteryLevel) {
    }

    /** 面试事实。只取已落库数据，不重新触发面试 AI 报告生成。 */
    public record InterviewFact(
            BigDecimal overallScore,
            /** 观察项条数（interview_ability_observation） */
            int observationCount,
            /** 已落库的风险信号（去重后） */
            List<String> riskSignals,
            /** 观察结论摘要 */
            List<String> conclusions) {
    }

    /** 单项能力的最终等级决策。 */
    public record DecisionFact(
            Long tagId,
            String abilityName,
            Integer finalLevel,
            Integer finalConfidence,
            String decisionStatus) {
    }

    /** 已确立能力（融合进画像的结果）。 */
    public record AbilityFact(
            Long tagId,
            String abilityName,
            String category,
            Integer masteryLevel) {
    }

    /** 单个来源的权重配置（只读展示用，来源定义见 AbilitySourceType）。 */
    public record SourceWeightFact(
            /** 来源标识：RESUME_PARSE / AI_TEST / AI_INTERVIEW */
            String sourceType,
            /** 来源中文名，如「简历解析」 */
            String sourceLabel,
            /** 该来源的有效权重 */
            BigDecimal weight) {
    }
}
