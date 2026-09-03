package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.kg.KgGraphSnapshot;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgPostAbilitySnapshot;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgPostAbilitySnapshotMapper;
import com.example.matching.mapper.kg.KgGraphSnapshotMapper;
import com.example.matching.service.kg.KnowledgeGraphSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱快照服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphSnapshotServiceImpl implements KnowledgeGraphSnapshotService {

    private final KgGraphSnapshotMapper graphSnapshotMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final KgPostAbilitySnapshotMapper postAbilitySnapshotMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter SNAPSHOT_CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    public KgGraphSnapshot createSnapshot(String snapshotType, String snapshotName, String graphJson, Long createdBy) {
        KgGraphSnapshot snapshot = new KgGraphSnapshot();
        snapshot.setSnapshotCode(generateSnapshotCode());
        snapshot.setSnapshotName(snapshotName);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setSnapshotJson(graphJson);
        snapshot.setCreatedBy(createdBy);

        // 计算节点和边数量（从JSON中解析）
        int[] counts = parseGraphCounts(graphJson);
        snapshot.setNodeCount(counts[0]);
        snapshot.setEdgeCount(counts[1]);

        graphSnapshotMapper.insert(snapshot);
        log.info("创建图谱快照成功：code={}, type={}, nodes={}, edges={}",
                snapshot.getSnapshotCode(), snapshotType, counts[0], counts[1]);
        return snapshot;
    }

    @Override
    public Map<String, Object> getSnapshotPage(String snapshotType, Integer page, Integer size) {
        int effectivePage = page != null ? page : 1;
        int effectiveSize = size != null ? size : 10;

        LambdaQueryWrapper<KgGraphSnapshot> query = Wrappers.<KgGraphSnapshot>lambdaQuery();
        if (snapshotType != null && !snapshotType.isEmpty()) {
            query.eq(KgGraphSnapshot::getSnapshotType, snapshotType);
        }
        query.orderByDesc(KgGraphSnapshot::getCreatedTime);

        Page<KgGraphSnapshot> pageResult = graphSnapshotMapper.selectPage(
                new Page<>(effectivePage, effectiveSize), query);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return result;
    }

    @Override
    public KgGraphSnapshot getSnapshotById(Long id) {
        return graphSnapshotMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPostAbilitySnapshot(String snapshotType, Long createdBy) {
        String snapshotCode = "KGPA_" + LocalDateTime.now().format(SNAPSHOT_CODE_FORMAT);
        LocalDateTime snapshotTime = LocalDateTime.now();
        List<KgGraphEdge> allEdges = graphEdgeMapper.selectList(Wrappers.<KgGraphEdge>lambdaQuery());
        Map<String, List<KgGraphEdge>> evidenceByBusinessNode = new HashMap<>();
        for (KgGraphEdge edge : allEdges) {
            if ("SUPPORTED_BY".equals(edge.getEdgeType())) {
                evidenceByBusinessNode.computeIfAbsent(edge.getSourceNodeKey(), ignored -> new ArrayList<>()).add(edge);
            }
        }

        for (KgGraphEdge edge : allEdges) {
            if (!"REQUIRES".equals(edge.getEdgeType())) {
                continue;
            }
            Long postId = parseRefId(edge.getSourceNodeKey(), "POST:");
            Long abilityTagId = parseRefId(edge.getTargetNodeKey(), "ABILITY:");
            if (postId == null || abilityTagId == null) {
                continue;
            }
            JsonNode metadata = readMetadata(edge.getMetadataJson());
            List<KgGraphEdge> postEvidence = evidenceByBusinessNode.getOrDefault(edge.getSourceNodeKey(), List.of());
            List<KgGraphEdge> abilityEvidence = evidenceByBusinessNode.getOrDefault(edge.getTargetNodeKey(), List.of());
            KgPostAbilitySnapshot snapshot = new KgPostAbilitySnapshot();
            snapshot.setSnapshotCode(snapshotCode);
            snapshot.setSnapshotType(snapshotType == null || snapshotType.isBlank() ? "MANUAL" : snapshotType);
            snapshot.setPostId(postId);
            snapshot.setAbilityTagId(abilityTagId);
            snapshot.setRelationType("REQUIRES");
            snapshot.setWeightValue(edge.getWeightValue());
            snapshot.setMinRequiredLevel(metadata.path("minRequiredLevel").isInt() ? metadata.path("minRequiredLevel").asInt() : null);
            snapshot.setIsRequired(metadata.path("isRequired").isInt() ? metadata.path("isRequired").asInt() : null);
            snapshot.setEvidenceCount(postEvidence.size() + abilityEvidence.size());
            snapshot.setAverageConfidence(averageConfidence(postEvidence, abilityEvidence, edge));
            snapshot.setGraphVersion(metadata.path("graphVersion").asText("KGV_UNVERSIONED"));
            snapshot.setSnapshotTime(snapshotTime);
            snapshot.setCreatedBy(createdBy);
            postAbilitySnapshotMapper.insert(snapshot);
        }
        log.info("Created post ability snapshot: code={}, type={}", snapshotCode, snapshotType);
        return snapshotCode;
    }

    @Override
    public Map<String, Object> diffPostAbilitySnapshots(String baselineSnapshotCode, String targetSnapshotCode) {
        Map<String, KgPostAbilitySnapshot> baseline = indexFacts(baselineSnapshotCode);
        Map<String, KgPostAbilitySnapshot> target = indexFacts(targetSnapshotCode);
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> weightChanged = new ArrayList<>();
        List<Map<String, Object>> confidenceChanged = new ArrayList<>();

        target.forEach((key, value) -> {
            KgPostAbilitySnapshot previous = baseline.get(key);
            if (previous == null) {
                added.add(fact(value));
                return;
            }
            if (!sameDecimal(previous.getWeightValue(), value.getWeightValue())) {
                weightChanged.add(change(previous, value, "weightValue"));
            }
            if (!sameDecimal(previous.getAverageConfidence(), value.getAverageConfidence())) {
                confidenceChanged.add(change(previous, value, "averageConfidence"));
            }
        });
        baseline.forEach((key, value) -> {
            if (!target.containsKey(key)) {
                removed.add(fact(value));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baselineSnapshotCode", baselineSnapshotCode);
        result.put("targetSnapshotCode", targetSnapshotCode);
        result.put("added", added);
        result.put("removed", removed);
        result.put("weightChanged", weightChanged);
        result.put("confidenceChanged", confidenceChanged);
        return result;
    }

    private Map<String, KgPostAbilitySnapshot> indexFacts(String snapshotCode) {
        Map<String, KgPostAbilitySnapshot> indexed = new HashMap<>();
        postAbilitySnapshotMapper.selectList(Wrappers.<KgPostAbilitySnapshot>lambdaQuery()
                        .eq(KgPostAbilitySnapshot::getSnapshotCode, snapshotCode))
                .forEach(snapshot -> indexed.put(snapshot.getPostId() + ":" + snapshot.getAbilityTagId() + ":" + snapshot.getRelationType(), snapshot));
        return indexed;
    }

    private Map<String, Object> fact(KgPostAbilitySnapshot snapshot) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("postId", snapshot.getPostId());
        fact.put("abilityTagId", snapshot.getAbilityTagId());
        fact.put("relationType", snapshot.getRelationType());
        fact.put("weightValue", snapshot.getWeightValue());
        fact.put("averageConfidence", snapshot.getAverageConfidence());
        fact.put("evidenceCount", snapshot.getEvidenceCount());
        return fact;
    }

    private Map<String, Object> change(KgPostAbilitySnapshot before, KgPostAbilitySnapshot after, String field) {
        Map<String, Object> result = fact(after);
        result.put("field", field);
        result.put("before", "weightValue".equals(field) ? before.getWeightValue() : before.getAverageConfidence());
        result.put("after", "weightValue".equals(field) ? after.getWeightValue() : after.getAverageConfidence());
        return result;
    }

    private java.math.BigDecimal averageConfidence(List<KgGraphEdge> postEvidence,
                                                    List<KgGraphEdge> abilityEvidence,
                                                    KgGraphEdge requiresEdge) {
        List<KgGraphEdge> evidence = new ArrayList<>(postEvidence);
        evidence.addAll(abilityEvidence);
        if (evidence.isEmpty()) {
            return requiresEdge.getConfidenceScore();
        }
        return evidence.stream().map(KgGraphEdge::getConfidenceScore)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .divide(java.math.BigDecimal.valueOf(evidence.size()), 4, java.math.RoundingMode.HALF_UP);
    }

    private JsonNode readMetadata(String metadataJson) {
        try {
            return metadataJson == null ? objectMapper.createObjectNode() : objectMapper.readTree(metadataJson);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Long parseRefId(String nodeKey, String prefix) {
        if (nodeKey == null || !nodeKey.startsWith(prefix)) return null;
        try {
            return Long.parseLong(nodeKey.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean sameDecimal(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    private String generateSnapshotCode() {
        return "KGS_" + LocalDateTime.now().format(SNAPSHOT_CODE_FORMAT);
    }

    private int[] parseGraphCounts(String graphJson) {
        try {
            if (graphJson == null || graphJson.isBlank()) {
                return new int[]{0, 0};
            }
            JsonNode root = objectMapper.readTree(graphJson);
            int nodeCount = root.path("nodes").isArray() ? root.path("nodes").size() : 0;
            int edgeCount = root.path("edges").isArray() ? root.path("edges").size() : 0;
            return new int[]{nodeCount, edgeCount};
        } catch (Exception e) {
            log.warn("解析图谱JSON失败", e);
            return new int[]{0, 0};
        }
    }

}
