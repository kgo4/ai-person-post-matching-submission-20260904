package com.example.matching.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习建议 DTO
 * <p>
 * 包含请求、响应、校验结果的完整结构。
 * AI 只能基于系统检索到的资源生成学习建议，不能凭空编造资源或能力。
 *
 * @author system
 */
public class AiLearningSuggestionDTO {

    // ==================== Request ====================

    /**
     * AI 学习建议请求
     */
    @Data
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 匹配记录ID（用于诊断差距） */
        private Long matchingRecordId;

        /** 员工ID */
        private Long empId;

        /** 岗位ID */
        private Long postId;

        /** 能力差距列表（可选，不传则从匹配记录诊断） */
        private List<GapInput> gaps;

        @Data
        public static class GapInput implements Serializable {
            private static final long serialVersionUID = 1L;
            private Long tagId;
            private String abilityName;
            private BigDecimal currentLevel;
            private Integer requiredLevel;
            private boolean weakEvidence;
            private String reason;
        }
    }

    // ==================== Response ====================

    /**
     * AI 学习建议响应（完整）
     */
    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 匹配记录ID */
        private Long matchingRecordId;

        /** 员工ID */
        private Long empId;

        /** 岗位ID */
        private Long postId;

        /** 每个能力的AI建议 */
        private List<AbilitySuggestion> suggestions = new ArrayList<>();

        /** 校验状态 */
        private ValidationSummary validation;

        /** 是否存在证据不足的情况 */
        private boolean hasInsufficientEvidence;

        /** RAG 检索到的 chunkIds */
        private List<Long> ragChunkIds = new ArrayList<>();
    }

    /**
     * 单个能力的AI建议
     */
    @Data
    public static class AbilitySuggestion implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 能力名称（必须来自差距诊断） */
        private String abilityName;

        /** 能力标签ID */
        private Long tagId;

        /** 风险等级：HIGH / MEDIUM / LOW */
        private String riskLevel;

        /** 差距原因 */
        private String reason;

        /** 当前等级 */
        private BigDecimal currentLevel;

        /** 要求等级 */
        private Integer requiredLevel;

        /** 学习步骤（AI生成，已校验） */
        private List<LearningStep> steps = new ArrayList<>();

        /** 证据不足标记：true 表示系统无足够资源，AI建议仅供参考 */
        private boolean insufficientEvidence;

        /** AI 建议来源标签 */
        private String suggestionSource;
    }

    /**
     * 单个学习步骤
     */
    @Data
    public static class LearningStep implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 资源ID（必须来自系统资源库） */
        private Long resourceId;

        /** 资源标题（与数据库一致） */
        private String title;

        /** 资源类型 */
        private String resourceType;

        /** 资源URL */
        private String url;

        /** 难度等级 */
        private Integer difficultyLevel;

        /** AI生成：为什么先学这个 */
        private String why;

        /** AI生成：怎么用这个资源 */
        private String action;

        /** 引用来源（resource:{id} 或 evidence:{id}） */
        private List<String> sourceRefs = new ArrayList<>();

        /** 是否通过校验 */
        private boolean validated;

        /** 校验失败原因（如果有） */
        private String validationFailureReason;
    }

    // ==================== Validation ====================

    /**
     * 校验摘要
     */
    @Data
    public static class ValidationSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        /** AI返回的总步骤数 */
        private int totalSteps;

        /** 通过校验的步骤数 */
        private int validatedSteps;

        /** 被过滤的步骤数 */
        private int filteredSteps;

        /** 是否存在证据不足的能力 */
        private boolean hasInsufficientEvidence;

        /** 校验详情 */
        private List<String> details = new ArrayList<>();
    }
}
