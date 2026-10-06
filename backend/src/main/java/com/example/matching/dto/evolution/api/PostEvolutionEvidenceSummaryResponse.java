package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "岗位演化变更项的已关联证据汇总")
public record PostEvolutionEvidenceSummaryResponse(
        @Schema(description = "真实来源数量，按 sourceRef 去重") int sourceCount,
        @Schema(description = "已关联证据中的最高可信度") BigDecimal maxTrustScore,
        @Schema(description = "已关联证据中的平均可信度") BigDecimal averageTrustScore,
        @Schema(description = "是否由两个及以上不同来源交叉佐证") boolean crossSourceVerified
) {
}
