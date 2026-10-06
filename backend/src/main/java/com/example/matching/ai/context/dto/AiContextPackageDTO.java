package com.example.matching.ai.context.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI上下文包DTO - AI每次读取数据的标准输入
 *
 * @author system
 */
@Data
public class AiContextPackageDTO {

    /** 场景：MATCHING_ANALYSIS/LEARNING_PATH/GOVERNANCE */
    private String scenario;

    /** 员工信息 */
    private Long empId;
    private String empName;
    private String empCode;
    private String empLevel;

    /** 岗位信息 */
    private Long postId;
    private String postName;
    private String postCode;
    private String postLevel;

    /** 匹配信息 */
    private Long matchingRecordId;
    private BigDecimal matchScore;

    /** 能力列表 */
    private List<AiContextAbilityDTO> employeeAbilities;
    private List<AiContextAbilityDTO> postRequirements;

    /** 差距列表 */
    private List<AiContextGapDTO> gaps;

    /** 评分明细 */
    private List<AiContextScoreBreakdownDTO> scoreBreakdown;

    /** 证据列表 */
    private List<AiContextEvidenceDTO> evidences;

    /** 风险信号 */
    private List<AiContextRiskSignalDTO> riskSignals;

    /** 来源引用 */
    private List<AiContextSourceRefDTO> sourceRefs;

    /** 图谱摘要 */
    private AiContextGraphSummaryDTO graphSummary;

    /** 反馈信号 */
    private Map<String, Object> feedbackSignals;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 预估token数量 */
    private Integer tokenEstimate;

    /** 上下文hash */
    private String contextHash;
}
