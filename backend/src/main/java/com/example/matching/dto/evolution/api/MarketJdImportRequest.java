package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "市场JD导入请求")
public record MarketJdImportRequest(
        @Schema(description = "导入批次号") String batchNo,
        @Schema(description = "岗位名称") String postName,
        @Schema(description = "公司名称") String companyName,
        @Schema(description = "城市") String city,
        @Schema(description = "薪资范围") String salaryRange,
        @Schema(description = "岗位描述") String jobDescription,
        @Schema(description = "任职要求") String requirements,
        @Schema(description = "技能标签JSON") String skillTags,
        @Schema(description = "来源平台") String sourcePlatform
) implements Serializable {
}
