package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位模型未匹配能力标签响应 DTO（M-07）
 * <p>
 * AI 提取能力无法匹配已有标签时的展示项，供管理员绑定或忽略。
 */
@Data
@Builder
@Schema(description = "岗位模型未匹配能力标签")
public class UnmatchedAbilityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "未匹配记录ID") private Long id;
    @Schema(description = "岗位模型版本ID") private Long versionId;
    @Schema(description = "AI 提取的能力名称") private String abilityName;
    @Schema(description = "归一化后的能力名称") private String normalizedAbilityName;
    @Schema(description = "建议最低要求等级 1-5") private Integer minRequiredLevel;
    @Schema(description = "建议权重 0-100") private BigDecimal weight;
    @Schema(description = "是否必需 0-否 1-是") private Integer isRequired;
    @Schema(description = "是否核心 0-否 1-是") private Integer isCore;
    @Schema(description = "AI 推理说明") private String reasoning;
    @Schema(description = "未匹配原因: MATCHED_TAG_ID_NOT_FOUND/TAG_NAME_NOT_FOUND/TAG_DISABLED/TAG_NAME_AMBIGUOUS") private String reason;
    @Schema(description = "状态: PENDING/TAG_BOUND/IGNORED") private String status;
    @Schema(description = "推荐标签ID") private Long recommendedTagId;
    @Schema(description = "推荐标签名称") private String recommendedTagName;
    @Schema(description = "是否已创建标签候选（candidateId 不为空即已创建）") private Long candidateId;
    @Schema(description = "创建时间") private LocalDateTime createdTime;
}
