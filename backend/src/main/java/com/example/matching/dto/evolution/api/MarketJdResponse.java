package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "市场JD响应")
public record MarketJdResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "导入批次号") String batchNo,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "公司名称") String companyName,
        @Schema(description = "城市") String city,
        @Schema(description = "薪资范围") String salaryRange,
        @Schema(description = "岗位描述") String jobDescription,
        @Schema(description = "任职要求") String requirements,
        @Schema(description = "技能标签JSON") String skillTags,
        @Schema(description = "来源平台") String sourcePlatform,
        @Schema(description = "JD发布时间") LocalDateTime publishedTime,
        @Schema(description = "文本哈希") String textHash,
        @Schema(description = "相似JD分组ID") String similarityGroupId,
        @Schema(description = "JD质量分") BigDecimal qualityScore,
        @Schema(description = "是否重复") Integer isDuplicate,
        @Schema(description = "规范文档ID") Long canonicalDocumentId,
        @Schema(description = "最后出现时间") LocalDateTime lastSeenTime,
        @Schema(description = "时效性评分") BigDecimal freshnessScore,
        @Schema(description = "噪声评分") BigDecimal noiseScore,
        @Schema(description = "公司多样性键") String companyDiversityKey,
        @Schema(description = "匹配到的系统岗位ID") Long matchedPostId,
        @Schema(description = "分析状态") Integer analysisStatus,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT") String ingestChannel,
        @Schema(description = "入库通道中文名") String ingestChannelText,
        // 2026-09-04 新增：原文核对所需的外部标识与来源链接。
        // 「查看推送原文」时要能回答「这条到底是不是我推的那条」，只有岗位名/公司名是不够的 ——
        // 爬虫推送体里带的是 externalId（源站岗位 ID）与 sourceUrl（原始 JD 页），
        // 这两个字段原本已落库（MarketJdData.externalId / sourceUrl），只是没有透出。
        @Schema(description = "爬虫推送的源站岗位ID") String externalId,
        @Schema(description = "原始JD页面链接") String sourceUrl
) implements Serializable {
}
