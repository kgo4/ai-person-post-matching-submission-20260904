package com.example.matching.agent.dto.person;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 人员能力声明DTO
 * <p>
 * 统一输出格式，所有人员能力提取结果都转换为该DTO。
 * 每条能力声明都必须包含证据和来源引用。
 *
 * @author system
 */
@Data
public class PersonAbilityClaim {

    /** 员工ID */
    private Long empId;

    /** 统一来源类型，见 AbilitySourceType */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 能力名称（原始提取） */
    @JsonProperty(required = true)
    private String abilityName;

    /** 标准化能力名称 */
    private String normalizedAbilityName;

    /** 匹配到的正式标签ID */
    private Long abilityTagId;

    /** 相似标签ID（当无法匹配正式标签时） */
    private Long similarTagId;

    /** 掌握程度：1-5 */
    @JsonProperty(required = true)
    private Integer masteryLevel;

    /** 置信度：0-100 */
    private BigDecimal confidenceScore;

    /** 证据文本 */
    @JsonProperty(required = true)
    private String evidenceText;

    /** 证据在原文中的起始偏移（可选，服务端核验） */
    private Integer evidenceStart;

    /** 证据在原文中的结束偏移（可选，服务端核验） */
    private Integer evidenceEnd;

    /** 提取原因 */
    private String extractReason;

    /** 来源引用列表 */
    private List<String> sourceRefs;

    /** 验证结果诊断 */
    private EvidenceValidationResult validationResult;

    /** 原始模型输出 */
    private String rawModelOutput;

    /**
     * 验证声明是否有效
     *
     * @return 是否有效
     */
    @JsonIgnore
    public boolean isValid() {
        return empId != null
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
}
