package com.example.matching.listener.assessment;

import com.example.matching.common.enums.StageLifecycleEventType;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.event.CapabilityStageLifecycleEvent;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 全面能力分析报告触发生成监听器（HR 匹配闭环 P2）。
 *
 * <p>挂钩策略（零侵入）：复用既有审核联动事件，事务提交后做轻量「待审清空」检查，
 * 清空才生成报告（fingerprint 幂等防重），任何一次检查失败都不影响主链路。
 * 覆盖两条审核链路：
 * <ul>
 *   <li>单条审核采纳：{@code AbilityChangeEvent}(EMP_ABILITY) 直接携带 empId；</li>
 *   <li>聚合审核/等级确认：{@code CapabilityStageLifecycleEvent}(USER_ACTION_COMPLETED)
 *       携带 workflowId，经 claim group 反查 empId。</li>
 * </ul>
 * batch-review 循环复用单条编排，同样被上述事件覆盖；重复触发由指纹幂等兜底。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityAnalysisReportListener {

    private final CapabilityAnalysisReportService reportService;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAbilityChange(AbilityChangeEvent event) {
        try {
            if (!"EMP_ABILITY".equals(event.getChangeType()) || event.getEntityId() == null) {
                return;
            }
            reportService.generateIfCleared(event.getEntityId());
        } catch (Exception e) {
            log.warn("分析报告触发（能力变更事件）异常，已忽略: empId={}", event.getEntityId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStageLifecycle(CapabilityStageLifecycleEvent event) {
        try {
            if (event.eventType() != StageLifecycleEventType.USER_ACTION_COMPLETED
                || event.workflowId() == null) {
                return;
            }
            PersonAbilityClaimGroup group = claimGroupMapper.selectOne(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                    .eq(PersonAbilityClaimGroup::getWorkflowId, event.workflowId())
                    .last("LIMIT 1"));
            if (group == null || group.getEmpId() == null) {
                return;
            }
            reportService.generateIfCleared(group.getEmpId());
        } catch (Exception e) {
            log.warn("分析报告触发（阶段生命周期事件）异常，已忽略: workflowId={}", event.workflowId(), e);
        }
    }
}
