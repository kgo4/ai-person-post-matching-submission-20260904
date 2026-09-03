package com.example.matching.vo.matching;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI分析报告视图
 */
@Data
@Schema(description = "AI分析报告视图")
public class AiAnalysisReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "匹配记录ID")
    private Long matchingRecordId;

    @Schema(description = "总体匹配度")
    private BigDecimal overallScore;

    @Schema(description = "各维度得分")
    private List<DimensionScore> dimensionScores;

    @Schema(description = "优势分析")
    private List<String> strengths;

    @Schema(description = "差距分析")
    private List<String> gaps;

    @Schema(description = "建议")
    private List<String> suggestions;

    @Data
    @Schema(description = "维度得分")
    public static class DimensionScore implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "维度名称")
        private String dimensionName;

        @Schema(description = "得分")
        private BigDecimal score;

        @Schema(description = "权重")
        private BigDecimal weight;
    }
}
