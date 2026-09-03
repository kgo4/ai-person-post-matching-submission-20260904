package com.example.matching.agent.dto.post;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位能力声明DTO
 * <p>
 * 统一输出格式，所有岗位能力提取结果都转换为该DTO。
 * 每条岗位能力声明都必须包含证据和来源引用。
 *
 * @author system
 */
@Data
public class PostAbilityClaim {

    /** 岗位ID */
    private Long postId;

    /** 来源类型：JD_IMPORT, POST_DESCRIPTION, POST_TEMPLATE, MARKET_JD, POST_EVOLUTION, COMPANY_POST_WEIGHT, MANUAL_POST_MODEL */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 能力名称（原始提取） */
    private String abilityName;

    /** 标准化能力名称 */
    private String normalizedAbilityName;

    /** 技能点所属技术栈，如 Java、Spring、MySQL；标签库关联为可选信息。 */
    private String techStack;

    /** 匹配到的正式标签ID */
    private Long abilityTagId;

    /** 相似标签ID（当无法匹配正式标签时） */
    private Long similarTagId;

    /** 候选标签ID（统一准入创建候选时回填，用于追溯） */
    private Long candidateId;

    /** 要求等级：1-5 */
    private Integer requiredLevel;

    /** 权重：0-1 */
    private BigDecimal weight;

    /** 是否核心能力 */
    private Boolean isCore;

    /** 是否必填能力 */
    private Boolean isRequired;

    /** 置信度：0-100 */
    private BigDecimal confidenceScore;

    /** 证据文本 */
    private String evidenceText;

    /** 证据文本内可定位的能力锚点；新提示词必须提供，历史结果允许为空。 */
    private String evidenceAnchor;

    /** 能力类型：TECHNICAL、BUSINESS、SOFT 或 QUALIFICATION。 */
    private String abilityType;

    /** 证据在原文中的起始偏移（可选，服务端核验） */
    private Integer evidenceStart;

    /** 证据在原文中的结束偏移（可选，服务端核验） */
    private Integer evidenceEnd;

    /** 提取原因 */
    private String extractReason;

    /** 来源引用列表 */
    private List<String> sourceRefs;

    /** 原始模型输出 */
    private String rawModelOutput;

    /**
     * 验证声明是否有效
     *
     * @return 是否有效
     */
    @JsonIgnore
    public boolean isValid() {
        return postId != null
                && abilityName != null && !abilityName.isBlank()
                && evidenceText != null && !evidenceText.isBlank()
                && sourceRefs != null && !sourceRefs.isEmpty();
    }

    @JsonIgnore
    public boolean hasMatchedTag() {
        return abilityTagId != null;
    }

    @JsonIgnore
    public boolean hasSimilarTag() {
        return similarTagId != null;
    }

    /**
     * 获取标准sourceRef
     *
     * @return 标准sourceRef格式
     */
    @JsonIgnore
    public String getStandardSourceRef() {
        if (sourceRefId == null) {
            return null;
        }
        return "source:" + sourceType + ":" + sourceRefId;
    }

    @JsonIgnore
    public Integer getRequiredLevelOrDefault() {
        return requiredLevel != null ? requiredLevel : 3;
    }

    @JsonIgnore
    public BigDecimal getWeightOrDefault() {
        return weight != null ? weight : new BigDecimal("5");
    }

    @JsonIgnore
    public boolean isCoreAbility() {
        return isCore != null && isCore;
    }

    @JsonIgnore
    public boolean isRequiredAbility() {
        return isRequired != null && isRequired;
    }
}
