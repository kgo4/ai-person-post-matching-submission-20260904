package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.kg.KnowledgeGraphIncrementalService;
import com.example.matching.service.kg.Neo4jGraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies daily graph changes to the smallest reliable business subgraph. */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphIncrementalServiceImpl implements KnowledgeGraphIncrementalService {

    private static final String POST = "POST";
    private static final String EMPLOYEE = "EMPLOYEE";
    private static final String ABILITY = "ABILITY";
    private static final String REQUIRES = "REQUIRES";
    private static final String HAS_ABILITY = "HAS_ABILITY";

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final TagQueryPort tagQueryPort;
    private final KnowledgeGraphBuildService fullBuildService;
    private final ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncrementalGraphResult apply(KgGraphChangeSet changeSet) {
        String graphVersion = "KGV_" + System.currentTimeMillis();
        MutableResult result = new MutableResult(graphVersion);
        switch (changeSet.getSourceType()) {
            case "POST_MODEL" -> refreshPostModel(changeSet, result);
            case "EMP_ABILITY" -> refreshEmployeeAbilities(changeSet, result);
            case "ABILITY_TAG" -> refreshAbility(changeSet, result);
            default -> {
                // M19：不支持的来源类型不再抛失败导致无限重试；
                // 调用已有全量重建兜底，保证 KNOWLEDGE_DOMAIN/KNOWLEDGE_NODE 等变更可收敛
                log.warn("增量图不支持 sourceType={}，降级为全量重建: changeCode={}",
                        changeSet.getSourceType(), changeSet.getChangeCode());
                rebuildFallback(changeSet, result);
            }
        }
        syncNeo4j(result);
        int nodeCount = result.fullRebuild ? result.fallbackNodeCount : result.nodes.size() + result.deletedNodeKeys.size();
        int edgeCount = result.fullRebuild ? result.fallbackEdgeCount : result.upsertEdges.size() + result.deletedEdgeKeys.size();
        return new IncrementalGraphResult(nodeCount, edgeCount, graphVersion);
    }

    private void refreshPostModel(KgGraphChangeSet changeSet, MutableResult result) {
        Long postId = changeSet.getEntityId();
        String postKey = key(POST, postId);
        PostQueryPort.PostDTO post = postQueryPort.getPostById(postId);
        if (post == null || "DELETE".equals(changeSet.getOperationType()) || "DISABLE".equals(changeSet.getOperationType())) {
            removeNode(postKey, result);
            return;
        }

        upsertNode(postNode(post), result);
        List<KgGraphEdge> edges = new ArrayList<>();
        for (PostQueryPort.PostAbilityDTO requirement : postQueryPort.listRequirementsByPostId(postId)) {
            TagQueryPort.TagDTO tag = tagQueryPort.getTagById(requirement.tagId());
            String abilityKey;
            if (tag == null) {
                if (requirement.id() == null || requirement.abilityName() == null || requirement.abilityName().isBlank()) {
                    continue;
                }
                abilityKey = "POST_SKILL_POINT:" + requirement.id();
                upsertNode(skillPointNode(abilityKey, requirement.abilityName(), requirement.techStack(), requirement.minRequiredLevel()), result);
            } else {
                abilityKey = key(ABILITY, tag.id());
                upsertNode(abilityNode(tag), result);
            }
            if (abilityKey == null) {
                continue;
            }
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(edgeKey(REQUIRES, postKey, abilityKey));
            edge.setSourceNodeKey(postKey);
            edge.setTargetNodeKey(abilityKey);
            edge.setEdgeType(REQUIRES);
            edge.setWeightValue(requirement.weight());
            edge.setConfidenceScore(BigDecimal.valueOf(100));
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("minRequiredLevel", requirement.minRequiredLevel());
            fields.put("isRequired", requirement.isRequired());
            fields.put("isCore", requirement.isCore());
            fields.put("skillType", Integer.valueOf(1).equals(requirement.isRequired()) ? "REQUIRED" : "BONUS");
            edge.setMetadataJson(metadata(REQUIRES, graphVersion(result),
                    requirement.id() == null ? List.of() : List.of(SourceRefConstants.postAbilityModelFactRef(requirement.id())), fields));
            edges.add(edge);
        }
        replaceSourceEdges(postKey, REQUIRES, edges, result);
    }

    private void refreshEmployeeAbilities(KgGraphChangeSet changeSet, MutableResult result) {
        Long employeeId = changeSet.getEntityId();
        String employeeKey = key(EMPLOYEE, employeeId);
        TalentQueryPort.EmployeeDTO employee = talentQueryPort.getEmployeeById(employeeId);
        if (employee == null || "DELETE".equals(changeSet.getOperationType()) || "DISABLE".equals(changeSet.getOperationType())) {
            removeNode(employeeKey, result);
            return;
        }

        upsertNode(employeeNode(employee), result);
        List<KgGraphEdge> edges = new ArrayList<>();
        for (TalentQueryPort.EmployeeAbilityDTO ability : talentQueryPort.listAbilitiesByEmpId(employeeId)) {
            TagQueryPort.TagDTO tag = tagQueryPort.getTagById(ability.tagId());
            String abilityKey;
            String edgeType;
            if (tag == null) {
                if (ability.id() == null || ability.abilityName() == null || ability.abilityName().isBlank()) {
                    continue;
                }
                abilityKey = "EMP_ABILITY:" + ability.id();
                edgeType = "HAS_ABILITY_FACT";
                upsertNode(employeeAbilityFactNode(abilityKey, ability), result);
            } else {
                abilityKey = key(ABILITY, tag.id());
                edgeType = HAS_ABILITY;
                upsertNode(abilityNode(tag), result);
            }
            if (abilityKey == null) {
                continue;
            }
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(edgeKey(edgeType, employeeKey, abilityKey));
            edge.setSourceNodeKey(employeeKey);
            edge.setTargetNodeKey(abilityKey);
            edge.setEdgeType(edgeType);
            edge.setWeightValue(ability.sourceWeight());
            edge.setConfidenceScore(BigDecimal.valueOf(100));
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("masteryLevel", ability.masteryLevel());
            fields.put("evaluationSource", ability.evaluationSource());
            fields.put("evaluationDate", ability.evaluationDate());
            fields.put("sourceWeight", ability.sourceWeight());
            edge.setMetadataJson(metadata(HAS_ABILITY, graphVersion(result),
                    ability.id() == null ? List.of() : List.of(SourceRefConstants.empAbilityFactRef(ability.id())), fields));
            edges.add(edge);
        }
        replaceSourceEdges(employeeKey, HAS_ABILITY, edges.stream()
                .filter(edge -> HAS_ABILITY.equals(edge.getEdgeType())).toList(), result);
        replaceSourceEdges(employeeKey, "HAS_ABILITY_FACT", edges.stream()
                .filter(edge -> "HAS_ABILITY_FACT".equals(edge.getEdgeType())).toList(), result);
    }

    private void refreshAbility(KgGraphChangeSet changeSet, MutableResult result) {
        String abilityKey = key(ABILITY, changeSet.getEntityId());
        TagQueryPort.TagDTO tag = tagQueryPort.getTagById(changeSet.getEntityId());
        if (tag == null || "DELETE".equals(changeSet.getOperationType()) || "DISABLE".equals(changeSet.getOperationType())) {
            removeNode(abilityKey, result);
            return;
        }
        upsertNode(abilityNode(tag), result);
    }

    private void rebuildFallback(KgGraphChangeSet changeSet, MutableResult result) {
        log.info("No incremental handler for sourceType={}; use complete graph rebuild: changeCode={}",
                changeSet.getSourceType(), changeSet.getChangeCode());
        var fullResult = fullBuildService.rebuildFullGraph();
        result.fullRebuild = true;
        result.graphVersion = fullResult.getGraphVersion();
        result.fallbackNodeCount = fullResult.getNodeCount();
        result.fallbackEdgeCount = fullResult.getEdgeCount();
    }

    private void upsertNode(KgGraphNode node, MutableResult result) {
        KgGraphNode existing = graphNodeMapper.selectOne(Wrappers.<KgGraphNode>lambdaQuery()
                .eq(KgGraphNode::getNodeKey, node.getNodeKey()).last("LIMIT 1"));
        if (existing == null) {
            graphNodeMapper.insert(node);
        } else {
            node.setId(existing.getId());
            graphNodeMapper.updateById(node);
        }
        result.nodes.add(node);
    }

    private void replaceSourceEdges(String sourceNodeKey, String edgeType, List<KgGraphEdge> newEdges, MutableResult result) {
        List<KgGraphEdge> existing = graphEdgeMapper.selectList(Wrappers.<KgGraphEdge>lambdaQuery()
                .eq(KgGraphEdge::getSourceNodeKey, sourceNodeKey)
                .eq(KgGraphEdge::getEdgeType, edgeType));
        existing.forEach(edge -> result.deletedEdgeKeys.add(edge.getEdgeKey()));
        graphEdgeMapper.delete(Wrappers.<KgGraphEdge>lambdaQuery()
                .eq(KgGraphEdge::getSourceNodeKey, sourceNodeKey)
                .eq(KgGraphEdge::getEdgeType, edgeType));
        newEdges.forEach(graphEdgeMapper::insert);
        result.upsertEdges.addAll(newEdges);
    }

    private void removeNode(String nodeKey, MutableResult result) {
        List<KgGraphEdge> related = graphEdgeMapper.selectList(Wrappers.<KgGraphEdge>lambdaQuery()
                .and(wrapper -> wrapper.eq(KgGraphEdge::getSourceNodeKey, nodeKey)
                        .or().eq(KgGraphEdge::getTargetNodeKey, nodeKey)));
        related.forEach(edge -> result.deletedEdgeKeys.add(edge.getEdgeKey()));
        graphEdgeMapper.delete(Wrappers.<KgGraphEdge>lambdaQuery()
                .and(wrapper -> wrapper.eq(KgGraphEdge::getSourceNodeKey, nodeKey)
                        .or().eq(KgGraphEdge::getTargetNodeKey, nodeKey)));
        graphNodeMapper.delete(Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, nodeKey));
        result.deletedNodeKeys.add(nodeKey);
    }

    private void syncNeo4j(MutableResult result) {
        if (result.fullRebuild) {
            return;
        }
        Neo4jGraphStore graphStore = neo4jGraphStoreProvider.getIfAvailable();
        if (graphStore != null) {
            Map<String, Object> syncResult = graphStore.syncIncremental(
                    result.nodes, result.upsertEdges, result.deletedNodeKeys, result.deletedEdgeKeys);
            if ("FAIL".equals(syncResult.get("status"))) {
                // MySQL is the authoritative graph projection. Neo4j is an optional read model,
                // so its temporary outage must not roll back the business transaction.
                log.warn("Neo4j incremental graph sync unavailable; MySQL projection remains committed: {}",
                        syncResult.get("message"));
            }
        }
    }

    private KgGraphNode postNode(PostQueryPort.PostDTO post) {
        KgGraphNode node = baseNode(key(POST, post.id()), POST, post.id(), post.postName(), post.postLevel());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postCode", post.postCode());
        metadata.put("departmentId", post.departmentId());
        node.setMetadataJson(writeJson(metadata));
        return node;
    }

    private KgGraphNode employeeNode(TalentQueryPort.EmployeeDTO employee) {
        KgGraphNode node = baseNode(key(EMPLOYEE, employee.id()), EMPLOYEE, employee.id(), employee.realName(), employee.level());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("employeeNo", employee.employeeNo());
        metadata.put("departmentId", employee.departmentId());
        metadata.put("currentPostId", employee.currentPostId());
        node.setMetadataJson(writeJson(metadata));
        return node;
    }

    private KgGraphNode abilityNode(TagQueryPort.TagDTO tag) {
        KgGraphNode node = baseNode(key(ABILITY, tag.id()), ABILITY, tag.id(), tag.tagName(), tag.tagCategory());
        node.setLevelValue(tag.tagLevel());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tagCode", tag.tagCode());
        metadata.put("parentId", tag.parentId());
        metadata.put("canonicalTagId", tag.canonicalTagId());
        metadata.put("domain", tag.domain());
        node.setMetadataJson(writeJson(metadata));
        return node;
    }

    private KgGraphNode skillPointNode(String nodeKey, String name, String techStack, Integer level) {
        KgGraphNode node = baseNode(nodeKey, "POST_SKILL_POINT", null, name.trim(),
                techStack == null || techStack.isBlank() ? "通用工程能力" : techStack.trim());
        node.setLevelValue(level);
        return node;
    }

    private KgGraphNode employeeAbilityFactNode(String nodeKey, TalentQueryPort.EmployeeAbilityDTO ability) {
        KgGraphNode node = baseNode(nodeKey, "ABILITY_FACT", ability.id(), ability.abilityName().trim(), "EMPLOYEE_ABILITY");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("empId", ability.empId());
        metadata.put("abilityName", ability.abilityName().trim());
        metadata.put("masteryLevel", ability.masteryLevel());
        metadata.put("evaluationSource", ability.evaluationSource());
        node.setMetadataJson(writeJson(metadata));
        return node;
    }

    private KgGraphNode baseNode(String nodeKey, String nodeType, Long refId, String label, String category) {
        KgGraphNode node = new KgGraphNode();
        node.setNodeKey(nodeKey);
        node.setNodeType(nodeType);
        node.setRefId(refId);
        node.setLabel(label);
        node.setCategory(category);
        node.setStatus("ACTIVE");
        node.setWeightValue(BigDecimal.ONE);
        return node;
    }

    private String metadata(String relationType, String graphVersion, List<String> sourceRefs, Map<String, Object> businessFields) {
        Map<String, Object> metadata = new LinkedHashMap<>(businessFields);
        metadata.put("relationType", relationType);
        metadata.put("sourceRefs", sourceRefs.isEmpty() ? List.of(SourceRefConstants.PREFIX_KG + "GRAPH_RELATION:" + relationType) : sourceRefs);
        metadata.put("reviewStatus", "SYSTEM_VERIFIED");
        metadata.put("relationStatus", "ACTIVE");
        metadata.put("validFrom", LocalDateTime.now().toString());
        metadata.put("graphVersion", graphVersion);
        return writeJson(metadata);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize incremental graph metadata", exception);
        }
    }

    private String key(String type, Long id) {
        return type + ":" + id;
    }

    private String edgeKey(String type, String sourceKey, String targetKey) {
        return type + "_" + sourceKey + "_" + targetKey;
    }

    private String graphVersion(MutableResult result) {
        return result.graphVersion;
    }

    private static final class MutableResult {
        private String graphVersion;
        private final List<KgGraphNode> nodes = new ArrayList<>();
        private final List<KgGraphEdge> upsertEdges = new ArrayList<>();
        private final List<String> deletedNodeKeys = new ArrayList<>();
        private final List<String> deletedEdgeKeys = new ArrayList<>();
        private boolean fullRebuild;
        private int fallbackNodeCount;
        private int fallbackEdgeCount;

        private MutableResult(String graphVersion) {
            this.graphVersion = graphVersion;
        }
    }

}
