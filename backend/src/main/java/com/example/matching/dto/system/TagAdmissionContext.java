package com.example.matching.dto.system;

import lombok.Builder;
import lombok.Data;

/**
 * 标签准入上下文
 * <p>
 * 业务服务提出能力主张时，将所有相关信息封装到此对象中，
 * 由 AbilityTagService 统一决策是否准入。
 *
 * @author system
 */
@Data
@Builder
public class TagAdmissionContext {

    /**
     * 能力标签名称（原始名称）
     */
    private String tagName;

    /**
     * 规范化后的标签名称（由 AbilityTagNormalizer 生成）
     */
    private String normalizedTagName;

    /**
     * 标签分类：TECHNICAL / SOFT / BUSINESS
     */
    private String tagCategory;

    /**
     * 领域分类：AI / BIG_DATA / IOT / SMART_SYSTEM / CLOUD / BLOCKCHAIN / GENERAL
     */
    private String domain;

    /**
     * 来源类型：RESUME_PARSE / AI_TEST / VIDEO_INTERVIEW / PMS_ANALYSIS / JD_IMPORT / POST_EVOLUTION
     */
    private String sourceType;

    /**
     * 来源记录ID（如简历解析记录ID、测试记录ID等）
     */
    private Long sourceRefId;

    /**
     * 关联员工ID
     */
    private Long empId;

    /**
     * 关联岗位ID
     */
    private Long postId;

    /**
     * AI 提取置信度（0.0 - 1.0）
     */
    private Float confidenceScore;

    /**
     * 掌握程度：EXPERT / ADVANCED / INTERMEDIATE / BEGINNER
     */
    private String masteryLevel;

    /**
     * 证据文本（简历原文、测试答案、面试回答、PMS项目记录等）
     */
    private String evidenceText;

    /**
     * AI 推理理由
     */
    private String aiReasoning;

    /**
     * 原始文本上下文（用于幻觉防护检查）
     */
    private String contextText;
}
