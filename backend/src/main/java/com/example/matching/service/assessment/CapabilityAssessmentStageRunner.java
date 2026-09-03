package com.example.matching.service.assessment;

/**
 * 能力评估阶段执行器接口
 * <p>
 * 执行单个阶段，调用既有 Resume/Test/Interview/PMS 服务。
 * 异步消费者调用入口。
 *
 * @author system
 */
public interface CapabilityAssessmentStageRunner {

    /**
     * 执行一个阶段运行。
     *
     * @param stageRunId 阶段运行ID
     */
    void runStage(Long stageRunId);
}
