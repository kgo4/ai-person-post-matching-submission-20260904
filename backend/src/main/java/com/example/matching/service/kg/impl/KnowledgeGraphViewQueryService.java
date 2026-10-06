package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.matching.service.kg.Neo4jGraphStore;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱视图查询：全景、岗位/员工中心图、能力差距路径、记忆图、时间线。
 * <p>
 * 从 KnowledgeGraphQueryServiceImpl（860+ 行）中拆分的只读视图组件。
 */
@Slf4j
@Service
public class KnowledgeGraphViewQueryService {

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider;

    @Autowired
    public KnowledgeGraphViewQueryService(KgGraphNodeMapper graphNodeMapper,
                                          KgGraphEdgeMapper graphEdgeMapper,
                                          ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider) {
        this.graphNodeMapper = graphNodeMapper;
        this.graphEdgeMapper = graphEdgeMapper;
        this.neo4jGraphStoreProvider = neo4jGraphStoreProvider;
    }

    /** 兼容已有单元测试构造方式，默认不启用 Neo4j。 */
    public KnowledgeGraphViewQueryService(KgGraphNodeMapper graphNodeMapper,
                                          KgGraphEdgeMapper graphEdgeMapper) {
        this(graphNodeMapper, graphEdgeMapper, null);
    }

    private static final int DEFAULT_LIMIT = 300;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    public Map<String, Object> getPanorama(List<String> nodeTypes, String keyword, String category, Integer limit) {
        int effectiveLimit = clampLimit(limit, 20, 120, DEFAULT_LIMIT);
        Neo4jGraphStore neo4j = neo4jGraphStoreProvider == null ? null : neo4jGraphStoreProvider.getIfAvailable();
        if (neo4j != null) {
            Map<String, Object> neo4jResult = neo4j.queryPanorama(nodeTypes, keyword, category, effectiveLimit);
            Object neoNodes = neo4jResult.get("nodes");
            if (Boolean.TRUE.equals(neo4jResult.get("available")) && neoNodes instanceof List<?> list && !list.isEmpty()) {
                return neo4jResult;
            }
            log.info("Neo4j 图谱无可用投影，回退 MySQL: keyword={}, category={}", keyword, category);
        }
        int maxEdges = Math.min(effectiveLimit * 2, 240);
        int maxExtraNodes = effectiveLimit;

        // 构建节点查询条件
        LambdaQueryWrapper<KgGraphNode> nodeQuery = Wrappers.<KgGraphNode>lambdaQuery();
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            nodeQuery.in(KgGraphNode::getNodeType, nodeTypes);
        }
        if (keyword != null && !keyword.isEmpty()) {
            nodeQuery.like(KgGraphNode::getLabel, keyword);
        }
        if (category != null && !category.isEmpty()) {
            nodeQuery.eq(KgGraphNode::getCategory, category);
        }
        nodeQuery.last("LIMIT " + effectiveLimit);

        List<KgGraphNode> nodes = graphNodeMapper.selectList(nodeQuery);

        if (nodes.isEmpty()) {
            return buildEmptyResult();
        }

        // 获取节点key集合
        Set<String> nodeKeys = nodes.stream()
                .map(KgGraphNode::getNodeKey)
                .collect(Collectors.toSet());

        // 查询相关的边（源或目标在节点集合中，避免 LIMIT 导致单类型节点无关联边）
        LambdaQueryWrapper<KgGraphEdge> edgeQuery = Wrappers.<KgGraphEdge>lambdaQuery();
        edgeQuery.and(w -> w.in(KgGraphEdge::getSourceNodeKey, nodeKeys)
                .or()
                .in(KgGraphEdge::getTargetNodeKey, nodeKeys));
        List<KgGraphEdge> edges = graphEdgeMapper.selectList(edgeQuery);

        // 截断边
        if (edges.size() > maxEdges) {
            edges = new ArrayList<>(edges.subList(0, maxEdges));
        }

