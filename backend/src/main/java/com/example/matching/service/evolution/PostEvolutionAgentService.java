package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.AgentProgressVO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.entity.evolution.PostEvolutionTask;

/**
 * 岗位演化 Agent 服务接口
 * <p>
 * 负责编排整个岗位演化流程：检索证据、生成信号、生成变更建议。
 *
 * @author system
 */
public interface PostEvolutionAgentService {

    /**
     * 运行岗位演化 Agent
     *
     * @param request Agent 请求
     * @return Agent 结果
     */
    PostEvolutionAgentResult runEvolution(PostEvolutionAgentRequest request);

    /**
     * 运行 Agent 并创建演化任务
     *
     * @param request Agent 请求
     * @return 创建的演化任务
     */
    PostEvolutionTask runEvolutionAndCreateTask(PostEvolutionAgentRequest request);

    /**
     * 获取 Agent 执行进度
     *
     * @param taskId 任务ID
     * @return 执行进度
     */
    AgentProgressVO getAgentProgress(Long taskId);

    /**
     * Execute a queued Agent task outside the HTTP request thread.
     *
     * @param taskId task ID
     * @param request original Agent request
     */
    void executeQueuedEvolution(Long taskId, PostEvolutionAgentRequest request);
}
