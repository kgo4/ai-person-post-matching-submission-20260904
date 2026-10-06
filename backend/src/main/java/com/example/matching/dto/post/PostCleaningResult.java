package com.example.matching.dto.post;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位数据清洗结果
 * <p>
 * 系统内部自动清洗去噪后的结果，包含清洗后文本、质量评分、重复检测等信息。
 * 这是 PostDataCleaningService.cleanAndDetect() 的返回值。
 */
@Data
public class PostCleaningResult {

    /** 清洗后的岗位名称 */
    private String cleanedPostName;

    /** 清洗后的岗位描述文本 */
    private String cleanedText;

    /** 被移除的噪声内容 */
    private String removedNoiseText;

    /** 结构化职责列表 */
    private List<String> responsibilities;

    /** 结构化要求列表 */
    private List<String> requirements;

    /** 质量评分 0.00-1.00 */
    private BigDecimal qualityScore;

    /** 质量评估详情 */
    private QualityDetails qualityDetails;

    /** 重复状态：NONE / SUSPECTED / DUPLICATE_BLOCKED */
    private String duplicateStatus;

    /** 疑似重复的岗位ID */
    private Long duplicatePostId;

    /** 疑似重复岗位名称 */
    private String duplicatePostName;

    /** 与疑似重复岗位的相似度 */
    private BigDecimal duplicateScore;

    /** 是否被阻断 */
    private boolean blocked;

    /** 阻断原因 */
    private String blockReason;

    /** 清洗记录ID（持久化后回填） */
    private Long cleaningRecordId;

    /**
     * 判断是否应该阻断后续流程
     * <p>
     * 阻断条件：
     * 1. 强重复（相似度 >= 0.92）
     * 2. 质量评分 < 0.4
     */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * 判断是否为疑似重复（不阻断但标记）
     */
    public boolean isSuspectedDuplicate() {
        return PostCleaningRecordVO.DUPLICATE_STATUS_SUSPECTED.equals(duplicateStatus);
    }

    /**
     * 判断是否为低质量（不阻断但标记）
     */
    public boolean isLowQuality() {
        return qualityScore != null && qualityScore.compareTo(new BigDecimal("0.4")) >= 0
                && qualityScore.compareTo(new BigDecimal("0.7")) < 0;
    }

    /**
     * 质量评估详情
     */
    @Data
    public static class QualityDetails {
        /** 文本长度评分 */
        private BigDecimal lengthScore;
        /** 结构化程度评分 */
        private BigDecimal structureScore;
        /** 技术关键词丰富度评分 */
        private BigDecimal keywordScore;
        /** 通用描述占比评分（越低越好） */
        private BigDecimal genericRatioScore;
        /** 详细警告信息 */
        private List<String> warnings;
    }
}