        // 补全边关联的另一端节点（不在当前结果集中的）
        Set<String> existingKeys = new HashSet<>(nodeKeys);
        Set<String> missingKeys = new HashSet<>();
        for (KgGraphEdge edge : edges) {
            if (!existingKeys.contains(edge.getSourceNodeKey())) missingKeys.add(edge.getSourceNodeKey());
            if (!existingKeys.contains(edge.getTargetNodeKey())) missingKeys.add(edge.getTargetNodeKey());
        }
        if (!missingKeys.isEmpty()) {
            // 截断补全节点
            List<String> truncatedKeys = missingKeys.size() > maxExtraNodes
                    ? new ArrayList<>(missingKeys).subList(0, maxExtraNodes) : new ArrayList<>(missingKeys);
            List<KgGraphNode> extraNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, truncatedKeys));
            nodes = new ArrayList<>(nodes);
            nodes.addAll(extraNodes);

            // 丢弃端点不再存在的边
            Set<String> allKeys = nodes.stream().map(KgGraphNode::getNodeKey).collect(Collectors.toSet());
            edges = edges.stream()
                    .filter(e -> allKeys.contains(e.getSourceNodeKey()) && allKeys.contains(e.getTargetNodeKey()))
                    .collect(Collectors.toList());
        }

        return buildGraphResult(nodes, edges);
    }

    public Map<String, Object> getPostCenteredGraph(Long postId) {
        String postNodeKey = "POST:" + postId;

        // 查询岗位节点
        KgGraphNode postNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, postNodeKey));
        if (postNode == null) {
            return buildEmptyResult();
        }

        // 查询与岗位相关的边
        List<KgGraphEdge> relatedEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, postNodeKey)
                        .or().eq(KgGraphEdge::getTargetNodeKey, postNodeKey));

        // 收集所有相关节点key
        Set<String> relatedNodeKeys = new HashSet<>();
        relatedNodeKeys.add(postNodeKey);
        for (KgGraphEdge edge : relatedEdges) {
            relatedNodeKeys.add(edge.getSourceNodeKey());
            relatedNodeKeys.add(edge.getTargetNodeKey());
        }

        // 查询所有相关节点
        List<KgGraphNode> nodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, relatedNodeKeys));

        // 过滤边（确保两端节点都存在）
        Set<String> existingNodeKeys = nodes.stream()
                .map(KgGraphNode::getNodeKey)
                .collect(Collectors.toSet());
        List<KgGraphEdge> validEdges = relatedEdges.stream()
                .filter(e -> existingNodeKeys.contains(e.getSourceNodeKey())
                        && existingNodeKeys.contains(e.getTargetNodeKey()))
                .collect(Collectors.toList());

        return buildGraphResult(nodes, validEdges);
    }

    public Map<String, Object> getEmployeeCenteredGraph(Long empId) {
        String empNodeKey = "EMPLOYEE:" + empId;

        // 查询员工节点
        KgGraphNode empNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, empNodeKey));
        if (empNode == null) {
            return buildEmptyResult();
        }

        // 查询与员工相关的边
        List<KgGraphEdge> relatedEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, empNodeKey)
                        .or().eq(KgGraphEdge::getTargetNodeKey, empNodeKey));

        // 收集所有相关节点key
        Set<String> relatedNodeKeys = new HashSet<>();
        relatedNodeKeys.add(empNodeKey);
        for (KgGraphEdge edge : relatedEdges) {
            relatedNodeKeys.add(edge.getSourceNodeKey());
            relatedNodeKeys.add(edge.getTargetNodeKey());
        }

        // 查询所有相关节点
        List<KgGraphNode> nodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, relatedNodeKeys));

        // 过滤边
        Set<String> existingNodeKeys = nodes.stream()
                .map(KgGraphNode::getNodeKey)
                .collect(Collectors.toSet());
        List<KgGraphEdge> validEdges = relatedEdges.stream()
                .filter(e -> existingNodeKeys.contains(e.getSourceNodeKey())
                        && existingNodeKeys.contains(e.getTargetNodeKey()))
                .collect(Collectors.toList());

        return buildGraphResult(nodes, validEdges);
    }

    public Map<String, Object> getAbilityGapPath(Long empId, Long postId) {
        String empNodeKey = "EMPLOYEE:" + empId;
        String postNodeKey = "POST:" + postId;

        // 查询员工和岗位节点
        KgGraphNode empNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, empNodeKey));
        KgGraphNode postNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, postNodeKey));

        if (empNode == null || postNode == null) {
            return buildEmptyResult();
        }

        // 查询员工能力
        List<KgGraphEdge> empAbilities = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, empNodeKey)
                        .in(KgGraphEdge::getEdgeType, "HAS_ABILITY", "HAS_ABILITY_FACT"));

        // 查询岗位要求
        List<KgGraphEdge> postRequirements = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, postNodeKey)
                        .eq(KgGraphEdge::getEdgeType, "REQUIRES"));

        // 收集员工能力标签key
        Set<String> empAbilityKeys = empAbilities.stream()
                .map(KgGraphEdge::getTargetNodeKey)
                .collect(Collectors.toSet());

        // 构建结果
        List<KgGraphNode> nodes = new ArrayList<>();
        nodes.add(empNode);
        nodes.add(postNode);

        List<KgGraphEdge> edges = new ArrayList<>();
        Set<String> nodeKeys = new HashSet<>();
        nodeKeys.add(empNodeKey);
        nodeKeys.add(postNodeKey);

        // 添加岗位要求边和节点
        for (KgGraphEdge req : postRequirements) {
            String abilityKey = req.getTargetNodeKey();
            edges.add(req);
            nodeKeys.add(abilityKey);

            // 如果员工也有这个能力，添加员工能力边
            if (empAbilityKeys.contains(abilityKey)) {
                KgGraphEdge empEdge = empAbilities.stream()
                        .filter(e -> e.getTargetNodeKey().equals(abilityKey))
                        .findFirst().orElse(null);
                if (empEdge != null) {
                    edges.add(empEdge);
                }
            }
        }

        // 查询所有涉及的节点
        List<KgGraphNode> allNodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, nodeKeys));
        nodes.clear();
        nodes.addAll(allNodes);

        return buildGraphResult(nodes, edges);
    }

    public Map<String, Object> getMemoryGraph(Integer limit) {
        // 修复：limit 未 clamp，外部可传 100000+ 拉全量进内存；上限 500
        int effectiveLimit = clampLimit(limit, 100, 500, 100);

        // 查询记忆相关节点类型
        List<String> memoryNodeTypes = List.of("AGENT_MEMORY", "GOVERNANCE_EVENT", "ABILITY", "SOURCE_SYSTEM", "EVIDENCE");

        LambdaQueryWrapper<KgGraphNode> nodeQuery = Wrappers.<KgGraphNode>lambdaQuery();
        nodeQuery.in(KgGraphNode::getNodeType, memoryNodeTypes);
        nodeQuery.last("LIMIT " + effectiveLimit);
        List<KgGraphNode> nodes = graphNodeMapper.selectList(nodeQuery);

        if (nodes.isEmpty()) {
            return buildEmptyResult();
        }

        Set<String> nodeKeys = nodes.stream()
                .map(KgGraphNode::getNodeKey)
                .collect(Collectors.toSet());

        // 查询记忆相关的边类型
        List<String> memoryEdgeTypes = List.of("GENERATED_MEMORY", "USED_BY_AGENT", "NORMALIZED_TO", "SUPPORTED_BY", "HAS_ABILITY", "HAS_ABILITY_FACT");

        LambdaQueryWrapper<KgGraphEdge> edgeQuery = Wrappers.<KgGraphEdge>lambdaQuery();
        edgeQuery.in(KgGraphEdge::getEdgeType, memoryEdgeTypes);
        List<KgGraphEdge> allMemoryEdges = graphEdgeMapper.selectList(edgeQuery);

        // 过滤：两端节点都在当前节点集合中
        List<KgGraphEdge> edges = allMemoryEdges.stream()
                .filter(e -> nodeKeys.contains(e.getSourceNodeKey()) && nodeKeys.contains(e.getTargetNodeKey()))
                .collect(Collectors.toList());

        // 收集边涉及的额外节点
        Set<String> extraNodeKeys = new HashSet<>();
        for (KgGraphEdge edge : allMemoryEdges) {
            if (nodeKeys.contains(edge.getSourceNodeKey()) || nodeKeys.contains(edge.getTargetNodeKey())) {
                extraNodeKeys.add(edge.getSourceNodeKey());
                extraNodeKeys.add(edge.getTargetNodeKey());
            }
        }
        extraNodeKeys.removeAll(nodeKeys);

        if (!extraNodeKeys.isEmpty()) {
            List<KgGraphNode> extraNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, extraNodeKeys));
            nodes = new ArrayList<>(nodes);
            nodes.addAll(extraNodes);

            // 重新过滤边
            Set<String> allNodeKeys = nodes.stream()
                    .map(KgGraphNode::getNodeKey)
                    .collect(Collectors.toSet());
            edges = allMemoryEdges.stream()
                    .filter(e -> allNodeKeys.contains(e.getSourceNodeKey()) && allNodeKeys.contains(e.getTargetNodeKey()))
                    .collect(Collectors.toList());
        }

        return buildGraphResult(nodes, edges);
    }

    public Map<String, Object> getTimeline(Integer limit) {
        // 修复：limit 未 clamp，外部可传 100000+ 拉全量进内存；上限 500
        int effectiveLimit = clampLimit(limit, 50, 500, 50);

        // 获取最近创建的节点
        LambdaQueryWrapper<KgGraphNode> recentNodeQuery = Wrappers.<KgGraphNode>lambdaQuery();
        recentNodeQuery.orderByDesc(KgGraphNode::getCreatedTime);
        recentNodeQuery.last("LIMIT " + effectiveLimit);
        List<KgGraphNode> recentNodes = graphNodeMapper.selectList(recentNodeQuery);

        // 获取最近创建的边
        LambdaQueryWrapper<KgGraphEdge> recentEdgeQuery = Wrappers.<KgGraphEdge>lambdaQuery();
        recentEdgeQuery.orderByDesc(KgGraphEdge::getCreatedTime);
        recentEdgeQuery.last("LIMIT " + effectiveLimit);
        List<KgGraphEdge> recentEdges = graphEdgeMapper.selectList(recentEdgeQuery);

        // 构建时间线事件
        List<Map<String, Object>> events = new ArrayList<>();

        for (KgGraphNode node : recentNodes) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "NODE_ADDED");
            event.put("timestamp", node.getCreatedTime());
            event.put("nodeKey", node.getNodeKey());
            event.put("nodeType", node.getNodeType());
            event.put("label", node.getLabel());
            event.put("category", node.getCategory());
            events.add(event);
        }

        for (KgGraphEdge edge : recentEdges) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "EDGE_ADDED");
            event.put("timestamp", edge.getCreatedTime());
            event.put("edgeKey", edge.getEdgeKey());
            event.put("edgeType", edge.getEdgeType());
            event.put("source", edge.getSourceNodeKey());
            event.put("target", edge.getTargetNodeKey());
            events.add(event);
        }

        // 按时间排序
        events.sort((a, b) -> {
            Object ta = a.get("timestamp");
            Object tb = b.get("timestamp");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ((Comparable) tb).compareTo(ta);
        });

        // 截取
        if (events.size() > effectiveLimit) {
            events = events.subList(0, effectiveLimit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", events);
        result.put("total", events.size());
        return result;
    }

    // ===================== 受限上下文查询 =====================

    private int clampLimit(Integer limit, int min, int max, int defaultVal) {
        if (limit == null) return defaultVal;
        return Math.max(min, Math.min(max, limit));
    }

    private Map<String, Object> buildGraphResult(List<KgGraphNode> nodes, List<KgGraphEdge> edges) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);

        // 转换节点
        List<Map<String, Object>> nodeDtos = nodes.stream()
                .map(this::convertNode)
                .collect(Collectors.toList());
        result.put("nodes", nodeDtos);

        // 转换边
        List<Map<String, Object>> edgeDtos = edges.stream()
                .map(this::convertEdge)
                .collect(Collectors.toList());
        result.put("edges", edgeDtos);

        // 统计信息
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodeCount", nodes.size());
        stats.put("edgeCount", edges.size());
        stats.put("postCount", nodes.stream().filter(n -> "POST".equals(n.getNodeType())).count());
        stats.put("abilityCount", nodes.stream().filter(n -> "ABILITY".equals(n.getNodeType())).count());
        stats.put("evidenceCount", nodes.stream().filter(n -> "EVIDENCE".equals(n.getNodeType())).count());
        stats.put("evolutionCount", nodes.stream().filter(n -> "EVOLUTION_EVENT".equals(n.getNodeType())).count());
        stats.put("knowledgeDomainCount", nodes.stream()
                .filter(n -> "KNOWLEDGE_DOMAIN".equals(n.getNodeType())).count());
        stats.put("knowledgeNodeCount", nodes.stream()
                .filter(n -> "KNOWLEDGE_NODE".equals(n.getNodeType())).count());
        stats.put("prerequisiteCount", edges.stream()
                .filter(e -> "PREREQUISITE_OF".equals(e.getEdgeType())).count());
        result.put("stats", stats);

        return result;
    }

    private Map<String, Object> buildEmptyResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("nodes", Collections.emptyList());
        result.put("edges", Collections.emptyList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("nodeCount", 0);
        stats.put("edgeCount", 0);
        stats.put("postCount", 0);
        stats.put("abilityCount", 0);
        stats.put("evidenceCount", 0);
        stats.put("evolutionCount", 0);
        stats.put("knowledgeDomainCount", 0);
        stats.put("knowledgeNodeCount", 0);
        stats.put("prerequisiteCount", 0);
        result.put("stats", stats);
        return result;
    }

    private Map<String, Object> convertNode(KgGraphNode node) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", node.getNodeKey());
        dto.put("label", node.getLabel());
        dto.put("type", node.getNodeType());
        dto.put("category", node.getCategory());
        dto.put("weight", node.getWeightValue());
        dto.put("level", node.getLevelValue());
        dto.put("status", node.getStatus());
        dto.put("metadata", parseMetadata(node.getMetadataJson()));
        return dto;
    }

    private Map<String, Object> convertEdge(KgGraphEdge edge) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", edge.getEdgeKey());
        dto.put("source", edge.getSourceNodeKey());
        dto.put("target", edge.getTargetNodeKey());
        dto.put("type", edge.getEdgeType());
        dto.put("weight", edge.getWeightValue());
        dto.put("confidence", edge.getConfidenceScore());
        dto.put("metadata", parseMetadata(edge.getMetadataJson()));
        return dto;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            log.debug("解析图谱元数据失败，返回空对象: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
