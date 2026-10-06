package com.example.matching.dto.contest.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "创建报告请求")
public record CreateContestReportRequest(
        @Schema(description = "报告类型") String reportType,
        @Schema(description = "报告标题") String title
) implements Serializable {
}
