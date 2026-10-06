package com.example.matching.event;

import com.example.matching.dto.evolution.PostEvolutionAgentRequest;

/**
 * 岗位演化 Agent 任务在数据库提交后的投递事件。
 */
public record PostEvolutionAgentQueuedEvent(Long taskId, PostEvolutionAgentRequest request) {
}
