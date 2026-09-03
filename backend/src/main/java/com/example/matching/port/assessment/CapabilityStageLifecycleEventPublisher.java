package com.example.matching.port.assessment;

import com.example.matching.event.CapabilityStageLifecycleEvent;

/**
 * 能力评估阶段生命周期事件发布器（端口）
 * <p>
 * 业务服务（简历/测试/面试/Harness/等级确认）不再直接推进工作流状态，
 * 统一通过本发布器发布"发生了什么"的生命周期事件。
 * <p>
 * 事件必须经 Outbox 持久化后投递 RabbitMQ（可靠链路）；进程内事件仅作加速，
 * 协调器以 eventId 幂等去重，重复投递不会重复推进状态。
 * <p>
 * 接口置于 port 包以解耦：employee/interview 等业务服务只依赖端口，
 * 实现位于 service.assessment.impl，避免跨域模块循环依赖。
 *
 * @author system
 */
public interface CapabilityStageLifecycleEventPublisher {

    /**
     * 发布生命周期事件（在业务事务内调用）。
     */
    void publish(CapabilityStageLifecycleEvent event);
}
