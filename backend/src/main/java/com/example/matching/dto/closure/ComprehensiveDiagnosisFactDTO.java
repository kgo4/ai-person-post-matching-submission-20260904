package com.example.matching.dto.closure;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 综合差距诊断 — 事实诊断包
 * <p>
 * 由系统构造，包含所有可量化的事实数据。
 * AI 只能基于这个事实包和 RAG 检索片段输出诊断，不能自由发挥。
 *
 * @author system
 */
@Data
public class ComprehensiveDiagnosisFactDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 匹配记录ID */
    private Long recordId;

    /** 员工ID */
    private Long empId;

    /** 员工姓名 */
    private String empName;

    /** 岗位ID */
    private Long postId;

    /** 岗位名称 */
    private String postName;

    /** 岗位级别 */
    private String postLevel;

    /** 多维度分数 */
    private ScoreSnapshot scores;

    /** 硬条件检查结果 */
    private List<HardConditionFact> hardConditions = new ArrayList<>();

    /** 能力等级差距 */
    private List<AbilityGapFact> abilityGaps = new ArrayList<>();

    /** 证据风险 */
    private List<EvidenceRiskFact> evidenceRisks = new ArrayList<>();

    /** 语义匹配信号 */
    private SemanticSignal semanticSignals;

    /** 反馈信号 */
    private FeedbackSignal feedbackSignals;

    /** 可用学习资源 */
    private List<LearningResourceFact> availableLearningResources = new ArrayList<>();

    /**
     * 多维度分数快照
     */
    @Data
    public static class ScoreSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 最终匹配分 */
        private BigDecimal finalMatchScore;

        /** 能力模型匹配分 */
        private BigDecimal abilityScore;

        /** 语义匹配分 */
        private BigDecimal semanticScore;

        /** 证据可信度分 */
        private BigDecimal evidenceScore;

        /** AI深度分 */
        private BigDecimal llmScore;

        /** 模型质量系数 */
        private BigDecimal modelQualityScore;

        /** 硬条件分 */
        private BigDecimal hardConditionScore;

        /** 反馈校准值 */
        private BigDecimal feedbackAdjustment;

        /** 筛选级别 */
        private Integer screeningLevel;

        /** 匹配状态 */
        private Integer matchStatus;
    }

    /**
     * 硬条件事实
     */
    @Data
    public static class HardConditionFact implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 条件字段 */
        private String field;

        /** 条件标签（中文） */
        private String label;

        /** 运算符 */
        private String operator;

        /** 期望值 */
        private String expectedValue;

        /** 实际值 */
        private String actualValue;

        /** 是否通过 */
        private boolean passed;

        /** 来源 */
        private String source;
    }

    /**
     * 能力差距事实
     */
    @Data
    public static class AbilityGapFact implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 标签ID */
        private Long tagId;

        /** 能力名称 */
        private String abilityName;

        /** 当前等级 */
        private BigDecimal currentLevel;

        /** 要求等级 */
        private Integer requiredLevel;

        /** 是否核心能力 */
        private boolean core;

        /** 是否必备能力 */
        private boolean required;

        /** 匹配系数 */
        private BigDecimal matchCoefficient;

        /** 相似度分 */
        private BigDecimal similarityScore;

        /** 是否弱证据 */
        private boolean weakEvidence;

        /** 差距原因 */
        private String reason;

        /** 证据来源列表 */
        private List<EvidenceSource> evidenceSources = new ArrayList<>();
    }

    /**
     * 证据来源
     */
    @Data
    public static class EvidenceSource implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 来源类型：RESUME_PARSE, AI_TEST, INTERVIEW, LEARNING_OUTCOME, MANUAL_CONFIRM */
        private String source;

        /** 等级 */
        private Integer level;

        /** 可信度 */
        private BigDecimal credibility;

        /** 时间因子 */
        private BigDecimal timeFactor;
    }

    /**
     * 证据风险事实
     */
    @Data
    public static class EvidenceRiskFact implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 能力名称 */
        private String abilityName;

        /** 风险类型：WEAK_SOURCE, SINGLE_SOURCE, OUTDATED, LOW_CREDIBILITY */
        private String riskType;

        /** 风险描述 */
        private String description;

        /** 来源数量 */
        private int sourceCount;

        /** 主要来源类型 */
        private String primarySourceType;

        /** 可信度 */
        private BigDecimal credibility;
    }

    /**
     * 语义匹配信号
     */
    @Data
    public static class SemanticSignal implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 向量语义分 */
        private BigDecimal vectorScore;

        /** 整人×整岗语义分 */
        private BigDecimal profileSemanticScore;

        /** 是否有向量数据 */
        private boolean vectorAvailable;

        /** 员工画像文本摘要 */
        private String employeeProfileSummary;

        /** 岗位描述摘要 */
        private String postDescriptionSummary;
    }

    /**
     * 反馈信号
     */
    @Data
    public static class FeedbackSignal implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 人工反馈校准值 */
        private BigDecimal feedbackCalibration;

        /** 审批状态 */
        private Integer approvalStatus;

        /** 人工备注 */
        private String manualRemark;

        /** 结构化反馈原因 */
        private List<String> feedbackReasons = new ArrayList<>();
    }

    /**
     * 学习资源事实
     */
    @Data
    public static class LearningResourceFact implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 关联能力名称 */
        private String abilityName;

        /** 资源标题 */
        private String title;

        /** 资源类型 */
        private String resourceType;

        /** 难度等级 */
        private Integer difficultyLevel;

        /** 资源URL */
        private String url;
    }
}
