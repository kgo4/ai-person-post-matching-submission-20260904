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
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
