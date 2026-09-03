package com.example.matching.service.kg.impl;

import com.example.matching.config.Neo4jGraphProperties;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.service.kg.Neo4jGraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
@Service
@ConditionalOnBean(Driver.class)
@RequiredArgsConstructor
public class Neo4jGraphStoreImpl implements Neo4jGraphStore {

    private final Driver driver;
    private final Neo4jGraphProperties properties;

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isUsable());
        try (var session = driver.session(sessionConfig())) {
            String value = session.executeRead(tx -> tx.run("RETURN 'OK' AS status").single().get("status").asString());
            result.put("status", value);
        } catch (Exception e) {
            result.put("status", "FAIL");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> syncSnapshot(List<KgGraphNode> nodes, List<KgGraphEdge> edges) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isUsable());
        if (!properties.isUsable()) {
            result.put("status", "SKIPPED");
            return result;
        }
        try (var session = driver.session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> {
                tx.run("MATCH (n) DETACH DELETE n");
                for (KgGraphNode node : nodes) {
                    tx.run("""
                            MERGE (n:TalentGraphNode {nodeKey: $nodeKey})
                            SET n.nodeType = $nodeType,
                                n.refId = $refId,
                                n.label = $label,
                                n.category = $category,
                                n.levelValue = $levelValue,
                                n.status = $status,
                                n.weightValue = $weightValue,
                                n.metadataJson = $metadataJson
                            """, Map.of(
                            "nodeKey", safe(node.getNodeKey()),
                            "nodeType", safe(node.getNodeType()),
                            "refId", node.getRefId() == null ? "" : String.valueOf(node.getRefId()),
                            "label", safe(node.getLabel()),
                            "category", safe(node.getCategory()),
                            "levelValue", node.getLevelValue() == null ? "" : String.valueOf(node.getLevelValue()),
                            "status", safe(node.getStatus()),
                            "weightValue", node.getWeightValue() == null ? "" : node.getWeightValue().toPlainString(),
                            "metadataJson", safe(node.getMetadataJson())
                    ));
                }
                for (KgGraphEdge edge : edges) {
                    tx.run("""
                            MATCH (s:TalentGraphNode {nodeKey: $sourceNodeKey})
                            MATCH (t:TalentGraphNode {nodeKey: $targetNodeKey})
                            MERGE (s)-[r:GRAPH_RELATION {edgeKey: $edgeKey}]->(t)
                            SET r.edgeType = $edgeType,
                                r.weightValue = $weightValue,
                                r.confidenceScore = $confidenceScore,
                                r.metadataJson = $metadataJson
                            """, Map.of(
                            "sourceNodeKey", safe(edge.getSourceNodeKey()),
                            "targetNodeKey", safe(edge.getTargetNodeKey()),
                            "edgeKey", safe(edge.getEdgeKey()),
                            "edgeType", safe(edge.getEdgeType()),
                            "weightValue", edge.getWeightValue() == null ? "" : edge.getWeightValue().toPlainString(),
                            "confidenceScore", edge.getConfidenceScore() == null ? "" : edge.getConfidenceScore().toPlainString(),
                            "metadataJson", safe(edge.getMetadataJson())
                    ));
                }
            });
            result.put("status", "OK");
            result.put("nodeCount", nodes.size());
            result.put("edgeCount", edges.size());
        } catch (Exception e) {
            log.warn("Neo4j graph sync failed: {}", e.getMessage());
            result.put("status", "FAIL");
            result.put("message", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> queryPanorama(List<String> nodeTypes, String keyword, String category, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (var session = driver.session(sessionConfig())) {
            StringBuilder cypher = new StringBuilder("MATCH (n:TalentGraphNode) WHERE 1=1 ");
            Map<String, Object> params = new HashMap<>();
            if (nodeTypes != null && !nodeTypes.isEmpty()) {
                cypher.append("AND n.nodeType IN $nodeTypes ");
                params.put("nodeTypes", nodeTypes);
            }
            if (keyword != null && !keyword.isBlank()) {
                cypher.append("AND toLower(n.label) CONTAINS toLower($keyword) ");
                params.put("keyword", keyword);
            }
            if (category != null && !category.isBlank()) {
                cypher.append("AND n.category = $category ");
                params.put("category", category);
            }
            cypher.append("RETURN n LIMIT $limit");
            params.put("limit", Math.max(20, Math.min(limit, 500)));
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            session.executeRead(tx -> {
                tx.run(cypher.toString(), params).list(record -> {
                    var n = record.get("n").asNode();
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", n.get("nodeKey").asString());
                    node.put("type", n.get("nodeType").asString());
                    node.put("label", n.get("label").asString());
                    node.put("category", n.get("category").asString());
                    node.put("status", n.get("status").asString());
                    node.put("meta", Map.of("refId", n.get("refId").asString()));
                    nodes.add(node);
                    keys.add(n.get("nodeKey").asString());
                    return null;
                });
                return null;
            });
            if (nodes.isEmpty()) return Map.of("available", true, "nodes", List.of(), "edges", List.of());
            List<Map<String, Object>> edges = new ArrayList<>();
            session.executeRead(tx -> {
                tx.run("MATCH (s:TalentGraphNode)-[r:GRAPH_RELATION]->(t:TalentGraphNode) "
                                + "WHERE s.nodeKey IN $keys OR t.nodeKey IN $keys "
                                + "RETURN r.edgeKey AS id, s.nodeKey AS source, t.nodeKey AS target, "
                                + "r.edgeType AS type, r.weightValue AS weight", Map.of("keys", keys))
                        .list(record -> {
                            Map<String, Object> edge = new LinkedHashMap<>();
                            edge.put("id", record.get("id").asString());
                            edge.put("source", record.get("source").asString());
                            edge.put("target", record.get("target").asString());
                            edge.put("type", record.get("type").asString());
                            edge.put("weight", parseDouble(record.get("weight").asString(), 0.5));
                            edges.add(edge);
                            return null;
                        });
                return null;
            });
            result.put("available", true);
            result.put("nodes", nodes);
            result.put("edges", edges);
            result.put("source", "NEO4J");
            result.put("stats", Map.of("nodeCount", nodes.size(), "edgeCount", edges.size()));
            return result;
        } catch (Exception e) {
            log.warn("Neo4j graph query failed: {}", e.getMessage());
            return Map.of("available", false, "error", e.getMessage());
        }
    }

    private double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; }
    }

    @Override
    public Map<String, Object> syncIncremental(List<KgGraphNode> upsertNodes, List<KgGraphEdge> upsertEdges,
                                                List<String> deletedNodeKeys, List<String> deletedEdgeKeys) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isUsable());
        if (!properties.isUsable()) {
            result.put("status", "SKIPPED");
            return result;
        }
        try (var session = driver.session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> {
                for (String nodeKey : deletedNodeKeys) {
                    tx.run("MATCH (n:TalentGraphNode {nodeKey: $nodeKey}) DETACH DELETE n", Map.of("nodeKey", safe(nodeKey)));
                }
                for (String edgeKey : deletedEdgeKeys) {
                    tx.run("MATCH ()-[r:GRAPH_RELATION {edgeKey: $edgeKey}]->() DELETE r", Map.of("edgeKey", safe(edgeKey)));
                }
                for (KgGraphNode node : upsertNodes) {
                    mergeNode(tx, node);
                }
                for (KgGraphEdge edge : upsertEdges) {
                    mergeEdge(tx, edge);
                }
            });
            result.put("status", "OK");
            result.put("nodeCount", upsertNodes.size());
            result.put("edgeCount", upsertEdges.size());
        } catch (Exception e) {
            log.warn("Neo4j incremental graph sync failed: {}", e.getMessage());
            result.put("status", "FAIL");
            result.put("message", e.getMessage());
        }
        return result;
    }

    private void mergeNode(org.neo4j.driver.TransactionContext tx, KgGraphNode node) {
        tx.run("""
                MERGE (n:TalentGraphNode {nodeKey: $nodeKey})
                SET n.nodeType = $nodeType,
                    n.refId = $refId,
                    n.label = $label,
                    n.category = $category,
                    n.levelValue = $levelValue,
                    n.status = $status,
                    n.weightValue = $weightValue,
                    n.metadataJson = $metadataJson
                """, nodeParameters(node));
    }

    private void mergeEdge(org.neo4j.driver.TransactionContext tx, KgGraphEdge edge) {
        tx.run("""
                MATCH (s:TalentGraphNode {nodeKey: $sourceNodeKey})
                MATCH (t:TalentGraphNode {nodeKey: $targetNodeKey})
                MERGE (s)-[r:GRAPH_RELATION {edgeKey: $edgeKey}]->(t)
                SET r.edgeType = $edgeType,
                    r.weightValue = $weightValue,
                    r.confidenceScore = $confidenceScore,
                    r.metadataJson = $metadataJson
                """, edgeParameters(edge));
    }

    private Map<String, Object> nodeParameters(KgGraphNode node) {
        return Map.of(
                "nodeKey", safe(node.getNodeKey()), "nodeType", safe(node.getNodeType()),
                "refId", node.getRefId() == null ? "" : String.valueOf(node.getRefId()),
                "label", safe(node.getLabel()), "category", safe(node.getCategory()),
                "levelValue", node.getLevelValue() == null ? "" : String.valueOf(node.getLevelValue()),
                "status", safe(node.getStatus()),
                "weightValue", node.getWeightValue() == null ? "" : node.getWeightValue().toPlainString(),
                "metadataJson", safe(node.getMetadataJson()));
    }

    private Map<String, Object> edgeParameters(KgGraphEdge edge) {
        return Map.of(
                "sourceNodeKey", safe(edge.getSourceNodeKey()), "targetNodeKey", safe(edge.getTargetNodeKey()),
                "edgeKey", safe(edge.getEdgeKey()), "edgeType", safe(edge.getEdgeType()),
                "weightValue", edge.getWeightValue() == null ? "" : edge.getWeightValue().toPlainString(),
                "confidenceScore", edge.getConfidenceScore() == null ? "" : edge.getConfidenceScore().toPlainString(),
                "metadataJson", safe(edge.getMetadataJson()));
    }

    private SessionConfig sessionConfig() {
        return SessionConfig.builder()
                .withDatabase(properties.getDatabase())
                .build();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
