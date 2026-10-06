package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评估流程综合报告实体
 * <p>
 * 一次评估（workflow）一份报告；面试结束后生成主体，聚合审核/等级确认完成后回填。
 */
@Data
@TableName("assessment_report")
public class AssessmentReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private Long empId;

    private Long postId;

    private Long sessionId;

    /** 报告状态：READY/FAILED */
    private String status;

    private Integer overallScore;

    private Integer postMatchScore;

    private String resumeSummaryJson;

    private String testSummaryJson;

    private String interviewSummaryJson;

    private String aggregateSummaryJson;

    private String levelSummaryJson;

    private String conclusion;

    private String recommendation;

    /* ===================== AI 综合洞察（V160 新增；只有文字，没有分数） ===================== */

    /** 各部分解读 JSON：[{section, insight}]，section ∈ RESUME/AI_TEST/AI_INTERVIEW/FINAL_LEVEL */
    private String sectionInsightsJson;

    private String strengthsJson;

    private String weaknessesJson;

    private String riskSignalsJson;

    private String suggestionsJson;

    /**
     * 综合结论文字（AI 或模板生成）。
     * <p>刻意与面试侧写入的 {@link #conclusion} 分开：后者是面试结论，本列是本报告的结论。
     */
    private String aiConclusion;

    /** 洞察文字来源：AI / TEMPLATE（AI 不可用时降级为模板，数值部分不受影响） */
    private String insightSource;

    private String insightModel;

    /** 洞察文字可信度 0-100；语义为「文字可信度」而非分数可信度 */
    private Integer insightConfidence;

    private LocalDateTime insightGeneratedAt;

    /** 输入事实指纹，用于幂等（未变化则不重复调用 LLM） */
    private String sourceFingerprint;

    private LocalDateTime generatedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Version
    private Integer version;
}
