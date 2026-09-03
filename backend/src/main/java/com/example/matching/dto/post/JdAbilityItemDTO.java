package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * JD分析结果中的单条能力项DTO
 */
@Data
@Schema(description = "JD分析结果中的单条能力项，包含AI建议的能力标签信息及与已有标签的匹配状态")
public class JdAbilityItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "AI建议的能力标签名称", example = "Java并发编程")
    private String suggestedName;

    @Schema(description = "技能点所属技术栈，如 Java、Spring、MySQL、Redis")
    private String techStack;

    @Schema(description = "AI建议的标签分类：TECHNICAL-技术能力，SOFT-软技能，BUSINESS-业务能力", example = "TECHNICAL")
    private String tagCategory;

    @Schema(description = "建议的最低要求等级：1-入门，2-熟悉，3-掌握，4-精通，5-专家", example = "3")
    private Integer minRequiredLevel;

    @Schema(description = "建议的权重占比（所有能力项权重之和应为100）", example = "15.00")
    private BigDecimal weight;

    @Schema(description = "是否核心项：0-否，1-是", example = "1")
    private Integer isCore;

    @Schema(description = "是否必填：0-否，1-是", example = "1")
    private Integer isRequired;

    @Schema(description = "AI的推理依据，说明从JD中哪段描述得出此能力要求")
    private String reasoning;

    // ===== 匹配状态字段 =====

    @Schema(description = "匹配状态：MATCHED-精确匹配已有标签，SIMILAR-疑似相似标签，NEW-需创建新标签")
    private String matchStatus;

    @Schema(description = "匹配到的已有标签ID（仅MATCHED/SIMILAR状态有值）")
    private Long matchedTagId;

    @Schema(description = "匹配到的已有标签名称（仅MATCHED/SIMILAR状态有值）")
    private String matchedTagName;

    @Schema(description = "与已有标签的相似度分数（0-1），仅SIMILAR状态有值")
    private Double similarityScore;

    // ===== 证据字段（从验证后的 PostAbilityClaim 原样复制） =====

    @Schema(description = "AI置信度：0-100", example = "85.00")
    private BigDecimal confidenceScore;

    @Schema(description = "证据文本：原文中支持该能力主张的片段，作为 Harness 证据载荷，不得使用 reasoning 代替")
    private String evidenceText;

    @Schema(description = "证据文本内的能力锚点，新提取结果必须可在 evidenceText 中定位")
    private String evidenceAnchor;

    @Schema(description = "能力类型：TECHNICAL、BUSINESS、SOFT 或 QUALIFICATION")
    private String abilityType;

    @Schema(description = "证据在原文中的起始偏移（可选，服务端核验）")
    private Integer evidenceStart;

    @Schema(description = "证据在原文中的结束偏移（可选，服务端核验）")
    private Integer evidenceEnd;

    @Schema(description = "来源引用列表，例如 source:MARKET_JD:<jdId>")
    private List<String> sourceRefs;
}
