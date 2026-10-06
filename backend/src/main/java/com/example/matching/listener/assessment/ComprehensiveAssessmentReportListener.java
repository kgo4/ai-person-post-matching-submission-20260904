package com.example.matching.listener.assessment;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.service.assessment.AssessmentReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 全方位评估报告「AI 综合洞察」触发生成监听器。
 *
 * <p><b>为什么零侵入</b>：复用既有阶段生命周期事件（{@code USER_ACTION_COMPLETED}），
 * 事务提交后尝试生成一次洞察，失败只记日志。不复用已有业务代码、不新增调用点，
 * 因此对评估主链路没有任何影响面。
 *
 * <p><b>为什么不会重复烧模型</b>：
 * <ul>
 *   <li>{@code USER_ACTION_COMPLETED} 会在多个阶段发出（提交测试、面试结束等），
 *       但 {@code AssessmentReportService.generateInsights} 内部有两级闸门 ——
 *       流程未到 {@code COMPLETED}、或仍有待人工审核的能力项时直接返回，**根本不调模型**；</li>
 *   <li>真正满足条件时还有指纹幂等兜底：同一份事实只生成一次，审核被重审后才允许重算。</li>
 * </ul>
 *
 * <p>HR 手动「重新生成报告」走 {@code force=true}，忽略指纹强制重算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveAssessmentReportListener {

    private final AssessmentReportService assessmentReportService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStageLifecycle(CapabilityStageLifecycleEvent event) {
        try {
            if (event == null
                    || event.eventType() != StageLifecycleEventType.USER_ACTION_COMPLETED
                    || event.workflowId() == null) {
                return;
            }
            // 【2026-09-04 修复】先把结论区块补齐，再生成洞察。
            //
            // 触发点已扩展到「HR 把待人工复核项全部审完」（见
            // AggregateAbilityHarnessReviewServiceImpl#finalizeIfAllReviewsResolved）。
            // 此前 refreshLevelConclusion 没有任何生产调用点，导致报告里的
            //「最终能力等级」永远为空；放在这里可以同时兜住人工收尾与阶段收尾两条链路。
            //
            // 顺序很重要：洞察的事实指纹取自报告四部分内容，区块没补齐就生成洞察，
            // 指纹会基于残缺事实，补完后又得再烧一次模型。
            assessmentReportService.refreshReportSections(event.workflowId());
            assessmentReportService.generateInsights(event.workflowId(), false);
        } catch (Exception e) {
            log.warn("评估报告洞察触发异常，已忽略: workflowId={}",
                    event == null ? null : event.workflowId(), e);
        }
    }
}
