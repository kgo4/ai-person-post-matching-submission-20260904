package com.example.matching.service.kg.build;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSnapshotWriter {

    private static final int GRAPH_WRITE_BATCH_SIZE = 500;
    private static final String EDGE_TYPE_RELATED_TO = "RELATED_TO";
    private static final String EDGE_TYPE_SUPPORTED_BY = "SUPPORTED_BY";

    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final ObjectMapper objectMapper;

    public String generateNodeKey(String type, Long id) {
        return type + ":" + id;
    }

    public String generateEdgeKey(String type, String sourceKey, String targetKey) {
        return type + "_" + sourceKey + "_" + targetKey;
    }

    public void batchInsertNodes(List<KgGraphNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < nodes.size(); i += GRAPH_WRITE_BATCH_SIZE) {
            int end = Math.min(i + GRAPH_WRITE_BATCH_SIZE, nodes.size());
            List<KgGraphNode> batch = nodes.subList(i, end);
            for (KgGraphNode n : batch) {
                graphNodeMapper.insert(n);
            }
        }
    }

    public void batchInsertEdges(List<KgGraphEdge> edges, GraphBuildContext ctx) {
        if (edges.isEmpty()) {
            return;
        }
        Map<String, KgGraphEdge> edgesByKey = new LinkedHashMap<>();
        for (KgGraphEdge edge : edges) {
            normalizeEdgeMetadata(edge, ctx);
            KgGraphEdge existing = edgesByKey.putIfAbsent(edge.getEdgeKey(), edge);
            if (existing != null) {
                mergeDuplicateEdge(existing, edge);
            }
        }
        List<KgGraphEdge> uniqueEdges = new ArrayList<>(edgesByKey.values());
        for (int i = 0; i < uniqueEdges.size(); i += GRAPH_WRITE_BATCH_SIZE) {
            int end = Math.min(i + GRAPH_WRITE_BATCH_SIZE, uniqueEdges.size());
            List<KgGraphEdge> batch = uniqueEdges.subList(i, end);
            for (KgGraphEdge e : batch) {
                graphEdgeMapper.insert(e);
            }
        }
    }

    private void mergeDuplicateEdge(KgGraphEdge existing, KgGraphEdge duplicate) {
        if (isGreater(duplicate.getWeightValue(), existing.getWeightValue())) {
            existing.setWeightValue(duplicate.getWeightValue());
            existing.setConfidenceScore(duplicate.getConfidenceScore());
        }

        Map<String, Object> metadata = readMetadata(existing.getMetadataJson());
        LinkedHashSet<String> sourceRefs = new LinkedHashSet<>(extractSourceRefs(metadata));
        sourceRefs.addAll(extractSourceRefs(readMetadata(duplicate.getMetadataJson())));
        metadata.put("sourceRefs", new ArrayList<>(sourceRefs));
        existing.setMetadataJson(toJson(metadata));
    }

    private boolean isGreater(BigDecimal candidate, BigDecimal current) {
        return candidate != null && (current == null || candidate.compareTo(current) > 0);
    }

    public boolean graphNodeExists(String nodeKey) {
        return nodeKey != null && graphNodeMapper.selectCount(
                Wrappers.<KgGraphNode>lambdaQuery().eq(KgGraphNode::getNodeKey, nodeKey)) > 0;
    }

    public List<String> readSourceRefs(String sourceRefsJson) {
        if (sourceRefsJson == null || sourceRefsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sourceRefsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            log.warn("Ignore invalid source references from relation candidate");
            return List.of();
        }
    }

    private void normalizeEdgeMetadata(KgGraphEdge edge, GraphBuildContext ctx) {
        Map<String, Object> metadata = readMetadata(edge.getMetadataJson());
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        List<String> sourceRefs = extractSourceRefs(metadata);
        if (sourceRefs.isEmpty()) {
            sourceRefs.add(SourceRefConstants.PREFIX_KG + "GRAPH_RELATION:" + edge.getEdgeKey());
            metadata.put("provenance", "SYSTEM_PROJECTION");
        }
        metadata.put("relationType", edge.getEdgeType());
        metadata.put("sourceRefs", sourceRefs);
        if (!EDGE_TYPE_SUPPORTED_BY.equals(edge.getEdgeType())) {
            metadata.putIfAbsent("reviewStatus",
                    EDGE_TYPE_RELATED_TO.equals(edge.getEdgeType()) ? "APPROVED" : "SYSTEM_VERIFIED");
        }
        metadata.putIfAbsent("relationStatus", "ACTIVE");
        metadata.putIfAbsent("validFrom",
                Optional.ofNullable(ctx.validFrom()).orElseGet(() -> java.time.LocalDateTime.now().toString()));
        metadata.putIfAbsent("graphVersion",
                Optional.ofNullable(ctx.graphVersion()).orElse("KGV_UNVERSIONED"));
        edge.setMetadataJson(toJson(metadata));

        if (edge.getConfidenceScore() == null) {
            if (EDGE_TYPE_RELATED_TO.equals(edge.getEdgeType()) && edge.getWeightValue() != null) {
                edge.setConfidenceScore(edge.getWeightValue().multiply(BigDecimal.valueOf(100)));
            } else if (EDGE_TYPE_SUPPORTED_BY.equals(edge.getEdgeType()) && edge.getWeightValue() != null) {
                edge.setConfidenceScore(toPercentage(edge.getWeightValue()));
            } else {
                edge.setConfidenceScore(BigDecimal.valueOf(100));
            }
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (Exception exception) {
            log.warn("Discard invalid graph edge metadata when applying the data contract");
            return new LinkedHashMap<>();
        }
    }

    private List<String> extractSourceRefs(Map<String, Object> metadata) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        if (metadata == null) {
            return new ArrayList<>(refs);
        }
        Object existingRefs = metadata.get("sourceRefs");
        if (existingRefs instanceof Collection<?> collection) {
            for (Object sourceRef : collection) {
                if (sourceRef instanceof String value && !value.isBlank()) {
                    refs.add(value);
                }
            }
        }
        Object legacySourceRef = metadata.get("sourceRef");
        if (legacySourceRef instanceof String value && !value.isBlank()) {
            refs.add(value);
        }
        return new ArrayList<>(refs);
    }

    private BigDecimal toPercentage(BigDecimal value) {
        return value.compareTo(BigDecimal.ONE) <= 0 ? value.multiply(BigDecimal.valueOf(100)) : value;
    }

    String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
