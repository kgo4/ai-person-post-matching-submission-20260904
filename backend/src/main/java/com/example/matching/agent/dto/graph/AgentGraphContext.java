package com.example.matching.agent.dto.graph;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 图谱预构建上下文（任务级受限子图）。
 * <p>
 * 服务端为当前任务构建完整关系子图并一次性放入 Agent 上下文，
 * Agent 直接读取已计算关系（abilityMatches/gaps/prerequisites），
 * 不再依赖重复图谱 Tool Call，也不自行推理人岗关系。
 */
@Data
public class AgentGraphContext {

    /** 图谱状态：FRESH / STALE / UNAVAILABLE */
    private String status;

    /** 图谱版本 */
    private String graphVersion;

    /**
     * 关系契约：节点和边是服务端预计算的权威事实，Agent 只能读取并引用，
     * 不得根据名称自行补推未提供的关系。
     */
    private String relationshipContract = "PRECOMPUTED_FACTS_ONLY";

    /** Agent 可直接使用的关系类型说明，避免模型把事实关系当作需要推断的语义。 */
    private List<String> relationTypes = new ArrayList<>(List.of(
            "HAS_ABILITY", "HAS_ABILITY_FACT", "REQUIRES", "SUPPORTED_BY",
            "PREREQUISITE_OF", "BELONGS_TO_DOMAIN", "HAS_KNOWLEDGE_NODE",
            "ASKED_IN", "ANSWERED_BY", "FOLLOWED_UP_BY", "LEARN_BY"));

    /** 图谱刷新时间 */
    private java.time.LocalDateTime refreshedAt;

    /** 子图节点 */
    private List<AgentGraphNode> nodes = new ArrayList<>();

    /** 子图边 */
    private List<AgentGraphEdge> edges = new ArrayList<>();

    /** 预计算能力满足关系 */
    private List<AgentAbilityMatchFact> abilityMatches = new ArrayList<>();

    /** 预计算能力差距 */
    private List<AgentAbilityGapFact> gaps = new ArrayList<>();

    /** 已验证员工证据（reviewStatus=VERIFIED 且归属当前员工） */
    private List<AgentVerifiedEvidenceFact> verifiedEvidence = new ArrayList<>();

    /** 预计算前置关系 */
    private List<AgentPrerequisiteFact> prerequisites = new ArrayList<>();

    /** 允许回链的来源引用白名单（如 fact:EMP_ABILITY:12） */
    private Set<String> allowedSourceRefs = new LinkedHashSet<>();

    /** 允许引用的能力标签白名单 */
    private Set<Long> allowedAbilityTagIds = new LinkedHashSet<>();

    /** 公共资料（不得证明员工拥有能力） */
    private List<Object> referenceMaterials = new ArrayList<>();

    /** 该上下文是否可用于关系结论（FRESH 时 true，STALE/UNAVAILABLE 时 false） */
    public boolean isUsable() {
        return "FRESH".equals(status);
    }

    /** 权重工具：边权重缺省 0 */
    public static BigDecimal weightOf(AgentGraphEdge edge) {
        return edge != null && edge.getWeight() != null ? edge.getWeight() : BigDecimal.ZERO;
    }
}
