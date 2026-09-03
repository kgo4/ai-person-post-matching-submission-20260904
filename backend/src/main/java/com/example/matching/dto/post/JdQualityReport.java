package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * JD质量检测报告DTO
 */
@Data
@Schema(description = "JD质量检测报告")
public class JdQualityReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否存在质量问题")
    private Boolean hasIssues;

    @Schema(description = "整体质量评分 0-100")
    private Integer overallScore;

    @Schema(description = "质量警告列表")
    private List<QualityWarning> warnings;

    @Schema(description = "时效性问题列表")
    private List<TimelinessIssue> timelinessIssues;

    @Schema(description = "抄袭检测结果（多个JD对比时）")
    private List<SimilarityPair> plagiarismResults;

    /**
     * 质量警告
     */
    @Data
    public static class QualityWarning implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "警告类型：TIMELINESS/INFLATION/QUALITY/PLAGIARISM")
        private String type;

        @Schema(description = "严重程度：ERROR/WARNING/INFO")
        private String level;

        @Schema(description = "警告信息")
        private String message;
    }

    /**
     * 时效性问题
     */
    @Data
    public static class TimelinessIssue implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "过时技术名称")
        private String outdatedTech;

        @Schema(description = "建议替换为")
        private String suggestedReplacement;

        @Schema(description = "严重程度")
        private String severity;

        @Schema(description = "问题描述")
        private String message;
    }

    /**
     * 相似度对（抄袭检测）
     */
    @Data
    public static class SimilarityPair implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "第一个JD的索引")
        private Integer index1;

        @Schema(description = "第二个JD的索引")
        private Integer index2;

        @Schema(description = "相似度 0-1")
        private Double similarity;

        @Schema(description = "是否可疑")
        private Boolean isSuspicious;

        @Schema(description = "说明")
        private String message;
    }
}
