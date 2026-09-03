package com.example.matching.agent.service;

import com.example.matching.agent.dto.graph.AgentGraphContext;

import java.util.Set;

/**
 * Agent 图谱上下文装配器：服务端为当前任务构建完整受限子图，
 * 一次性放入 Agent 上下文；四个使用图谱的 Agent（匹配分析/学习路径/
 * 面试计划/面试观察）统一从这里获取，不各自复制图谱查询逻辑。
 */
public interface AgentGraphContextAssembler {

    /**
     * 匹配分析子图：员工 + 岗位 + 能力模型 + 员工能力（权威源）+
     * 已验证证据 + 预计算匹配/差距 + 前置关系。
     */
    AgentGraphContext buildForMatching(Long empId, Long postId);

    /**
     * 学习路径子图：岗位能力缺口 + 当前/目标等级 + 核心性权重 +
     * 前置能力关系 + 前置能力是否已满足。
     *
     * @param gapTagIds 已计算的能力缺口标签（Agent 输出白名单）
     */
    AgentGraphContext buildForLearningPath(Long empId, Long postId, Set<Long> gapTagIds);

    /**
     * 面试计划子图：当前 session + 员工 + 岗位 + 岗位能力模型 +
     * 员工能力等级 + 差距 + 核心/必填 + 前置关系。
     *
     * @param allowedTagIds 岗位能力白名单
     */
    AgentGraphContext buildForInterviewPlan(Long sessionId, Long empId, Long postId, Set<Long> allowedTagIds);

    /**
     * 面试观察子图：仅当前会话相关内容（session/问题/追问/回答引用/岗位白名单）。
     * 禁止放入其他员工证据、其他会话、全局图谱节点、未审核证据。
     *
     * @param allowedTagIds 当前会话题目能力标签白名单
     */
    AgentGraphContext buildForInterviewObservation(Long sessionId, Set<Long> allowedTagIds);
}
