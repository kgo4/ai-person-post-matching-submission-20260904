package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.GraphRelationGovernanceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GraphRelationGovernanceServiceImpl implements GraphRelationGovernanceService {

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getPolicies() {
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("REQUIRES", Map.of(
                "producer", "POST_ABILITY_MODEL",
                "approval", "业务模型发布后系统校验",
                "invalidation", "岗位模型替换、岗位停用或删除",
                "requiredMetadata", List.of("minRequiredLevel", "isRequired", "skillType", "sourceRefs", "confidenceScore")));
        policies.put("HAS_ABILITY", Map.of(
                "producer", "EMP_ABILITY",
                "approval", "员工能力画像业务校验后系统投影",
                "invalidation", "能力记录删除、员工停用或标签停用",
                "requiredMetadata", List.of("masteryLevel", "evaluationSource", "sourceWeight", "sourceRefs", "confidenceScore")));
        policies.put("PREREQUISITE_OF", Map.of(
                "producer", "KNOWLEDGE_NODE.prerequisites_json",
                "approval", "知识节点主数据治理",
                "invalidation", "知识点前置配置、状态或删除变化",
                "requiredMetadata", List.of("governanceMode", "sourceRefs", "reviewStatus")));
        policies.put("RELATED_TO", Map.of(
                "producer", "KG_RELATION_CANDIDATE",
                "approval", "人工审核 APPROVED",
                "invalidation", "候选关系 REVOKED 或端点标签失效",
                "requiredMetadata", List.of("relationCandidateId", "discoveryMethod", "sourceRefs", "reviewStatus")));
        return policies;
    }

    @Override
    public Map<String, Object> inspectActiveGraph() {
        Set<String> nodeKeys = new LinkedHashSet<>();
        graphNodeMapper.selectList(Wrappers.<KgGraphNode>lambdaQuery())
                .forEach(node -> nodeKeys.add(node.getNodeKey()));
        List<Map<String, Object>> violations = new ArrayList<>();
        int checked = 0;
        for (KgGraphEdge edge : graphEdgeMapper.selectList(Wrappers.<KgGraphEdge>lambdaQuery())) {
            checked++;
            Map<String, Object> metadata = readMetadata(edge.getMetadataJson());
            validateEndpoints(edge, nodeKeys, violations);
            validateCoreMetadata(edge, metadata, violations);
            switch (edge.getEdgeType()) {
                case "REQUIRES" -> validateRequires(edge, metadata, violations);
                case "HAS_ABILITY" -> validateHasAbility(edge, metadata, violations);
                case "PREREQUISITE_OF" -> validatePrerequisite(edge, metadata, violations);
                case "RELATED_TO" -> validateRelated(edge, metadata, violations);
                default -> { }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedEdgeCount", checked);
        result.put("violationCount", violations.size());
        result.put("violations", violations);
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    private void validateEndpoints(KgGraphEdge edge, Set<String> nodeKeys, List<Map<String, Object>> violations) {
        if (!nodeKeys.contains(edge.getSourceNodeKey()) || !nodeKeys.contains(edge.getTargetNodeKey())) {
            violation(edge, "MISSING_ENDPOINT", "图边存在不存在的端点", violations);
        }
    }

    private void validateCoreMetadata(KgGraphEdge edge, Map<String, Object> metadata,
                                      List<Map<String, Object>> violations) {
        if (!edge.getEdgeType().equals(metadata.get("relationType"))) {
            violation(edge, "RELATION_TYPE_MISMATCH", "metadata.relationType 与 edgeType 不一致", violations);
        }
        if (sourceRefs(metadata).isEmpty()) {
            violation(edge, "MISSING_SOURCE_REFS", "缺少 sourceRefs", violations);
        }
        if (!metadata.containsKey("reviewStatus") || !metadata.containsKey("graphVersion")) {
            violation(edge, "MISSING_GOVERNANCE_METADATA", "缺少审核状态或图谱版本", violations);
        }
        Object validTo = metadata.get("validTo");
        if (validTo instanceof String value && !value.isBlank() && isExpired(value)
                && "ACTIVE".equals(metadata.get("relationStatus"))) {
            violation(edge, "EXPIRED_RELATION_ACTIVE", "已过期关系仍为 ACTIVE", violations);
        }
    }

    private void validateRequires(KgGraphEdge edge, Map<String, Object> metadata,
                                  List<Map<String, Object>> violations) {
        if (edge.getSourceNodeKey().startsWith("POST_FAMILY:")) {
            return;
        }
        if (!edge.getSourceNodeKey().startsWith("POST:") || !edge.getTargetNodeKey().startsWith("ABILITY:")) {
            violation(edge, "REQUIRES_ENDPOINT_TYPE", "REQUIRES 必须从岗位指向能力", violations);
        }
        if (!metadata.containsKey("minRequiredLevel") || !metadata.containsKey("isRequired")
                || !metadata.containsKey("skillType") || !hasSourcePrefix(metadata, "fact:POST_ABILITY_MODEL:")) {
            violation(edge, "REQUIRES_CONTRACT", "岗位能力模型关系缺少业务属性或事实来源", violations);
        }
    }

    private void validateHasAbility(KgGraphEdge edge, Map<String, Object> metadata,
                                    List<Map<String, Object>> violations) {
        if (!edge.getSourceNodeKey().startsWith("EMPLOYEE:") || !edge.getTargetNodeKey().startsWith("ABILITY:")) {
            violation(edge, "HAS_ABILITY_ENDPOINT_TYPE", "HAS_ABILITY 必须从员工指向能力", violations);
        }
        if (!metadata.containsKey("masteryLevel") || !metadata.containsKey("evaluationSource")
                || !hasSourcePrefix(metadata, "fact:EMP_ABILITY:")) {
            violation(edge, "HAS_ABILITY_CONTRACT", "员工能力关系缺少掌握度、来源或事实引用", violations);
        }
    }

    private void validatePrerequisite(KgGraphEdge edge, Map<String, Object> metadata,
                                      List<Map<String, Object>> violations) {
        if (!edge.getSourceNodeKey().startsWith("KNOWLEDGE_NODE:")
                || !edge.getTargetNodeKey().startsWith("KNOWLEDGE_NODE:")
                || edge.getSourceNodeKey().equals(edge.getTargetNodeKey())) {
            violation(edge, "PREREQUISITE_ENDPOINT_TYPE", "前置关系必须连接两个不同知识点", violations);
        }
        if (!"KNOWLEDGE_NODE_MASTER_DATA".equals(metadata.get("governanceMode"))) {
            violation(edge, "PREREQUISITE_GOVERNANCE", "前置关系未标记知识节点主数据治理来源", violations);
        }
    }

    private void validateRelated(KgGraphEdge edge, Map<String, Object> metadata,
                                 List<Map<String, Object>> violations) {
        if (!edge.getSourceNodeKey().startsWith("ABILITY:") || !edge.getTargetNodeKey().startsWith("ABILITY:")) {
            violation(edge, "RELATED_ENDPOINT_TYPE", "语义关联仅允许能力标签之间建立", violations);
        }
        if (!"APPROVED".equals(metadata.get("reviewStatus")) || !metadata.containsKey("relationCandidateId")
                || !metadata.containsKey("discoveryMethod")) {
            violation(edge, "RELATED_APPROVAL", "语义关联缺少审核通过状态或候选关系信息", violations);
        }
        if (sourceRefs(metadata).stream().allMatch(ref -> ref.startsWith("kg:"))) {
            violation(edge, "RELATED_EVIDENCE", "语义关联不能只使用图谱自引用作为证据", violations);
        }
    }

    private Map<String, Object> readMetadata(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> sourceRefs(Map<String, Object> metadata) {
        Object refs = metadata.get("sourceRefs");
        if (!(refs instanceof Collection<?> collection)) return List.of();
        return collection.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private boolean hasSourcePrefix(Map<String, Object> metadata, String prefix) {
        return sourceRefs(metadata).stream().anyMatch(ref -> ref.startsWith(prefix));
    }

    private boolean isExpired(String value) {
        try {
            return LocalDateTime.parse(value).isBefore(LocalDateTime.now());
        } catch (Exception exception) {
            return false;
        }
    }

    private void violation(KgGraphEdge edge, String code, String message, List<Map<String, Object>> violations) {
        violations.add(Map.of("edgeKey", edge.getEdgeKey(), "edgeType", edge.getEdgeType(),
                "code", code, "message", message));
    }
}
