package com.example.matching.service.assessment;

import com.example.matching.event.CapabilityStageLifecycleEvent;

/**
 * 能力评估生命周期协调器
 * <p>
 * 唯一允许推进/结束人员能力评估工作流状态的组件：
 * <ol>
 *   <li>校验事件对应的 workflowId、stageRunId、stageType；</li>
 *   <li>按 eventId 幂等去重；</li>
 *   <li>用 CAS 更新 PersonCapabilityStageRun.status；</li>
 *   <li>用状态转换表更新 PersonCapabilityWorkflow.status（不允许非法/倒退/跨阶段跳转）；</li>
 *   <li>更新 currentStage、activeStageRunId、updatedTime；</li>
 *   <li>最终失败写入 failedReason，并让前端可执行重试失败阶段；</li>
 *   <li>所有状态变化写审计日志（capability_stage_lifecycle_event_log）。</li>
 * </ol>
 * 业务服务不得直接调用 workflowService.transition / failWorkflow / completeWorkflow。
 *
 * @author system
 */
public interface CapabilityAssessmentLifecycleCoordinator {

    /**
     * 处理一条生命周期事件（幂等）。
     */
    void handle(CapabilityStageLifecycleEvent event);
}
