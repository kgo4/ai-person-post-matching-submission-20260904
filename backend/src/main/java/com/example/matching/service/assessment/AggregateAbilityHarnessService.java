package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.HarnessBatchItemResultDTO;

import java.util.List;

/**
 * 聚合 Harness 审核服务接口
 * <p>
 * 面试完成后按能力聚合证据，执行一次批量 Harness。
 * 候选人主流程中唯一的 Harness 审核阶段。
 *
 * @author system
 */
public interface AggregateAbilityHarnessService {

    /**
     * 执行聚合 Harness 审核。
     * 8-15 项能力一批，超出按能力域拆分。
     *
     * @param workflowId 工作流ID
     * @param stageRunId 阶段运行ID
     * @return 逐能力审核结果
     */
    List<HarnessBatchItemResultDTO> runAggregateHarness(Long workflowId, Long stageRunId);

    /**
     * 查询工作流的聚合 Harness 结果。
     */
    List<HarnessBatchItemResultDTO> getHarnessResults(Long workflowId);
}
