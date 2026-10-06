package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新兴岗位发现结果DTO
 */
@Data
@Schema(description = "新兴岗位发现结果")
public class EmergingPostDiscoveryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 候选方向名称。
     * <p>
     * 【明确口径】名称**由能力社区本身生成**（取社区内提及最高的若干能力词拼成
     * 「X+Y 复合能力方向」），绝不取任何文档标题。
     * 原因：资料的标题在入库时可能来自上传文件名（`2026年AI人才需求白皮书.docx`），
     * 一旦被当作候选名，页面上会出现「以文件名命名的岗位」，既不可读也无法审核。
     * 真实岗位名请读 {@link #evidencePostNames}，资料标题请读 {@link #sourceTitles}。
     */
    @Schema(description = "候选方向名称，由能力社区自动生成（不取自文档标题）")
    private String candidateName;

    @Schema(description = "岗位描述")
    private String description;

    @Schema(description = "核心能力组合")
    private List<String> coreAbilities;

    /**
     * 证据总条数 = {@link #marketJdCount} + {@link #knowledgeDocumentCount}。
     * 前端不要直接渲染成「N 条 JD」—— 这两个来源是不同的东西，必须分列。
     */
    @Schema(description = "证据总条数（市场 JD 条数 + 资料份数）")
    private Integer frequency;

    @Schema(description = "支撑该候选的市场 JD 条数（来源 market_jd_data）")
    private Integer marketJdCount;

    @Schema(description = "支撑该候选的知识资料份数（来源已索引资料，非 JD）")
    private Integer knowledgeDocumentCount;

    @Schema(description = "证据中出现的真实岗位名（取自市场 JD 的岗位字段，不含资料标题）")
    private List<String> evidencePostNames;

    @Schema(description = "证据来源构成，如 MARKET_JD / OFFICIAL_POLICY / INDUSTRY_REPORT")
    private List<String> evidenceBreakdown;

    @Schema(description = "新颖度评分 0-100（越高表示越新颖）")
    private Integer noveltyScore;

    @Schema(description = "市场热度评分 0-100")
    private Integer marketHeatScore;

    @Schema(description = "相关行业领域")
    private List<String> relatedIndustries;

    @Schema(description = "发现来源摘要")
    private String sourceSummary;

    @Schema(description = "新兴岗位评分 0-100（综合评分）")
    private Integer emergenceScore;

    @Schema(description = "趋势增长评分 0-100")
    private Integer trendGrowthScore;

    @Schema(description = "来源多样性评分 0-100")
    private Integer sourceDiversityScore;

    @Schema(description = "候选证据覆盖的平台数量")
    private Integer sourcePlatformCount;

    @Schema(description = "候选证据覆盖的独立招聘主体数量，仅返回聚合数量")
    private Integer independentEmployerCount;

    @Schema(description = "独立招聘主体多样性评分 0-100")
    private Integer companyDiversityScore;

    @Schema(description = "语义新颖度评分 0-100")
    private Integer semanticNoveltyScore;

    @Schema(description = "内部需求评分 0-100")
    private Integer internalDemandScore;

    @Schema(description = "证据可信度评分 0-100")
    private Integer evidenceCredibilityScore;

    @Schema(description = "来源引用列表（统一sourceRef格式）")
    private List<String> sourceRefs;

    @Schema(description = "Harness 决策：PASS/REVIEW/BLOCK")
    private String harnessDecision;

    @Schema(description = "审核状态：PENDING/APPROVED/REJECTED")
    private String reviewStatus;

    @Schema(description = "关联的既有岗位ID列表")
    private List<Long> relatedExistingPostIds;

    @Schema(description = "差异化原因")
    private String differentiationReason;

    @Schema(description = "定义说明")
    private String definition;

    @Schema(description = "业务场景")
    private String businessScenario;

    @Schema(description = "核心任务")
    private List<String> coreTasks;

    @Schema(description = "发现模式：OBSERVATION/CANDIDATE/DISCOVERY")
    private String discoveryMode;

    @Schema(description = "技能社区凝聚度评分 0-100")
    private Integer cohesionScore;

    @Schema(description = "建议流转：POST_EVOLUTION/EMERGING_POST_REVIEW")
    private String recommendedAction;

    @Schema(description = "来源类型列表")
    private List<String> sourceTypes;

    @Schema(description = "原始岗位称谓列表")
    private List<String> rawTitles;

    @Schema(description = "来源材料标题列表")
    private List<String> sourceTitles;

    @Schema(description = "证据摘要")
    private String evidenceSummary;

    @Schema(description = "风险标记")
    private List<String> riskFlags;

    @Schema(description = "是否完成政策/官方来源验证")
    private Boolean policyValidated;

    /**
     * 市场洞察数据
     */
    @Data
    public static class MarketInsight implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "热门能力标签TOP10")
        private List<HotAbility> hotAbilities;

        @Schema(description = "新兴技术趋势")
        private List<TechTrend> techTrends;

        @Schema(description = "数据更新时间")
        private String lastUpdated;

        @Schema(description = "分析的JD文档数量")
        private Integer analyzedJdCount;

        @Schema(description = "当前已形成的候选岗位方向总数")
        private Integer candidateCount;

        @Schema(description = "有效 JD 覆盖的来源平台数量")
        private Integer sourcePlatformCount;

        @Schema(description = "有效 JD 覆盖的独立招聘主体数量，仅返回聚合数量")
        private Integer independentEmployerCount;

        @Schema(description = "来源平台多样性评分 0-100")
        private Integer sourceDiversityScore;

        @Schema(description = "独立招聘主体多样性评分 0-100")
        private Integer companyDiversityScore;

        @Schema(description = "精确或近似去重后被跳过的 JD 数量")
        private Integer deduplicatedCount;

        @Schema(description = "按市场噪声规则过滤的 JD 数量")
        private Integer noiseFilteredCount;

        @Schema(description = "已索引且参与解析的知识资料数量")
        private Integer indexedDocumentCount;

        @Schema(description = "已形成技能文档的资料与 JD 样本总数")
        private Integer matchedDocumentCount;

        @Schema(description = "本次解析使用的岗位能力词表规模")
        private Integer vocabularySize;

        @Schema(description = "词表来源：POST_ABILITY_MODEL / ABILITY_TAG / SEED")
        private String vocabularySource;

        @Schema(description = "从资料中识别出的能力词数量")
        private Integer recognizedAbilityCount;

        @Schema(description = "面向使用者的解析诊断说明；无候选时说明具体原因")
        private String diagnosticMessage;
    }

    /**
     * 热门能力标签
     */
    @Data
    public static class HotAbility implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "能力名称")
        private String abilityName;

        @Schema(description = "提及次数")
        private Integer mentionCount;

        @Schema(description = "增长率（相比上期）")
        private Integer growthRate;

        @Schema(description = "相关岗位数量")
        private Integer relatedPostCount;
    }

    /**
     * 技术趋势
     */
    @Data
    public static class TechTrend implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "技术名称")
        private String techName;

        @Schema(description = "趋势方向：RISING/STABLE/DECLINING")
        private String trendDirection;

        @Schema(description = "热度评分 0-100")
        private Integer heatScore;

        @Schema(description = "典型应用场景")
        private String typicalScenario;
    }
}
