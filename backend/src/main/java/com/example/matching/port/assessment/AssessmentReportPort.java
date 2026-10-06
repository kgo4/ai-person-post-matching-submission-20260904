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
                     String conclusion, String recommendation) {

        /**
         * 替换分数字段，其余字段原样保留。
         *
         * <p>用于「综合评分」的读时重算：{@code overall_score} 列存的是报告生成时点的值，
         * 而 HR 的能力等级确认发生在报告落库**之后**，所以对外下发前必须按当前决策重算一遍。
         */
        public ReportDTO withScores(Integer newOverallScore, Integer newPostMatchScore) {
            return new ReportDTO(workflowId, empId, postId, sessionId, status,
                    newOverallScore, newPostMatchScore, resumeSummaryJson, testSummaryJson,
                    interviewSummaryJson, aggregateSummaryJson, levelSummaryJson,
                    conclusion, recommendation);
        }
    }

    /** 历史报告列表项：一次评估流程 + 报告状态（无报告时 reportStatus=null） */
    record WorkflowReportDTO(Long workflowId, String workflowStatus,
                             LocalDateTime startedAt, LocalDateTime completedAt,
                             String reportStatus, Integer overallScore, Integer postMatchScore) {

        /**
         * 抹掉分数，保留流程与报告状态。
         *
         * <p>用于员工侧的「HR 尚未审完全部能力项」场景：分数属于**评估结论**，
         * 与报告正文受同一闸门保护，审核期间不下发；但流程状态与报告状态是进度信息，
         * 员工有权知道，所以只清分数、不整行丢弃。
         */
        public WorkflowReportDTO withoutScores() {
            return new WorkflowReportDTO(workflowId, workflowStatus, startedAt, completedAt,
                    reportStatus, null, null);
        }

        /**
         * 替换报告状态，其余字段原样保留。
         *
         * <p>用途：报告行虽然已是 {@code READY}（AI 面试结束即落库），但内容可能还没全部生成完
         * （缺最终能力等级 / AI 洞察等）。可见性以**内容完整性**为准，因此列表里要把这种行
         * 标成「生成中」，而不是给员工一个写着「已生成」却打不开的按钮。
         */
        public WorkflowReportDTO withReportStatus(String newReportStatus) {
            return new WorkflowReportDTO(workflowId, workflowStatus, startedAt, completedAt,
                    newReportStatus, overallScore, postMatchScore);
        }
    }

    /**
     * AI 综合洞察（V160）。
     *
     * <p>刻意与 {@link ReportDTO} 分开：洞察是**后置**写入的（面试报告主体先落库，
     * 审核清空后才生成洞察），且这里**没有任何分数字段** —— 数值全部复用既有列。
     */
    record InsightDTO(String sectionInsightsJson, String strengthsJson, String weaknessesJson,
                      String riskSignalsJson, String suggestionsJson, String aiConclusion,
                      String insightSource, String insightModel, Integer insightConfidence,
                      LocalDateTime insightGeneratedAt, String sourceFingerprint) {}

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

    /**
     * 读取报告的 AI 洞察部分；报告行不存在时返回 null。
     *
     * <p>返回 null 有两种含义：「报告主体尚未生成」或「报告已生成但洞察还没生成」——
     * 调用方需先用 {@link #findReport(Long)} 区分，避免把「还没跑洞察」误判为「报告不存在」。
     */
    InsightDTO findInsights(Long workflowId);

    /**
     * 写入 AI 洞察（只更新洞察列，**不触碰任何既有数值列与状态**）。
     *
     * @return 受影响行数；0 表示报告行尚不存在
     */
    int updateInsights(Long workflowId, InsightDTO insights);

    /** 员工全部评估流程 + 报告状态（倒序） */
    List<WorkflowReportDTO> listWorkflowReports(Long empId);

    /** 读取工作流的全部能力聚合组（回填时关联能力名） */
    List<ClaimGroupDTO> listClaimGroups(Long workflowId);
}
