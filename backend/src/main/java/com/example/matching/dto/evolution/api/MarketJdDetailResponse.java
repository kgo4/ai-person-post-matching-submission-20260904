package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单条市场 JD 的解析结果详情。
 * <p>
 * 背景：市场 JD 的解析结果落在 {@code market_jd_data.skill_tags}（已准入正式标签 ID 的 JSON）
 * 与 {@code recommended_skill_tags}（高置信候选）里，而池列表只渲染了一个状态标签 ——
 * 用户点完「解析该批次」看不到任何结果。本 DTO 把这两个 JSON 反解成可读标签，
 * 供「结果」入口逐条查看。
 * <p>
 * 同时提供 {@code rawSkillTags} / {@code rawRecommendedSkillTags} 原文：标签被删除或停用后
 * 反解不出名字，只靠 {@code acceptedTags} 会让人以为「解析没产出」，保留原文才能自查。
 *
 * @author system
 */
@Schema(description = "市场JD解析结果详情")
public record MarketJdDetailResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "导入批次号") String batchNo,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "公司名称") String companyName,
        @Schema(description = "城市") String city,
        @Schema(description = "薪资范围") String salaryRange,
        @Schema(description = "岗位描述") String jobDescription,
        @Schema(description = "任职要求") String requirements,
        @Schema(description = "来源平台") String sourcePlatform,
        @Schema(description = "JD发布时间") LocalDateTime publishedTime,
        @Schema(description = "分析状态：0待分析 / 1已分析 / 2跳过（跳过 = 去重命中或清洗阻断）") Integer analysisStatus,
        @Schema(description = "是否重复：0否 1是") Integer isDuplicate,
        @Schema(description = "JD质量分") BigDecimal qualityScore,
        @Schema(description = "噪声评分") BigDecimal noiseScore,
        @Schema(description = "时效性评分") BigDecimal freshnessScore,
        @Schema(description = "匹配到的系统岗位ID") Long matchedPostId,
        @Schema(description = "匹配到的系统岗位名称（查不到时为 null）") String matchedPostName,
        @Schema(description = "入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT") String ingestChannel,
        @Schema(description = "入库通道中文名") String ingestChannelText,
        @Schema(description = "已准入标签 ID 的原始 JSON（未准入时为空数组）") String rawSkillTags,
        @Schema(description = "已准入的正式能力标签（已反解名称）") List<TagRef> acceptedTags,
        @Schema(description = "推荐（候选）标签 ID 的原始 JSON") String rawRecommendedSkillTags,
        @Schema(description = "推荐（候选）能力标签，尚未正式准入") List<TagRef> recommendedTags,
        @Schema(description = "AI 原始提取结果的原始 JSON（未经标签库匹配与准入）") String rawAiSkillTags,
        @Schema(description = "AI 原始提取的能力项：无论是否通过标签库匹配与准入都会在这里返回，"
                + "是「解析跑过了、AI 到底读出了什么」的唯一依据") List<AiTag> aiTags,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {

    /**
     * 能力标签引用 —— 只暴露展示所需字段，不暴露 {@code AbilityTag} 实体。
     */
    @Schema(description = "能力标签引用")
    public record TagRef(
            @Schema(description = "标签ID") Long tagId,
            @Schema(description = "标签名称") String tagName,
            @Schema(description = "标签编码") String tagCode,
            @Schema(description = "标签分类：TECHNICAL / SOFT / BUSINESS") String tagCategory,
            @Schema(description = "标签层级：1/2/3级") Integer tagLevel
    ) implements Serializable {
    }

    /**
     * AI 原始提取的能力项（{@code market_jd_data.ai_skill_tags} 的元素）。
     * <p>
     * 与 {@link TagRef} 的区别是本条记录的<b>性质</b>：
     * {@code TagRef} 来自系统能力字典（有 tagId，代表「已经进了正式能力库」），
     * 而 {@code AiTag} 是 AI 读 JD 得到的<b>建议</b>，可能根本没命中任何正式标签 ——
     * 因此 {@code matchStatus} 为 {@code NEW} / {@code null} 时它只有名称与证据，没有 tagId。
     */
    @Schema(description = "AI 原始提取的能力项")
    public record AiTag(
            @Schema(description = "AI 建议的能力名称") String name,
            @Schema(description = "所属技术栈，如 Java、Spring、MySQL") String techStack,
            @Schema(description = "能力分类：TECHNICAL / SOFT / BUSINESS") String category,
            @Schema(description = "能力类型：TECHNICAL / BUSINESS / SOFT / QUALIFICATION") String abilityType,
            @Schema(description = "建议最低要求等级：1-5") Integer level,
            @Schema(description = "建议权重占比") BigDecimal weight,
            @Schema(description = "是否核心项：0否 1是") Integer isCore,
            @Schema(description = "是否必填：0否 1是") Integer isRequired,
            @Schema(description = "AI 置信度：0-100") BigDecimal confidence,
            @Schema(description = "与系统标签库的匹配状态：MATCHED / SIMILAR / NEW；未匹配到任何标签时为 null") String matchStatus,
            @Schema(description = "匹配到的系统标签ID（仅 MATCHED / SIMILAR 有值）") Long matchedTagId,
            @Schema(description = "匹配到的系统标签名称（仅 MATCHED / SIMILAR 有值）") String matchedTagName,
            @Schema(description = "与系统标签的相似度（仅 SIMILAR 有值）") Double similarityScore,
            @Schema(description = "支持该能力主张的 JD 原文片段") String evidence,
            @Schema(description = "AI 的推理依据") String reasoning
    ) implements Serializable {
    }
}
