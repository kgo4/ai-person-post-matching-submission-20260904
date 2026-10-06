package com.example.matching.dto.harness;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Harness 声明 DTO
 * <p>
 * 用于向 Harness 提交能力声明进行可信校验。
 * 所有字段都应尽可能填写，以提高校验准确性。
 *
 * @author system
 */
@Data
public class AiHarnessClaimDTO {

    /** Stable aggregate-assessment item identifier for batch correlation. */
    private Long claimGroupId;

    /** 场景：PERSON_ABILITY, POST_ABILITY, MATCHING_ANALYSIS, LEARNING_SUGGESTION, POST_EVOLUTION 等 */
    private String scenario;

    /** 声明类型：EMP_ABILITY, POST_ABILITY, ABILITY_TAG, LEARNING_SUGGESTION, POST_WEIGHT, POST_EVOLUTION_ITEM 等 */
    private String claimType;

    /** Business change action, for example ADD_ABILITY or REMOVE_ABILITY. */
    private String changeType;

    /** 声明文本：能力名称或描述 */
    private String claimText;

    /** 声明载荷JSON：完整的声明数据 */
    private String claimPayloadJson;

    /** 来源类型：RESUME_PARSE, AI_TEST, VIDEO_INTERVIEW, PMS_ANALYSIS, JD_IMPORT 等 */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 证据文本：原始证据内容 */
    private String evidenceText;

    /** 来源引用列表：标准格式 fact:EMP_ABILITY:123 */
    private List<String> sourceRefs = new ArrayList<>();

    /** RAG 分块ID列表 */
    private List<Long> ragChunkIds = new ArrayList<>();

    /** 匹配到的正式标签ID */
    private Long matchedTagId;

    /** 相似标签ID */
    private Long similarTagId;

    /** 置信度：0-100 */
    private Double confidence;

    /** 上下文哈希：用于追溯上下文 */
    private String contextHash;

    /** 上下文快照ID */
    private Long contextSnapshotId;

    /** 业务目标类型：EMP_ABILITY, POST_ABILITY_MODEL, MATCHING_RECORD 等 */
    private String businessTargetType;

    /** 业务目标ID */
    private Long businessTargetId;

    /**
     * 验证声明是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return claimText != null && !claimText.isBlank();
    }

    /**
     * 是否有证据
     *
     * @return 是否有证据
     */
    public boolean hasEvidence() {
        return evidenceText != null && !evidenceText.isBlank();
    }

    /**
     * 是否有来源引用
     *
     * @return 是否有来源引用
     */
    public boolean hasSourceRefs() {
        return sourceRefs != null && !sourceRefs.isEmpty();
    }

    /**
     * 是否有RAG分块
     *
     * @return 是否有RAG分块
     */
    public boolean hasRagChunks() {
        return ragChunkIds != null && !ragChunkIds.isEmpty();
    }

    /**
     * 是否已匹配正式标签
     *
     * @return 是否匹配
     */
    public boolean hasMatchedTag() {
        return matchedTagId != null;
    }

    /**
     * 是否有相似标签
     *
     * @return 是否有相似标签
     */
    public boolean hasSimilarTag() {
        return similarTagId != null;
    }
}
