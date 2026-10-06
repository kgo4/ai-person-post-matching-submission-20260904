package com.example.matching.service.kg.build;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图谱业务边投影：岗位要求、员工能力、证据支撑、RAG 派生、学习资源推荐、演化事件、匹配关系。
 * <p>
 * 从 GraphEdgeProjectionService（650 行）中拆分的业务域边投影组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphBusinessEdgeProjector {

    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final LearningQueryPort learningQueryPort;
    private final MatchingQueryPort matchingQueryPort;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final ContestQueryPort contestQueryPort;
    private final EvolutionQueryPort evolutionQueryPort;
    private final GraphSnapshotWriter snapshotWriter;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final int GRAPH_SOURCE_PAGE_SIZE = 500;
    private static final String NODE_TYPE_POST = "POST";
    private static final String NODE_TYPE_POST_FAMILY = "POST_FAMILY";
    private static final String NODE_TYPE_ABILITY = "ABILITY";
    private static final String NODE_TYPE_POST_SKILL_POINT = "POST_SKILL_POINT";
    private static final String NODE_TYPE_TECH_STACK = "POST_TECH_STACK";
    private static final String NODE_TYPE_EMPLOYEE = "EMPLOYEE";
    private static final String NODE_TYPE_EVIDENCE = "EVIDENCE";
    private static final String NODE_TYPE_RAG_DOCUMENT = "RAG_DOCUMENT";
    private static final String NODE_TYPE_LEARNING_RESOURCE = "LEARNING_RESOURCE";
    private static final String NODE_TYPE_EVOLUTION_EVENT = "EVOLUTION_EVENT";
    private static final String EDGE_TYPE_REQUIRES = "REQUIRES";
    private static final String EDGE_TYPE_HAS_ABILITY = "HAS_ABILITY";
    private static final String EDGE_TYPE_HAS_SKILL_POINT = "HAS_SKILL_POINT";
    private static final String EDGE_TYPE_SUPPORTED_BY = "SUPPORTED_BY";
    private static final String EDGE_TYPE_DERIVED_FROM = "DERIVED_FROM";
    private static final String EDGE_TYPE_RECOMMENDS = "RECOMMENDS";
    private static final String EDGE_TYPE_EVOLVED_TO = "EVOLVED_TO";
    private static final String EDGE_TYPE_MATCHED_WITH = "MATCHED_WITH";
    private static final String EDGE_TYPE_EVALUATED_BY = "EVALUATED_BY";
    public void buildPostRequiresAbilityEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        var models = postQueryPort.listActivePostAbilityModels(0);
        List<KgGraphEdge> edges = new ArrayList<>();
        for (var m : models) {
            if (m.id() == null || m.postId() == null || m.abilityName() == null || m.abilityName().isBlank()) {
                continue;
            }
            String skillPointNodeKey = "POST_SKILL_POINT:" + m.id();
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(snapshotWriter.generateEdgeKey(EDGE_TYPE_REQUIRES,
                    snapshotWriter.generateNodeKey(NODE_TYPE_POST, m.postId()),
                    skillPointNodeKey));
            edge.setEdgeType(EDGE_TYPE_REQUIRES);
            edge.setSourceNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_POST, m.postId()));
            edge.setTargetNodeKey(skillPointNodeKey);
            edge.setWeightValue(m.weight());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("minRequiredLevel", m.minRequiredLevel());
            meta.put("isRequired", m.isRequired());
            meta.put("isCore", m.isCore());
            meta.put("skillType", Integer.valueOf(1).equals(m.isRequired()) ? "REQUIRED" : "BONUS");
            meta.put("sourceType", SourceRefConstants.ENTITY_POST_ABILITY_MODEL);
            if (m.id() != null) {
                meta.put("sourceRef", SourceRefConstants.postAbilityModelFactRef(m.id()));
            }
            edge.setMetadataJson(toJson(meta));
            edges.add(edge);

            String stack = m.techStack() == null || m.techStack().isBlank() ? "通用工程能力" : m.techStack().trim();
            KgGraphEdge stackEdge = new KgGraphEdge();
            stackEdge.setEdgeKey(snapshotWriter.generateEdgeKey(EDGE_TYPE_HAS_SKILL_POINT,
                    "POST_TECH_STACK:" + stack, skillPointNodeKey));
            stackEdge.setEdgeType(EDGE_TYPE_HAS_SKILL_POINT);
            stackEdge.setSourceNodeKey("POST_TECH_STACK:" + stack);
            stackEdge.setTargetNodeKey(skillPointNodeKey);
            stackEdge.setWeightValue(m.weight());
            stackEdge.setMetadataJson(toJson(Map.of("sourceType", SourceRefConstants.ENTITY_POST_ABILITY_MODEL)));
            edges.add(stackEdge);
        }
        snapshotWriter.batchInsertEdges(edges, ctx);
        counter.put(EDGE_TYPE_REQUIRES, (int) edges.stream()
                .filter(edge -> EDGE_TYPE_REQUIRES.equals(edge.getEdgeType())).count());
        counter.put(EDGE_TYPE_HAS_SKILL_POINT, (int) edges.stream()
                .filter(edge -> EDGE_TYPE_HAS_SKILL_POINT.equals(edge.getEdgeType())).count());
    }

    public void buildEmployeeHasAbilityEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        int total = 0;
        int page = 1;
        List<TalentQueryPort.EmployeeAbilityDTO> batch;
        do {
            batch = talentQueryPort.listAbilitiesPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            List<KgGraphEdge> edges = new ArrayList<>(batch.size());
            for (var a : batch) {
                if (a.id() == null || a.abilityName() == null || a.abilityName().isBlank()) {
                    continue;
                }
                // Formal employee abilities are identified by their own abilityName
                // fact. tagId is retained in metadata as optional enrichment only.
                String abilityNodeKey = "EMP_ABILITY:" + a.id();
                KgGraphEdge edge = new KgGraphEdge();
                edge.setEdgeKey(snapshotWriter.generateEdgeKey(EDGE_TYPE_HAS_ABILITY,
                        snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, a.empId()),
                        abilityNodeKey));
                edge.setEdgeType("HAS_ABILITY_FACT");
                edge.setSourceNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, a.empId()));
                edge.setTargetNodeKey(abilityNodeKey);
                edge.setWeightValue(a.sourceWeight());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("masteryLevel", a.masteryLevel());
                meta.put("abilityName", a.abilityName());
                meta.put("evaluationSource", a.evaluationSource());
                meta.put("sourceWeight", a.sourceWeight());
                meta.put("sourceType", SourceRefConstants.ENTITY_EMP_ABILITY);
                if (a.id() != null) {
                    meta.put("sourceRef", SourceRefConstants.empAbilityFactRef(a.id()));
                }
                edge.setMetadataJson(toJson(meta));
                edges.add(edge);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            total += edges.size();
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
            counter.put(EDGE_TYPE_HAS_ABILITY, total);
    }

    public void buildPostFamilyRequiresAbilityEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        List<PostQueryPort.PostPrototypeTagDTO> tags = postQueryPort.listAllPrototypeTags();
        List<KgGraphEdge> edges = new ArrayList<>();
        for (var pt : tags) {
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(snapshotWriter.generateEdgeKey(EDGE_TYPE_REQUIRES,
                    snapshotWriter.generateNodeKey(NODE_TYPE_POST_FAMILY, pt.prototypeId()),
                    snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, pt.tagId())));
            edge.setEdgeType(EDGE_TYPE_REQUIRES);
            edge.setSourceNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_POST_FAMILY, pt.prototypeId()));
            edge.setTargetNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, pt.tagId()));
            edge.setWeightValue(BigDecimal.ONE);
            edges.add(edge);
        }
        snapshotWriter.batchInsertEdges(edges, ctx);
        counter.put(NODE_TYPE_POST_FAMILY + "_" + EDGE_TYPE_REQUIRES, edges.size());
    }

    public void buildEvidenceSupportedByEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<ContestQueryPort.ContestEvidenceDTO> items = contestQueryPort.listAllEvidence(0);
            List<KgGraphEdge> edges = new ArrayList<>();
            for (var item : items) {
                appendEvidenceSupportEdges(edges, item);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_SUPPORTED_BY, edges.size());
        } catch (Exception e) {
            log.warn("构建证据支持边失败，已跳过 (graph build continues): type={}, error={}",
                    EDGE_TYPE_SUPPORTED_BY, e.getMessage());
            counter.put(EDGE_TYPE_SUPPORTED_BY, 0);
        }
    }

    public void appendEvidenceSupportEdges(List<KgGraphEdge> edges, ContestQueryPort.ContestEvidenceDTO item) {
        if (item.id() == null) {
            return;
        }
        String targetType = item.targetType();
        if ("ABILITY_TAG".equals(targetType)) {
            addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, item.targetRefId()), item);
            return;
        }
        if ("EMP_ABILITY".equals(targetType)) {
            if (item.targetRefId() != null) {
                var ability = talentQueryPort.getEmpAbilityById(item.targetRefId());
                if (ability != null) {
                    addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, ability.empId()), item);
                    if (ability.tagId() != null) {
                        addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, ability.tagId()), item);
                    } else {
                        addSupportedByEdge(edges, "EMP_ABILITY:" + ability.id(), item);
                    }
                    return;
                }
            }
            if (item.tagId() != null) {
                addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, item.tagId()), item);
            }
            return;
        }
        if ("POST_ABILITY_MODEL".equals(targetType)) {
            if (item.targetRefId() != null) {
                var model = postQueryPort.getPostAbilityModelById(item.targetRefId());
                if (model != null) {
                    addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_POST, model.postId()), item);
                    addSupportedByEdge(edges, "POST_SKILL_POINT:" + model.id(), item);
                    if (model.tagId() != null) {
                        addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, model.tagId()), item);
                    }
                    return;
                }
            }
            if (item.tagId() != null) {
                addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, item.tagId()), item);
            }
            return;
        }
        if (item.targetRefId() != null) {
            addSupportedByEdge(edges, snapshotWriter.generateNodeKey(NODE_TYPE_POST, item.targetRefId()), item);
        }
    }

    public void addSupportedByEdge(List<KgGraphEdge> edges, String businessNodeKey,
                                     ContestQueryPort.ContestEvidenceDTO item) {
        if (businessNodeKey == null || businessNodeKey.endsWith(":null")) {
            return;
        }
        String evidenceNodeKey = snapshotWriter.generateNodeKey(NODE_TYPE_EVIDENCE, item.id());
        KgGraphEdge edge = new KgGraphEdge();
        edge.setEdgeKey(snapshotWriter.generateEdgeKey(businessNodeKey, EDGE_TYPE_SUPPORTED_BY, evidenceNodeKey));
        edge.setSourceNodeKey(businessNodeKey);
        edge.setTargetNodeKey(evidenceNodeKey);
        edge.setEdgeType(EDGE_TYPE_SUPPORTED_BY);
        edge.setWeightValue(item.confidenceScore());
        edge.setConfidenceScore(item.credibilityScore());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidenceStatus", item.evidenceStatus());
        metadata.put("sourceRefs", List.of(SourceRefConstants.contestEvidenceRef(item.id())));
        edge.setMetadataJson(toJson(metadata));
        edges.add(edge);
    }

    public void buildRagDocumentDerivedFromEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<RagKnowledgeDocument> docs = knowledgeDocumentService.listAllActiveDocuments(0);
            List<KgGraphEdge> edges = new ArrayList<>();
            for (RagKnowledgeDocument doc : docs) {
                appendRagDocumentDerivedFromEdges(edges, doc);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_DERIVED_FROM, edges.size());
        } catch (Exception e) {
            log.warn("构建RAG文档来源边失败，已跳过: error={}", e.getMessage());
            counter.put(EDGE_TYPE_DERIVED_FROM, 0);
        }
    }

    public void appendRagDocumentDerivedFromEdges(List<KgGraphEdge> edges, RagKnowledgeDocument doc) {
        if (doc.getId() == null || doc.getSourceRefId() == null) {
            return;
        }
        String docNodeKey = snapshotWriter.generateNodeKey(NODE_TYPE_RAG_DOCUMENT, doc.getId());
        if ("JD_IMPORT".equals(doc.getSourceType())) {
            addDerivedFromEdge(edges, docNodeKey,
                    snapshotWriter.generateNodeKey(NODE_TYPE_POST, doc.getSourceRefId()));
        } else if ("ABILITY_TAG".equals(doc.getSourceType())) {
            addDerivedFromEdge(edges, docNodeKey,
                    snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, doc.getSourceRefId()));
        } else if ("POST_PROTOTYPE".equals(doc.getSourceType())) {
            addDerivedFromEdge(edges, docNodeKey,
                    snapshotWriter.generateNodeKey(NODE_TYPE_POST_FAMILY, doc.getSourceRefId()));
        } else if ("EMP_ABILITY".equals(doc.getSourceType())) {
            var ability = talentQueryPort.getEmpAbilityById(doc.getSourceRefId());
            if (ability != null) {
                addDerivedFromEdge(edges, docNodeKey,
                        snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, ability.empId()));
            }
        }
    }

    public void addDerivedFromEdge(List<KgGraphEdge> edges, String docNodeKey, String businessNodeKey) {
        if (businessNodeKey == null || businessNodeKey.endsWith(":null")) {
            return;
        }
        KgGraphEdge edge = new KgGraphEdge();
        edge.setEdgeKey(snapshotWriter.generateEdgeKey(docNodeKey, EDGE_TYPE_DERIVED_FROM, businessNodeKey));
        edge.setSourceNodeKey(docNodeKey);
        edge.setTargetNodeKey(businessNodeKey);
        edge.setEdgeType(EDGE_TYPE_DERIVED_FROM);
        edges.add(edge);
    }

    public void buildLearningResourceRecommendsEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        var resources = learningQueryPort.listActiveResources(0);
        List<KgGraphEdge> edges = new ArrayList<>();
        for (var r : resources) {
            if (r.tagId() == null) {
                continue;
            }
            KgGraphEdge edge = new KgGraphEdge();
            String sourceKey = snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, r.tagId());
            String targetKey = snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_RESOURCE, r.id());
            edge.setEdgeKey(snapshotWriter.generateEdgeKey(sourceKey, EDGE_TYPE_RECOMMENDS, targetKey));
            edge.setSourceNodeKey(sourceKey);
            edge.setTargetNodeKey(targetKey);
            edge.setEdgeType(EDGE_TYPE_RECOMMENDS);
            edge.setWeightValue(BigDecimal.ONE);
            edges.add(edge);
        }
        snapshotWriter.batchInsertEdges(edges, ctx);
        counter.put(EDGE_TYPE_RECOMMENDS, edges.size());
    }

    public void buildEvolutionEventEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<EvolutionQueryPort.EvolutionTaskDTO> tasks = evolutionQueryPort.listAllTasks(0);
            List<KgGraphEdge> edges = new ArrayList<>();
            for (var task : tasks) {
                if (task.postId() == null) {
                    continue;
                }
                String sourceKey = snapshotWriter.generateNodeKey(NODE_TYPE_POST, task.postId());
                String targetKey = snapshotWriter.generateNodeKey(NODE_TYPE_EVOLUTION_EVENT, task.id());
                KgGraphEdge edge = new KgGraphEdge();
                edge.setEdgeKey(snapshotWriter.generateEdgeKey(sourceKey, EDGE_TYPE_EVOLVED_TO, targetKey));
                edge.setSourceNodeKey(sourceKey);
                edge.setTargetNodeKey(targetKey);
                edge.setEdgeType(EDGE_TYPE_EVOLVED_TO);
                edges.add(edge);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_EVOLVED_TO, edges.size());
        } catch (Exception e) {
            log.warn("构建演化事件边失败，已跳过: error={}", e.getMessage());
            counter.put(EDGE_TYPE_EVOLVED_TO, 0);
        }
    }

    public void buildMatchedWithEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            int total = 0;
            int page = 1;
            List<MatchingQueryPort.MatchingRecordDTO> batch;
            do {
                batch = matchingQueryPort.listRecordsPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
                List<KgGraphEdge> edges = new ArrayList<>(batch.size());
                for (var record : batch) {
                    KgGraphEdge edge = new KgGraphEdge();
                    String sourceKey = snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, record.empId());
                    String targetKey = snapshotWriter.generateNodeKey(NODE_TYPE_POST, record.postId());
                    edge.setEdgeKey(snapshotWriter.generateEdgeKey(sourceKey, EDGE_TYPE_MATCHED_WITH, targetKey));
                    edge.setSourceNodeKey(sourceKey);
                    edge.setTargetNodeKey(targetKey);
                    edge.setEdgeType(EDGE_TYPE_MATCHED_WITH);
                    edge.setWeightValue(record.aiMatchScore());
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("screeningLevel", record.screeningLevel());
                    edge.setMetadataJson(toJson(meta));
                    edges.add(edge);
                }
                snapshotWriter.batchInsertEdges(edges, ctx);
                total += edges.size();
            } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
            counter.put(EDGE_TYPE_MATCHED_WITH, total);
        } catch (Exception e) {
            log.warn("构建匹配边失败，已跳过: error={}", e.getMessage());
            counter.put(EDGE_TYPE_MATCHED_WITH, 0);
        }
    }

    public void buildEvaluatedByEdges(Map<String, Integer> counter) {
        counter.put(EDGE_TYPE_EVALUATED_BY, 0);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("图谱边元数据序列化失败: error={}", e.getMessage());
            return "{}";
        }
    }
}
