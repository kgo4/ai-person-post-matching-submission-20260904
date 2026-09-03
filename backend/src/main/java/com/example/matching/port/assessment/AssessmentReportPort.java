package com.example.matching.port.assessment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估报告数据端口：跨域读取 claim / 读写报告表。
 * <p>
 * Service 层（assessment 域）依赖本端口访问 ability/workflow 域数据，
 * 避免新增跨域 Mapper 依赖（ArchitectureRulesTest 强制）。
 */
public interface AssessmentReportPort {

    /** 能力声明摘要（简历/测试来源） */
    record ClaimDTO(Long tagId, String abilityName, Integer claimedLevel,
                    BigDecimal confidenceScore, String evidenceText, String harnessDecision) {}

    /** 能力聚合组（报告回填时关联能力名） */
    record ClaimGroupDTO(Long claimGroupId, Long canonicalTagId, String normalizedAbilityName, String status) {}

    /** 面试主问题及其追问回答，用于报告可追溯展示。 */
    record InterviewQuestionAnswerDTO(Integer questionOrder, String questionType, String questionText,
                                      String answerText, Integer durationSeconds, String endedBy,
                                      BigDecimal answerScore, String analysisComment,
                                      List<FollowUpAnswerDTO> followUps) {}

    record FollowUpAnswerDTO(Integer followUpOrder, String questionText, String answerText,
                              String triggerReason, String boundaryJudgement, BigDecimal answerQualityScore) {}

    /** 报告表行 */
    record ReportDTO(Long workflowId, Long empId, Long postId, Long sessionId, String status,
                     Integer overallScore, Integer postMatchScore,
                     String resumeSummaryJson, String testSummaryJson, String interviewSummaryJson,
                     String aggregateSummaryJson, String levelSummaryJson,
                     String conclusion, String recommendation) {}

    /** 历史报告列表项：一次评估流程 + 报告状态（无报告时 reportStatus=null） */
    record WorkflowReportDTO(Long workflowId, String workflowStatus,
                             LocalDateTime startedAt, LocalDateTime completedAt,
                             String reportStatus, Integer overallScore, Integer postMatchScore) {}

    /** 按来源读取工作流的能力声明摘要 */
    List<ClaimDTO> listClaims(Long workflowId, String sourceType);

    List<InterviewQuestionAnswerDTO> listInterviewQuestionAnswers(Long sessionId);

    /** 读取单次评估报告（不存在返回 null） */
    ReportDTO findReport(Long workflowId);

    /** 全量 upsert 报告主体（按 workflowId） */
    void saveReport(ReportDTO report);

    /** 回填聚合审核结论（单字段更新，不改状态） */
    void updateAggregateSummary(Long workflowId, String aggregateSummaryJson);

    /** 回填等级确认结论（单字段更新，不改状态） */
    void updateLevelSummary(Long workflowId, String levelSummaryJson);

    /** 员工全部评估流程 + 报告状态（倒序） */
    List<WorkflowReportDTO> listWorkflowReports(Long empId);

    /** 读取工作流的全部能力聚合组（回填时关联能力名） */
    List<ClaimGroupDTO> listClaimGroups(Long workflowId);
}
