package com.example.matching.dto.closure;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 综合差距诊断 — 最终诊断结果
 * <p>
 * 包含事实诊断包 + AI 综合分析 + 校验元数据。
 * 第一期：仅返回事实诊断包（无 AI 分析）。
 * 第二期：接入 AI 后返回完整结构。
 *
 * @author system
 */
@Data
public class ComprehensiveDiagnosisResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 员工ID */
    private Long empId;

    /** 岗位ID */
    private Long postId;

    /** 事实诊断包（系统生成，始终存在） */
    private ComprehensiveDiagnosisFactDTO factPackage;

    /** AI 综合分析结果（第二期启用，第一期为 null） */
    private AiDiagnosisAnalysis aiAnalysis;

    /**
     * AI 综合分析结果
     */
    @Data
    public static class AiDiagnosisAnalysis implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 整体结论 */
        private String overallConclusion;

        /** 风险等级：LOW / MEDIUM / HIGH / CRITICAL */
        private String riskLevel;

        /** 各维度诊断 */
        private List<DimensionDiagnosis> dimensions = new ArrayList<>();

        /** 优先动作 */
        private List<PriorityAction> priorityActions = new ArrayList<>();

        /** 被拦截的声明（无来源支撑的结论） */
        private List<BlockedClaim> blockedClaims = new ArrayList<>();

        /** AI 生成时间 */
        private String generatedAt;
    }

    /**
     * 维度诊断
     */
    @Data
    public static class DimensionDiagnosis implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 维度标识：HARD_CONDITION / ABILITY / SEMANTIC / EVIDENCE / POST_TASK / FEEDBACK / GROWTH */
        private String dimension;

        /** 维度标题 */
        private String title;

        /** 严重程度：LOW / MEDIUM / HIGH / CRITICAL */
        private String severity;

        /** 事实依据 */
        private List<String> facts = new ArrayList<>();

        /** AI 分析 */
        private String analysis;

        /** 来源引用 */
        private List<String> sourceRefs = new ArrayList<>();

        /** 改进建议 */
        private List<String> suggestions = new ArrayList<>();
    }

    /**
     * 优先动作
     */
    @Data
    public static class PriorityAction implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 动作描述 */
        private String action;

        /** 原因 */
        private String reason;

        /** 来源引用 */
        private List<String> sourceRefs = new ArrayList<>();
    }

    /**
     * 被拦截的声明
     */
    @Data
    public static class BlockedClaim implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 原始声明 */
        private String claim;

        /** 拦截原因 */
        private String reason;

        /** 置信度：SUPPORTED / WEAK_SUPPORT / BLOCKED */
        private String confidence;
    }
}
