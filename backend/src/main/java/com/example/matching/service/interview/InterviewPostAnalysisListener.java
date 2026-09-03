package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.event.InterviewFinishedEvent;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.assessment.InterviewAssessmentEvidenceService;
import com.example.matching.service.employee.impl.VideoInterviewVisualAnalyzer;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.service.learning.LearningPathPlanService;
import com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.common.enums.StageLifecycleEventType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 面试结束后异步触发 AI 分析
 * <p>
 * 监听 {@link InterviewFinishedEvent}，异步调用 AI Agent 进行面试能力观察和胜任力报告生成。
 * 工作流面试（workflowId 非空）分析完成后不直接构建最终画像，
 * 而是保存面试证据并推进聚合审核（AGGREGATE_HARNESS 阶段）。
 * 此组件解除了 {@link InterviewSessionManager} 与 AI Agent 之间的循环依赖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewPostAnalysisListener {

    private final AIInterviewAgent aiInterviewAgent;
    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final LearningPathPlanService learningPathPlanService;
    private final InterviewAssessmentEvidenceService interviewAssessmentEvidenceService;
    private final com.example.matching.service.assessment.AssessmentReportService assessmentReportService;
    private final VideoInterviewVisualAnalyzer visualAnalyzer;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final InterviewTranscriptBuffer transcriptBuffer;

    @Async
    @EventListener
    public void onInterviewFinished(InterviewFinishedEvent event) {
        Long sessionId = event.getSessionId();
        log.info("开始面试后异步分析，sessionId: {}", sessionId);

        // CAS 抢占状态机：FINISHED(3) -> ANALYZING(4)，防止与 REST analyze() 双跑，
        // 以及事件重放导致的重复分析
        if (!transitionStatus(sessionId, 3, 4)) {
            log.info("面试会话不在待分析状态，跳过异步分析（可能已被 analyze() 处理）: sessionId={}", sessionId);
            return;
        }

        try {
            // 结束请求与 ASR 回调可能并发到达，再次冲刷保证分析读取最终回答。
            if (transcriptBuffer != null) {
                transcriptBuffer.flushSession(sessionId);
            }
            EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
            Long empId = session != null ? session.getEmpId() : null;

            // The browser normally submits the assessment finish action after the
            // WebSocket acknowledgement. If the socket closes first, the session
            // can still finish successfully while the assessment workflow remains
            // INTERVIEW_IN_PROGRESS. Reconcile that boundary from the durable
            // interview-finished event before publishing TASK_SUCCEEDED.
            publishWorkflowInterviewCompleted(session);

            // 与手动 analyze 路径一致：先把已采集的关键帧转换为受控视觉证据，
            // 再进行仅基于范围内回答的能力核验。
            visualAnalyzer.analyzeVisualEvidence(sessionId);
            aiInterviewAgent.conductInterviewAndObserve(sessionId);

            CompetencyReport report = aiInterviewAgent.generateCompetencyReport(sessionId);
            if (report == null) {
                throw new IllegalStateException("Interview competency report is null: sessionId=" + sessionId);
            }
            // 评估流程面试：生成评估综合报告主体（简历+测试+面试），聚合审核/等级确认结论后续回填
            if (session != null && session.getWorkflowId() != null) {
                assessmentReportService.generateAndPersist(session.getWorkflowId(), sessionId, report);
            }
            if (session != null) {
                createLearningPathForInterviewSuggestions(session, report);
                // ANALYZING(4) -> COMPLETED(5)，CAS 更新防止与并发分析互相覆盖
                int rows = sessionMapper.completeAnalysis(sessionId,
                        report.degraded() ? null : BigDecimal.valueOf(report.overallScore()), report.conclusion());
                if (rows != 1) {
                    log.warn("Interview session status changed during analysis, completion skipped: sessionId={}", sessionId);
                }
                // 工作流面试：不直接构建最终画像，保存面试证据并推进聚合审核
                if (session.getWorkflowId() != null) {
                    interviewAssessmentEvidenceService.saveInterviewEvidenceAndAdvance(
                            session.getWorkflowId(), empId, sessionId);
                }
            }

            log.info("面试后异步分析完成，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("面试后异步分析失败，sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            // 回退到 FINISHED(3) 供调度器重试；重试计数由恢复调度器维护
            // （若恢复调度器已把该会话置 7=FAILED 或用户已手动重分析，此 CAS 自然失效）
            transitionStatus(sessionId, 4, 3);
            markAnalysisFailure(sessionId, truncate(e.getMessage(), 500));
        }
    }

    private void publishWorkflowInterviewCompleted(EmpVideoInterviewSession session) {
        if (session == null || session.getWorkflowId() == null) {
            return;
        }
        try {
            var workflow = workflowService.getWorkflow(session.getWorkflowId());
            if (workflow == null || !"INTERVIEW_IN_PROGRESS".equals(workflow.getStatus())) {
                return;
            }
            PersonCapabilityStageRun stageRun = workflowService.getLatestStageRun(
                    session.getWorkflowId(), "AI_INTERVIEW");
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    session.getWorkflowId(), stageRun != null ? stageRun.getId() : null,
                    "AI_INTERVIEW", "AI_INTERVIEW", session.getId(),
                    StageLifecycleEventType.USER_ACTION_COMPLETED, null, null));
            log.info("面试完成事件已补齐工作流状态: workflowId={}, sessionId={}",
                    session.getWorkflowId(), session.getId());
        } catch (Exception e) {
            // The analysis event remains retryable; do not prevent report
            // generation when workflow reconciliation is temporarily unavailable.
            log.warn("补齐面试工作流完成事件失败: workflowId={}, sessionId={}, error={}",
                    session.getWorkflowId(), session.getId(), e.getMessage());
        }
    }

    /**
     * 条件状态迁移（CAS）：仅当当前状态匹配时才切换，返回是否迁移成功。
     */
    private boolean transitionStatus(Long sessionId, int fromStatus, int toStatus) {
        return sessionMapper.transitionStatus(sessionId, fromStatus, toStatus) == 1;
    }

    /**
     * 记录分析失败原因（仅当会话仍处于待重试/分析中间态时写入，避免覆盖已完成状态）。
     */
    private void markAnalysisFailure(Long sessionId, String reason) {
        try {
            sessionMapper.markAnalysisFailure(sessionId, reason);
        } catch (Exception ex) {
            log.warn("Failed to mark interview analysis failure: sessionId={}", sessionId, ex);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void createLearningPathForInterviewSuggestions(EmpVideoInterviewSession session,
                                                            CompetencyReport report) {
        if (session.getEmpId() == null || session.getPostId() == null
                || report.learningPathSuggestions() == null || report.learningPathSuggestions().isEmpty()) {
            return;
        }
        MatchingRecord record = matchingRecordMapper.selectOne(Wrappers.<MatchingRecord>lambdaQuery()
                .eq(MatchingRecord::getEmpId, session.getEmpId())
                .eq(MatchingRecord::getPostId, session.getPostId())
                .eq(MatchingRecord::getIsDeleted, 0)
                .orderByDesc(MatchingRecord::getCreatedTime)
                .last("LIMIT 1"));
        if (record == null || record.getId() == null) {
            log.debug("No matching record for interview learning suggestions: sessionId={}", session.getId());
            return;
        }
        LearningPathGenerateRequest request = new LearningPathGenerateRequest();
        request.setMatchingRecordId(record.getId());
        request.setIncludeProjectTasks(true);
        request.setForceRegenerate(false);
        learningPathPlanService.generateFromMatchingRecord(request);
    }
}
