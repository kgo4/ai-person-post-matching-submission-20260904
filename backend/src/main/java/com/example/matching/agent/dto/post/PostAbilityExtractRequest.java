package com.example.matching.agent.dto.post;

import lombok.Data;

import java.util.List;

/**
 * 岗位能力提取请求DTO
 * <p>
 * 用于从指定来源材料中提取岗位能力声明。
 * 必须包含原始来源文本，Agent 才能从中抽取能力。
 *
 * @author system
 */
@Data
public class PostAbilityExtractRequest {

    /** 岗位ID */
    private Long postId;

    /**
     * 岗位名称，仅作为提取语义上下文使用。
     * <p>
     * 不参与证据偏移和证据定位；这两项始终以 sourceText/evidenceText 的原始材料为准。
     */
    private String postName;

    /** 来源类型：JD_IMPORT, POST_DESCRIPTION, POST_TEMPLATE, MARKET_JD, POST_EVOLUTION, COMPANY_POST_WEIGHT, MANUAL_POST_MODEL */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 来源文本：JD文本、岗位说明、市场数据等原始材料 */
    private String sourceText;

    /** 证据文本：与来源相关的证据内容 */
    private String evidenceText;

    /** 来源引用列表 */
    private List<String> sourceRefs;

    /** 分块索引（长文本分块时为 0..n-1，单块为 0） */
    private Integer chunkIndex;

    /** 分块在原文中的起始偏移（长文本分块时用于修正证据偏移） */
    private Integer chunkStartOffset;

    /** 上下文哈希 */
    private String contextHash;

    /** 上下文快照ID */
    private Long contextSnapshotId;

    /** 岗位已有能力列表（用于避免重复提取） */
    private List<ExistingRequirement> existingRequirements;

    /**
     * 已有岗位要求
     */
    @Data
    public static class ExistingRequirement {
        /** 能力标签ID */
        private Long abilityTagId;
        /** 能力名称 */
        private String abilityName;
        /** 要求等级 */
        private Integer requiredLevel;
        /** 权重 */
        private Integer weight;
        /** 是否核心 */
        private Boolean isCore;
    }

    /**
     * 验证请求是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return postId != null
                && sourceType != null && !sourceType.isBlank()
                && sourceText != null && !sourceText.isBlank();
    }
}
