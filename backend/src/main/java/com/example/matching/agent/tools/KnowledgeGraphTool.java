package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphContextStatus;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识图谱工具 - 供LangChain4j Agent调用
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraphTool {

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final KnowledgeGraphQueryService knowledgeGraphQueryService;

    @Tool("读取员工与岗位的受限能力差距上下文。返回满足、等级不足、缺失、加分能力和已审核证据引用。")
    public GraphMatchContext getMatchGraphContext(Long employeeId, Long postId) {
        Optional<String> empValidation = AgentToolInputValidator.validatePositive("employeeId", employeeId);
        if (empValidation.isPresent()) {
            log.warn("getMatchGraphContext invalid input: {}", empValidation.get());
            return GraphMatchContext.empty(GraphContextStatus.GRAPH_DATA_UNAVAILABLE, employeeId, postId);
        }
        Optional<String> postValidation = AgentToolInputValidator.validatePositive("postId", postId);
        if (postValidation.isPresent()) {
            log.warn("getMatchGraphContext invalid input: {}", postValidation.get());
            return GraphMatchContext.empty(GraphContextStatus.GRAPH_DATA_UNAVAILABLE, employeeId, postId);
        }

        log.info("Agent调用: getMatchGraphContext(employeeId={}, postId={})", employeeId, postId);
        try {
            return knowledgeGraphQueryService.getMatchContext(employeeId, postId);
        } catch (Exception e) {
            log.error("getMatchGraphContext 查询失败: employeeId={}, postId={}", employeeId, postId, e);
            return GraphMatchContext.empty(GraphContextStatus.GRAPH_DATA_UNAVAILABLE, employeeId, postId);
        }
    }

    @Tool("读取某项能力的已审核证据链。结果数量受限，必须以返回的 sourceRefs 作为引用。")
    public GraphAbilityEvidenceContext getAbilityEvidenceContext(Long abilityId, Long employeeId) {
        Optional<String> abValidation = AgentToolInputValidator.validatePositive("abilityId", abilityId);
        if (abValidation.isPresent()) {
            log.warn("getAbilityEvidenceContext invalid input: {}", abValidation.get());
            return new GraphAbilityEvidenceContext(abilityId, null, List.of());
        }
        Optional<String> empValidation = AgentToolInputValidator.validatePositive("employeeId", employeeId);
        if (empValidation.isPresent()) {
            log.warn("getAbilityEvidenceContext invalid input: {}", empValidation.get());
            return new GraphAbilityEvidenceContext(abilityId, null, List.of());
        }

        log.info("Agent调用: getAbilityEvidenceContext(abilityId={}, employeeId={})", abilityId, employeeId);
        try {
            return knowledgeGraphQueryService.getAbilityEvidenceContext(abilityId, employeeId);
        } catch (Exception e) {
            log.error("getAbilityEvidenceContext 查询失败: abilityId={}, employeeId={}", abilityId, employeeId, e);
            return new GraphAbilityEvidenceContext(abilityId, null, List.of());
        }
    }

    @Tool("读取能力的前置学习条件。返回恰好一跳的 PREREQUISITE_OF 关系。abilityIds 最多100个。")
    public Map<String, Object> getLearningPrerequisiteContext(List<Long> abilityIds) {
        Optional<String> validation = AgentToolInputValidator.validateNotEmpty("abilityIds", abilityIds);
        if (validation.isPresent()) {
            log.warn("getLearningPrerequisiteContext invalid input: {}", validation.get());
            return Map.of("available", false, "reason", validation.get());
        }

        log.info("Agent调用: getLearningPrerequisiteContext(abilityIds={})", abilityIds);
        if (abilityIds.size() > 100) {
            return Map.of(
                    "available", false,
                    "reason", "invalid_input: abilityIds size exceeds maximum of 100, got " + abilityIds.size());
        }
        try {
            GraphLearningPrerequisiteContext context = knowledgeGraphQueryService.getLearningPrerequisiteContext(abilityIds);
            return Map.of("available", true, "item", context);
        } catch (Exception e) {
            log.error("getLearningPrerequisiteContext 查询失败", e);
            return Map.of("available", false, "item", List.of(), "reason", "learning_prerequisites_unavailable");
        }
    }

    @Tool("分析人员与岗位的图谱业务关系，包括能力覆盖、差距、证据和关系数量")
    public Map<String, Object> getBusinessGraphAnalysis(Long employeeId, Long postId) {
        if (employeeId == null || postId == null) {
            return Map.of("available", false, "reason", "employeeId and postId are required");
        }
        try {
            return Map.of("available", true,
                    "matchContext", knowledgeGraphQueryService.getMatchContext(employeeId, postId),
                    "postGraph", knowledgeGraphQueryService.getPostCenteredGraph(postId));
        } catch (Exception e) {
            log.warn("getBusinessGraphAnalysis 查询失败: {}", e.getMessage());
            return Map.of("available", false, "reason", "business_graph_analysis_unavailable");
        }
    }

    @Tool("获取节点的关联节点和边")
    public Map<String, Object> getNodeConnections(String nodeKey) {
        Optional<String> validation = AgentToolInputValidator.validateNotEmpty("nodeKey", nodeKey);
        if (validation.isEmpty()) {
            validation = AgentToolInputValidator.validateMaxLength("nodeKey", nodeKey, 200);
        }
        if (validation.isPresent()) {
            log.warn("getNodeConnections invalid input: {}", validation.get());
            return Map.of("available", false, "reason", validation.get());
        }

        log.info("Agent调用: getNodeConnections(nodeKey={})", nodeKey);
        try {
            Map<String, Object> result = new HashMap<>();

            LambdaQueryWrapper<KgGraphNode> nodeWrapper = new LambdaQueryWrapper<>();
            nodeWrapper.eq(KgGraphNode::getNodeKey, nodeKey);
            KgGraphNode node = graphNodeMapper.selectOne(nodeWrapper);

            if (node == null) {
                return Map.of("available", false, "found", false, "reason", "node not found");
            }

            result.put("available", true);
            result.put("found", true);
            result.put("node", toNodeMap(node));

            LambdaQueryWrapper<KgGraphEdge> outEdgeWrapper = new LambdaQueryWrapper<>();
            outEdgeWrapper.eq(KgGraphEdge::getSourceNodeKey, nodeKey).last("LIMIT 100");
            List<KgGraphEdge> outEdges = graphEdgeMapper.selectList(outEdgeWrapper);
            result.put("outEdges", outEdges.stream().map(this::toEdgeMap).collect(Collectors.toList()));

            LambdaQueryWrapper<KgGraphEdge> inEdgeWrapper = new LambdaQueryWrapper<>();
            inEdgeWrapper.eq(KgGraphEdge::getTargetNodeKey, nodeKey).last("LIMIT 100");
            List<KgGraphEdge> inEdges = graphEdgeMapper.selectList(inEdgeWrapper);
            result.put("inEdges", inEdges.stream().map(this::toEdgeMap).collect(Collectors.toList()));

            return result;
        } catch (Exception e) {
            log.error("getNodeConnections 查询失败: nodeKey={}", nodeKey, e);
            return Map.of("available", false, "reason", "node_connections_unavailable");
        }
    }

    @Tool("根据节点类型和关键词搜索节点")
    public Map<String, Object> searchNodes(String nodeType, String keyword) {
        Optional<String> validation = AgentToolInputValidator.validateNotEmpty("keyword", keyword);
        if (validation.isEmpty()) {
            // 修复：关键词长度限制 + LIKE 通配符转义（防 %/_ 前缀通配全表扫描）
            validation = AgentToolInputValidator.validateMaxLength("keyword", keyword, 100);
        }
        if (validation.isPresent()) {
            log.warn("searchNodes invalid input: {}", validation.get());
            return Map.of("available", false, "items", List.of(), "reason", validation.get());
        }

        log.info("Agent调用: searchNodes(nodeType={}, keyword={})", nodeType, keyword);
        try {
            LambdaQueryWrapper<KgGraphNode> wrapper = new LambdaQueryWrapper<>();
            if (nodeType != null && !nodeType.isEmpty()) {
                wrapper.eq(KgGraphNode::getNodeType, nodeType);
            }
            wrapper.like(KgGraphNode::getLabel, AgentToolInputValidator.escapeLike(keyword));

            Page<KgGraphNode> page = new Page<>(1, 20);
            IPage<KgGraphNode> result = graphNodeMapper.selectPage(page, wrapper);

            List<Map<String, Object>> items = result.getRecords().stream()
                    .map(this::toNodeMap)
                    .collect(Collectors.toList());

            return Map.of("available", true, "items", items);
        } catch (Exception e) {
            log.error("searchNodes 查询失败: nodeType={}, keyword={}", nodeType, keyword, e);
            return Map.of("available", false, "items", List.of(), "reason", "node_search_unavailable");
        }
    }

    private Map<String, Object> toNodeMap(KgGraphNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("nodeKey", node.getNodeKey());
        map.put("nodeType", node.getNodeType());
        map.put("refId", node.getRefId());
        map.put("label", node.getLabel());
        map.put("category", node.getCategory());
        map.put("status", node.getStatus());
        return map;
    }

    private Map<String, Object> toEdgeMap(KgGraphEdge edge) {
        Map<String, Object> map = new HashMap<>();
        map.put("edgeKey", edge.getEdgeKey());
        map.put("sourceNodeKey", edge.getSourceNodeKey());
        map.put("targetNodeKey", edge.getTargetNodeKey());
        map.put("edgeType", edge.getEdgeType());
        map.put("weightValue", edge.getWeightValue());
        return map;
    }
}
