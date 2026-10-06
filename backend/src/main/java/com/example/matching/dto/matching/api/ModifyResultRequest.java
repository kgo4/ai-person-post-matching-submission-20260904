package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "人工修改匹配结果请求")
public record ModifyResultRequest(
    @Schema(description = "AI匹配度分数", example = "85.50") BigDecimal matchScore,
    @Schema(description = "匹配状态", example = "1") Integer matchStatus,
    @Schema(description = "人工备注") String remark
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
