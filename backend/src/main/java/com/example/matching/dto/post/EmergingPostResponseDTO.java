package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新兴岗位定义响应DTO
 */
@Data
@Schema(description = "新兴岗位定义响应")
public class EmergingPostResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "创建的岗位ID（仅createPost=true时返回）")
    private Long createdPostId;

    @Schema(description = "推荐的岗位原型列表")
    private List<PostPrototypeVO> recommendedPrototypes;

    @Schema(description = "AI推荐的能力项列表")
    private List<JdAbilityItemDTO> recommendedAbilities;

    @Schema(description = "AI生成的岗位描述建议")
    private String suggestedDescription;

    @Schema(description = "推荐理由")
    private String reasoning;

    // ===== 结构化岗位定义 =====

    @Schema(description = "核心职责列表")
    private List<String> coreResponsibilities;

    @Schema(description = "必备技能列表")
    private List<String> requiredSkills;

    @Schema(description = "加分技能列表")
    private List<String> bonusSkills;

    @Schema(description = "典型行业应用场景列表")
    private List<String> industryScenarios;

    // ===== 交叉验证 =====

    @Schema(description = "交叉验证摘要")
    private CrossValidationSummary crossValidation;

    @Schema(description = "数据源概览")
    private List<String> dataSources;

    // ===== 质量检测 =====

    @Schema(description = "JD质量检测报告")
    private JdQualityReport qualityReport;

    /**
     * 交叉验证摘要
     */
    @lombok.Data
    public static class CrossValidationSummary implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "覆盖的数据源种类数")
        private Integer sourceDiversity;

        @Schema(description = "一致性评分 0-100")
        private Integer consistencyScore;

        @Schema(description = "各数据源覆盖情况")
        private List<SourceBreakdownItem> sourceBreakdown;

        @Schema(description = "时效性等级：FRESH/RECENT/STALE")
        private String freshnessLevel;

        @Schema(description = "最新采集时间")
        private String lastCollectedAt;
    }

    /**
     * 数据源覆盖明细
     */
    @lombok.Data
    public static class SourceBreakdownItem implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "数据源类型")
        private String sourceType;

        @Schema(description = "数据源标签")
        private String label;

        @Schema(description = "该数据源覆盖的能力数量")
        private Integer abilityCount;
    }
}
