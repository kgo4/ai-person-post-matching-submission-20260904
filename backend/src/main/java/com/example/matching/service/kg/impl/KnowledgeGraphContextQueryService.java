package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.kg.context.*;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱 Agent 上下文查询：匹配上下文、证据链、学习前置条件。
 * <p>
 * 从 KnowledgeGraphQueryServiceImpl（860+ 行）中拆分的只读上下文组件。
 */
@Slf4j
@Service
public class KnowledgeGraphContextQueryService {

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;

    @Autowired
    public KnowledgeGraphContextQueryService(KgGraphNodeMapper graphNodeMapper,
                                             KgGraphEdgeMapper graphEdgeMapper,
                                             PostQueryPort postQueryPort,
                                             TalentQueryPort talentQueryPort) {
        this.graphNodeMapper = graphNodeMapper;
        this.graphEdgeMapper = graphEdgeMapper;
        this.postQueryPort = postQueryPort;
        this.talentQueryPort = talentQueryPort;
    }

    /** Backward-compatible constructor for isolated graph tests. */
    public KnowledgeGraphContextQueryService(KgGraphNodeMapper graphNodeMapper,
                                             KgGraphEdgeMapper graphEdgeMapper) {
        this(graphNodeMapper, graphEdgeMapper, null, null);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final int MAX_MATCH_ABILITIES = 30;
    private static final int MAX_EVIDENCE_PER_ABILITY = 5;
    private static final int MAX_PREREQUISITE_NODES = 20;
    private static final int MAX_LEARNING_RESOURCES = 10;
    private static final String VERIFIED_EVIDENCE_STATUS = "VERIFIED";
    public GraphMatchContext getMatchContext(Long employeeId, Long postId) {
        // 人员/岗位匹配上下文以业务正式表为唯一权威来源；Neo4j 仅作为可选关系增强。
        GraphMatchContext businessContext = buildBusinessMatchContext(employeeId, postId);
        if (businessContext != null && !businessContext.abilities().isEmpty()) {
            return businessContext;
        }
        String empKey = "EMPLOYEE:" + employeeId;
        String postKey = "POST:" + postId;

        KgGraphNode empNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, empKey));
        KgGraphNode postNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, postKey));

        if (empNode == null || postNode == null) {
            GraphMatchContext fallback = buildBusinessMatchContext(employeeId, postId);
            if (fallback != null) return fallback;
            return GraphMatchContext.empty(empNode == null
                    ? GraphContextStatus.EMPLOYEE_NOT_FOUND : GraphContextStatus.POST_NOT_FOUND, employeeId, postId);
        }

        // 查询岗位要求
        List<KgGraphEdge> requirements = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, postKey)
                        .eq(KgGraphEdge::getEdgeType, "REQUIRES"));

        // The graph is a projection, not the source of truth. During rebuilds it
        // can legitimately have no edges while the business tables are populated.
        // Build the same compact context from those tables instead of returning 0.
        if (requirements.isEmpty()) {
            GraphMatchContext fallback = buildBusinessMatchContext(employeeId, postId);
            if (fallback != null) return fallback;
        }

        if (requirements.isEmpty()) {
            return new GraphMatchContext(GraphContextStatus.AVAILABLE, employeeId, empNode.getLabel(),
                    postId, postNode.getLabel(), null, LocalDateTime.now(), List.of());
        }

        // 查询员工能力
        List<KgGraphEdge> empAbilities = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, empKey)
                        .in(KgGraphEdge::getEdgeType, "HAS_ABILITY", "HAS_ABILITY_FACT"));
        if (empAbilities.isEmpty()) {
            GraphMatchContext fallback = buildBusinessMatchContext(employeeId, postId);
            if (fallback != null && !fallback.abilities().isEmpty()) return fallback;
        }
        Map<String, KgGraphEdge> empAbilityMap = empAbilities.stream()
                .collect(Collectors.toMap(KgGraphEdge::getTargetNodeKey, e -> e, (a, b) -> a));
        Map<String, KgGraphEdge> empAbilityNameMap = empAbilities.stream()
                .filter(edge -> {
                    Object name = parseMetadata(edge.getMetadataJson()).get("abilityName");
                    return name != null && !name.toString().isBlank();
                })
                .collect(Collectors.toMap(edge -> normalizeName(
                                String.valueOf(parseMetadata(edge.getMetadataJson()).get("abilityName"))),
                        e -> e, (a, b) -> a));

        // 收集能力节点
        Set<String> abilityKeys = requirements.stream()
                .map(KgGraphEdge::getTargetNodeKey).collect(Collectors.toSet());
        empAbilities.forEach(e -> abilityKeys.add(e.getTargetNodeKey()));
        List<KgGraphNode> abilityNodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, abilityKeys));
        Map<String, KgGraphNode> abilityNodeMap = abilityNodes.stream()
                .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));

        // Query evidence attached to the requested abilities, then intersect it with
        // evidence explicitly attached to this employee.  EMP_ABILITY evidence is
        // projected to both nodes, so ability edges alone are not employee-safe.
        Set<String> abilityNodeIds = abilityNodes.stream()
                .map(KgGraphNode::getNodeKey).collect(Collectors.toSet());
        List<KgGraphEdge> abilityEvidenceEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .in(KgGraphEdge::getSourceNodeKey, abilityNodeIds)
                        .eq(KgGraphEdge::getEdgeType, "SUPPORTED_BY"));
        Set<String> employeeEvidenceKeys = graphEdgeMapper.selectList(
                        Wrappers.<KgGraphEdge>lambdaQuery()
                                .eq(KgGraphEdge::getSourceNodeKey, empKey)
                                .eq(KgGraphEdge::getEdgeType, "SUPPORTED_BY"))
                .stream()
                .map(KgGraphEdge::getTargetNodeKey)
                .collect(Collectors.toSet());
        List<KgGraphEdge> evidenceEdges = abilityEvidenceEdges.stream()
                .filter(edge -> employeeEvidenceKeys.contains(edge.getTargetNodeKey()))
                .toList();

        // 收集证据节点
        Set<String> evidenceKeys = evidenceEdges.stream()
                .map(KgGraphEdge::getTargetNodeKey).collect(Collectors.toSet());
        Map<String, KgGraphNode> evidenceNodeMap = Collections.emptyMap();
        if (!evidenceKeys.isEmpty()) {
            List<KgGraphNode> evidenceNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, evidenceKeys));
            evidenceNodeMap = evidenceNodes.stream()
                    .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));
        }

        // 按 abilityKey 分组证据
        Map<String, List<KgGraphEdge>> evidenceByAbility = evidenceEdges.stream()
                .collect(Collectors.groupingBy(KgGraphEdge::getSourceNodeKey));

        // 构建能力上下文列表
        List<GraphMatchAbilityContext> abilityContexts = new ArrayList<>();
        String graphVersion = graphVersionFromEdges(requirements, empAbilities, evidenceEdges);
        LocalDateTime graphRefreshedAt = latestEdgeTime(requirements, empAbilities, evidenceEdges);

        for (KgGraphEdge req : requirements) {
            String abilityKey = req.getTargetNodeKey();
            KgGraphNode abilityNode = abilityNodeMap.get(abilityKey);
            if (abilityNode == null) continue;

            Map<String, Object> reqMeta = parseMetadata(req.getMetadataJson());
            boolean required = Integer.valueOf(1).equals(asInteger(reqMeta.get("isRequired")));
            boolean core = reqMeta.containsKey("isCore") && Integer.valueOf(1).equals(asInteger(reqMeta.get("isCore")));
            int requiredLevel = asInteger(reqMeta.get("minRequiredLevel"), 0);
            BigDecimal weight = req.getWeightValue();

            KgGraphEdge empAbility = empAbilityMap.get(abilityKey);
            if (empAbility == null && abilityNode != null) {
                empAbility = empAbilityNameMap.get(normalizeName(abilityNode.getLabel()));
            }
            GraphMatchState state = matchState(req, empAbility);

            Integer masteryLevel = null;
            if (empAbility != null) {
                Map<String, Object> empMeta = parseMetadata(empAbility.getMetadataJson());
                masteryLevel = asInteger(empMeta.get("masteryLevel"), 0);
            }

            // 证据
            List<KgGraphEdge> rawEvidence = evidenceByAbility.getOrDefault(abilityKey, List.of());
            List<GraphEvidenceContext> evidence = buildEvidenceContexts(rawEvidence, evidenceNodeMap, graphVersion);

            abilityContexts.add(new GraphMatchAbilityContext(
                    abilityNode.getRefId(), abilityNode.getLabel(), weight,
                    requiredLevel, masteryLevel, required, core, state, evidence));
        }

        // 排序：核心必须 > 必须 > 降序权重 > 能力ID
        abilityContexts.sort((a, b) -> {
            int cmp = Boolean.compare(b.core(), a.core());
            if (cmp != 0) return cmp;
            cmp = Boolean.compare(b.required(), a.required());
            if (cmp != 0) return cmp;
            BigDecimal wa = Objects.requireNonNullElse(a.weight(), BigDecimal.ZERO);
            BigDecimal wb = Objects.requireNonNullElse(b.weight(), BigDecimal.ZERO);
            int wcmp = wb.compareTo(wa); // 降序
            if (wcmp != 0) return wcmp;
            return Long.compare(
                    Objects.requireNonNullElse(a.abilityId(), 0L),
                    Objects.requireNonNullElse(b.abilityId(), 0L));
        });

        // 截断
        if (abilityContexts.size() > MAX_MATCH_ABILITIES) {
            abilityContexts = new ArrayList<>(abilityContexts.subList(0, MAX_MATCH_ABILITIES));
        }

        return new GraphMatchContext(GraphContextStatus.AVAILABLE,
                employeeId, empNode.getLabel(), postId, postNode.getLabel(),
                graphVersion, graphRefreshedAt, abilityContexts);
    }

    private GraphMatchContext buildBusinessMatchContext(Long employeeId, Long postId) {
        if (postQueryPort == null || talentQueryPort == null || employeeId == null || postId == null) return null;
        PostQueryPort.PostDTO post = postQueryPort.getPostById(postId);
        TalentQueryPort.EmployeeDTO employee = talentQueryPort.getEmployeeById(employeeId);
        if (post == null) return null;
        if (employee == null) return GraphMatchContext.empty(GraphContextStatus.EMPLOYEE_NOT_FOUND, employeeId, postId);

        List<PostQueryPort.PostAbilityDTO> requirements = postQueryPort.listRequirementsByPostId(postId);
        List<TalentQueryPort.EmployeeAbilityDTO> employeeAbilities = talentQueryPort.listAbilitiesByEmpId(employeeId);
        Map<String, TalentQueryPort.EmployeeAbilityDTO> byName = new HashMap<>();
        Map<Long, TalentQueryPort.EmployeeAbilityDTO> byTagId = new HashMap<>();
        for (TalentQueryPort.EmployeeAbilityDTO ability : employeeAbilities) {
            if (ability == null || ability.abilityName() == null || ability.abilityName().isBlank()) continue;
            byName.putIfAbsent(normalizeName(ability.abilityName()), ability);
            if (ability.tagId() != null) byTagId.putIfAbsent(ability.tagId(), ability);
        }
        List<GraphMatchAbilityContext> contexts = new ArrayList<>();
        for (PostQueryPort.PostAbilityDTO requirement : requirements) {
            if (requirement == null) continue;
            String abilityName = requirement.abilityName();
            if (abilityName == null || abilityName.isBlank()) {
                abilityName = "能力#" + (requirement.id() != null ? requirement.id() : "unknown");
            }
            TalentQueryPort.EmployeeAbilityDTO matched = byName.get(normalizeName(abilityName));
            if (matched == null && requirement.tagId() != null) matched = byTagId.get(requirement.tagId());
            boolean required = Integer.valueOf(1).equals(requirement.isRequired());
            boolean core = Integer.valueOf(1).equals(requirement.isCore());
            int requiredLevel = requirement.minRequiredLevel() == null ? 0 : requirement.minRequiredLevel();
            GraphMatchState state;
            Integer mastery = matched == null ? null : matched.masteryLevel();
            if (!required) state = GraphMatchState.BONUS;
            else if (matched == null) state = GraphMatchState.MISSING;
            else if (mastery == null || mastery < requiredLevel) state = GraphMatchState.LEVEL_GAP;
            else state = GraphMatchState.SATISFIED;
            contexts.add(new GraphMatchAbilityContext(requirement.id(), abilityName,
                    requirement.weight(), requiredLevel, mastery, required, core, state, List.of()));
        }
        contexts.sort((a, b) -> {
            int cmp = Boolean.compare(b.core(), a.core());
            if (cmp != 0) return cmp;
            cmp = Boolean.compare(b.required(), a.required());
            if (cmp != 0) return cmp;
            return Objects.requireNonNullElse(b.weight(), BigDecimal.ZERO)
                    .compareTo(Objects.requireNonNullElse(a.weight(), BigDecimal.ZERO));
        });
        if (contexts.size() > MAX_MATCH_ABILITIES) contexts = new ArrayList<>(contexts.subList(0, MAX_MATCH_ABILITIES));
        return new GraphMatchContext(GraphContextStatus.AVAILABLE, employeeId, employee.realName(),
                postId, post.postName(), "business-fallback", LocalDateTime.now(), contexts);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    public GraphAbilityEvidenceContext getAbilityEvidenceContext(Long abilityId, Long employeeId) {
        String abilityKey = "ABILITY:" + abilityId;
        KgGraphNode abilityNode = graphNodeMapper.selectOne(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, abilityKey));

        // Untagged employee abilities are represented as ABILITY_FACT nodes and
        // still have a valid evidence chain; tagId is not required to read it.
        if (abilityNode == null) {
            abilityKey = "EMP_ABILITY:" + abilityId;
            abilityNode = graphNodeMapper.selectOne(
                    Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, abilityKey));
        }

        if (abilityNode == null) {
            return new GraphAbilityEvidenceContext(abilityId, null, List.of());
        }

        // Query ability evidence first. When an employee is supplied, only evidence
        // also linked from that employee is eligible for the compact context.
        List<KgGraphEdge> abilityEvidenceEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .eq(KgGraphEdge::getSourceNodeKey, abilityKey)
                        .eq(KgGraphEdge::getEdgeType, "SUPPORTED_BY"));
        List<KgGraphEdge> evidenceEdges = abilityEvidenceEdges;
        if (employeeId != null) {
            Set<String> employeeEvidenceKeys = graphEdgeMapper.selectList(
                            Wrappers.<KgGraphEdge>lambdaQuery()
                                    .eq(KgGraphEdge::getSourceNodeKey, "EMPLOYEE:" + employeeId)
                                    .eq(KgGraphEdge::getEdgeType, "SUPPORTED_BY"))
                    .stream()
                    .map(KgGraphEdge::getTargetNodeKey)
                    .collect(Collectors.toSet());
            evidenceEdges = abilityEvidenceEdges.stream()
                    .filter(edge -> employeeEvidenceKeys.contains(edge.getTargetNodeKey()))
                    .toList();
        }

        // 收集证据节点
        Set<String> evidenceKeys = evidenceEdges.stream()
                .map(KgGraphEdge::getTargetNodeKey).collect(Collectors.toSet());
        Map<String, KgGraphNode> evidenceNodeMap = Collections.emptyMap();
        if (!evidenceKeys.isEmpty()) {
            List<KgGraphNode> evidenceNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, evidenceKeys));
            evidenceNodeMap = evidenceNodes.stream()
                    .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));
        }

        List<GraphEvidenceContext> evidence = buildEvidenceContexts(evidenceEdges, evidenceNodeMap, null);
        return new GraphAbilityEvidenceContext(abilityId, abilityNode.getLabel(), evidence);
    }

    public GraphLearningPrerequisiteContext getLearningPrerequisiteContext(List<Long> abilityIds) {
        if (abilityIds == null || abilityIds.isEmpty()) {
            return new GraphLearningPrerequisiteContext(List.of(), List.of());
        }

        List<String> abilityKeys = abilityIds.stream()
                .map(id -> "ABILITY:" + id).collect(Collectors.toList());

        // 查询能力节点
        List<KgGraphNode> abilityNodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, abilityKeys));
        Map<String, KgGraphNode> abilityNodeMap = abilityNodes.stream()
                .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));

        if (abilityNodes.isEmpty()) {
            return new GraphLearningPrerequisiteContext(abilityIds, List.of());
        }

        // 查询 PREREQUISITE_OF 边（恰好一跳）
        Set<String> abilityNodeKeys = abilityNodes.stream()
                .map(KgGraphNode::getNodeKey).collect(Collectors.toSet());
        List<KgGraphEdge> domainEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .in(KgGraphEdge::getSourceNodeKey, abilityNodeKeys)
                        .eq(KgGraphEdge::getEdgeType, "BELONGS_TO_DOMAIN"));

        if (domainEdges.isEmpty()) {
            return new GraphLearningPrerequisiteContext(abilityIds, List.of());
        }

        // 收集前置能力节点
        Map<String, List<KgGraphEdge>> domainsByAbility = domainEdges.stream()
                .collect(Collectors.groupingBy(KgGraphEdge::getSourceNodeKey));
        Set<String> domainKeys = domainEdges.stream()
                .map(KgGraphEdge::getTargetNodeKey).collect(Collectors.toSet());
        List<KgGraphEdge> domainKnowledgeEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .in(KgGraphEdge::getSourceNodeKey, domainKeys)
                        .eq(KgGraphEdge::getEdgeType, "HAS_KNOWLEDGE_NODE"));
        if (domainKnowledgeEdges.isEmpty()) {
            return new GraphLearningPrerequisiteContext(abilityIds, List.of());
        }
        Map<String, List<String>> knowledgeNodesByDomain = domainKnowledgeEdges.stream()
                .collect(Collectors.groupingBy(KgGraphEdge::getSourceNodeKey,
                        Collectors.mapping(KgGraphEdge::getTargetNodeKey, Collectors.toList())));
        Map<String, List<KgGraphNode>> abilitiesByKnowledgeNode = new LinkedHashMap<>();
        for (KgGraphNode abilityNode : abilityNodes) {
            for (KgGraphEdge domainEdge : domainsByAbility.getOrDefault(abilityNode.getNodeKey(), List.of())) {
                for (String knowledgeNodeKey : knowledgeNodesByDomain.getOrDefault(domainEdge.getTargetNodeKey(), List.of())) {
                    abilitiesByKnowledgeNode.computeIfAbsent(knowledgeNodeKey, ignored -> new ArrayList<>()).add(abilityNode);
                }
            }
        }
        List<KgGraphEdge> prerequisiteEdges = graphEdgeMapper.selectList(
                Wrappers.<KgGraphEdge>lambdaQuery()
                        .in(KgGraphEdge::getTargetNodeKey, abilitiesByKnowledgeNode.keySet())
                        .eq(KgGraphEdge::getEdgeType, "PREREQUISITE_OF"));
        if (prerequisiteEdges.isEmpty()) {
            return new GraphLearningPrerequisiteContext(abilityIds, List.of());
        }

        Set<String> prereqKeys = prerequisiteEdges.stream()
                .map(KgGraphEdge::getSourceNodeKey).collect(Collectors.toSet());
        List<KgGraphNode> prereqNodes = graphNodeMapper.selectList(
                Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, prereqKeys));
        Map<String, KgGraphNode> prereqNodeMap = prereqNodes.stream()
                .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));

        // 构建前置条件节点
        List<GraphLearningPrerequisiteContext.PrerequisiteNode> prerequisites = new ArrayList<>();
        for (KgGraphEdge edge : prerequisiteEdges) {
            KgGraphNode prereqNode = prereqNodeMap.get(edge.getSourceNodeKey());
            if (prereqNode == null) continue;

            Map<String, Object> edgeMeta = parseMetadata(edge.getMetadataJson());
            String graphVersion = asString(edgeMeta.get("graphVersion"));
            List<String> sourceRefs = parseSourceRefs(edgeMeta.get("sourceRefs"));

            for (KgGraphNode targetAbility : abilitiesByKnowledgeNode.getOrDefault(edge.getTargetNodeKey(), List.of())) {
                prerequisites.add(new GraphLearningPrerequisiteContext.PrerequisiteNode(
                        targetAbility.getRefId(), targetAbility.getLabel(),
                        prereqNode.getRefId(), prereqNode.getLabel(),
                        edge.getEdgeType(), sourceRefs, graphVersion));
            }
        }

        // 截断
        if (prerequisites.size() > MAX_PREREQUISITE_NODES) {
            prerequisites = prerequisites.subList(0, MAX_PREREQUISITE_NODES);
        }

        return new GraphLearningPrerequisiteContext(abilityIds, prerequisites);
    }

    // ===================== 私有辅助方法 =====================

    private GraphMatchState matchState(KgGraphEdge requirement, KgGraphEdge employeeAbility) {
        Map<String, Object> requirementMeta = parseMetadata(requirement.getMetadataJson());
        boolean required = Integer.valueOf(1).equals(asInteger(requirementMeta.get("isRequired")));
        if (!required) return GraphMatchState.BONUS;
        if (employeeAbility == null) return GraphMatchState.MISSING;
        int requiredLevel = asInteger(requirementMeta.get("minRequiredLevel"), 0);
        int mastery = asInteger(parseMetadata(employeeAbility.getMetadataJson()).get("masteryLevel"), 0);
        return mastery >= requiredLevel ? GraphMatchState.SATISFIED : GraphMatchState.LEVEL_GAP;
    }

    private List<GraphEvidenceContext> buildEvidenceContexts(
            List<KgGraphEdge> edges, Map<String, KgGraphNode> evidenceNodeMap, String fallbackVersion) {
        List<GraphEvidenceContext> result = new ArrayList<>();
        for (KgGraphEdge edge : edges) {
            Map<String, Object> meta = parseMetadata(edge.getMetadataJson());
            String evidenceStatus = asString(meta.get("evidenceStatus"));
            // Evidence records have their own review lifecycle. Missing or generic
            // relation review states must never make evidence readable.
            if (!VERIFIED_EVIDENCE_STATUS.equals(evidenceStatus)) continue;

            KgGraphNode evNode = evidenceNodeMap.get(edge.getTargetNodeKey());
            List<String> sourceRefs = parseSourceRefs(meta.get("sourceRefs"));
            String graphVersion = asString(meta.get("graphVersion"));
            if (graphVersion == null) graphVersion = fallbackVersion;

            result.add(new GraphEvidenceContext(
                    evNode != null ? evNode.getRefId() : null,
                    evNode != null ? evNode.getLabel() : edge.getTargetNodeKey(),
                    edge.getEdgeType(),
                    edge.getConfidenceScore() != null ? edge.getConfidenceScore().doubleValue() : null,
                    evidenceStatus, sourceRefs, graphVersion,
                    edge.getCreatedTime()));
        }
        // 按置信度降序排序
        result.sort((a, b) -> Double.compare(
                Objects.requireNonNullElse(b.confidence(), 0.0),
                Objects.requireNonNullElse(a.confidence(), 0.0)));
        // 截断
        if (result.size() > MAX_EVIDENCE_PER_ABILITY) {
            result = new ArrayList<>(result.subList(0, MAX_EVIDENCE_PER_ABILITY));
        }
        return result;
    }

    @SafeVarargs
    private String graphVersionFromEdges(List<KgGraphEdge>... edgeGroups) {
        for (List<KgGraphEdge> edgeGroup : edgeGroups) {
            for (KgGraphEdge edge : edgeGroup) {
                String version = asString(parseMetadata(edge.getMetadataJson()).get("graphVersion"));
                if (version != null) {
                    return version;
                }
            }
        }
        return null;
    }

    @SafeVarargs
    private LocalDateTime latestEdgeTime(List<KgGraphEdge>... edgeGroups) {
        return Arrays.stream(edgeGroups)
                .flatMap(Collection::stream)
                .map(KgGraphEdge::getUpdatedTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElseGet(() -> Arrays.stream(edgeGroups)
                        .flatMap(Collection::stream)
                        .map(KgGraphEdge::getCreatedTime)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null));
    }

    private List<String> parseSourceRefs(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }

    private Integer asInteger(Object value) {
        return asInteger(value, null);
    }

    private Integer asInteger(Object value, Integer defaultVal) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            log.debug("瑙ｆ瀽鍥捐氨鍏冩暟鎹け璐ワ紝杩斿洖绌哄璞? {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
