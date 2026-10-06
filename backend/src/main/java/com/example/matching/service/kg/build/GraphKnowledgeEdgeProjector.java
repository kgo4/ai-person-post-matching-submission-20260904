package com.example.matching.service.kg.build;

import com.example.matching.common.constant.SourceRefConstants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.AbilityTagDomainRel;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgRelationCandidate;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.mapper.kg.AbilityTagDomainRelMapper;
import com.example.matching.mapper.kg.KgRelationCandidateMapper;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.service.kg.support.KnowledgeNodeDependencyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

/**
 * 图谱知识域边投影：学习计划/步骤、项目任务、能力归属、知识节点层级与前置条件、审核关系。
 * <p>
 * 从 GraphEdgeProjectionService（650 行）中拆分的知识域边投影组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphKnowledgeEdgeProjector {

    private final LearningQueryPort learningQueryPort;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final AbilityTagDomainRelMapper abilityTagDomainRelMapper;
    private final KgRelationCandidateMapper relationCandidateMapper;
    private final KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver;
    private final GraphSnapshotWriter snapshotWriter;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final int GRAPH_SOURCE_PAGE_SIZE = 500;
    private static final String NODE_TYPE_EMPLOYEE = "EMPLOYEE";
    private static final String NODE_TYPE_POST = "POST";
    private static final String NODE_TYPE_ABILITY = "ABILITY";
    private static final String NODE_TYPE_LEARNING_RESOURCE = "LEARNING_RESOURCE";
    private static final String NODE_TYPE_LEARNING_PLAN = "LEARNING_PLAN";
    private static final String NODE_TYPE_LEARNING_STEP = "LEARNING_STEP";
    private static final String NODE_TYPE_PROJECT_TASK = "PROJECT_TASK";
    private static final String NODE_TYPE_KNOWLEDGE_DOMAIN = "KNOWLEDGE_DOMAIN";
    private static final String NODE_TYPE_KNOWLEDGE_NODE = "KNOWLEDGE_NODE";
    private static final String EDGE_TYPE_HAS_STEP = "HAS_STEP";
    private static final String EDGE_TYPE_TRAINS_ABILITY = "TRAINS_ABILITY";
    private static final String EDGE_TYPE_HAS_PROJECT_TASK = "HAS_PROJECT_TASK";
    private static final String EDGE_TYPE_HAS_LEARNING_PLAN = "HAS_LEARNING_PLAN";
    private static final String EDGE_TYPE_BELONGS_TO_DOMAIN = "BELONGS_TO_DOMAIN";
    private static final String EDGE_TYPE_HAS_KNOWLEDGE_NODE = "HAS_KNOWLEDGE_NODE";
    private static final String EDGE_TYPE_PARENT_OF = "PARENT_OF";
    private static final String EDGE_TYPE_PREREQUISITE_OF = "PREREQUISITE_OF";
    private static final String EDGE_TYPE_RELATED_TO = "RELATED_TO";
    public void buildLearningPlanEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            int page = 1;
            List<LearningQueryPort.LearningPathPlanDTO> batch;
            List<KgGraphEdge> edges = new ArrayList<>();
            do {
                batch = learningQueryPort.listPlansPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
                for (var plan : batch) {
                    String empKey = snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, plan.empId());
                    String planKey = snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_PLAN, plan.id());
                    KgGraphEdge empEdge = new KgGraphEdge();
                    empEdge.setEdgeKey(snapshotWriter.generateEdgeKey(empKey, EDGE_TYPE_HAS_LEARNING_PLAN, planKey));
                    empEdge.setSourceNodeKey(empKey);
                    empEdge.setTargetNodeKey(planKey);
                    empEdge.setEdgeType(EDGE_TYPE_HAS_LEARNING_PLAN);
                    empEdge.setWeightValue(BigDecimal.ONE);
                    edges.add(empEdge);

                    var steps = learningQueryPort.listStepsByPlanId(plan.id());
                    for (var step : steps) {
                        String stepKey = snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_STEP, step.id());
                        KgGraphEdge stepEdge = new KgGraphEdge();
                        stepEdge.setEdgeKey(snapshotWriter.generateEdgeKey(planKey, EDGE_TYPE_HAS_STEP, stepKey));
                        stepEdge.setSourceNodeKey(planKey);
                        stepEdge.setTargetNodeKey(stepKey);
                        stepEdge.setEdgeType(EDGE_TYPE_HAS_STEP);
                        stepEdge.setWeightValue(BigDecimal.ONE);
                        edges.add(stepEdge);

                        if (step.abilityTagId() != null) {
                            String abilityKey = snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, step.abilityTagId());
                            KgGraphEdge abilityEdge = new KgGraphEdge();
                            abilityEdge.setEdgeKey(
                                    snapshotWriter.generateEdgeKey(stepKey, EDGE_TYPE_TRAINS_ABILITY, abilityKey));
                            abilityEdge.setSourceNodeKey(stepKey);
                            abilityEdge.setTargetNodeKey(abilityKey);
                            abilityEdge.setEdgeType(EDGE_TYPE_TRAINS_ABILITY);
                            abilityEdge.setWeightValue(BigDecimal.ONE);
                            edges.add(abilityEdge);
                        }
                    }
                }
            } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_HAS_LEARNING_PLAN, edges.size());
        } catch (Exception e) {
            log.warn("构建学习路径边失败，已跳过: type={}, error={}", EDGE_TYPE_HAS_LEARNING_PLAN, e.getMessage(), e);
            counter.put(EDGE_TYPE_HAS_LEARNING_PLAN, 0);
        }
    }

    public void buildLearningStepEdges(Map<String, Integer> counter) {
        counter.put(EDGE_TYPE_HAS_STEP, 0);
        counter.put(EDGE_TYPE_TRAINS_ABILITY, 0);
    }

    public void buildProjectTaskEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            int page = 1;
            List<LearningQueryPort.LearningProjectTaskDTO> batch;
            List<KgGraphEdge> edges = new ArrayList<>();
            do {
                batch = learningQueryPort.listProjectTasksPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
                for (var task : batch) {
                    String stepKey = snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_STEP, task.stepId());
                    String taskKey = snapshotWriter.generateNodeKey(NODE_TYPE_PROJECT_TASK, task.id());
                    KgGraphEdge taskEdge = new KgGraphEdge();
                    taskEdge.setEdgeKey(snapshotWriter.generateEdgeKey(stepKey, EDGE_TYPE_HAS_PROJECT_TASK, taskKey));
                    taskEdge.setSourceNodeKey(stepKey);
                    taskEdge.setTargetNodeKey(taskKey);
                    taskEdge.setEdgeType(EDGE_TYPE_HAS_PROJECT_TASK);
                    taskEdge.setWeightValue(BigDecimal.ONE);
                    edges.add(taskEdge);
                }
            } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_HAS_PROJECT_TASK, edges.size());
        } catch (Exception e) {
            log.warn("构建项目任务边失败，已跳过: type={}, error={}", EDGE_TYPE_HAS_PROJECT_TASK, e.getMessage(), e);
            counter.put(EDGE_TYPE_HAS_PROJECT_TASK, 0);
        }
    }

    public void buildAbilityBelongsToDomainEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        List<AbilityTagDomainRel> rels = abilityTagDomainRelMapper.selectList(
                Wrappers.<AbilityTagDomainRel>lambdaQuery());
        List<KgGraphEdge> edges = new ArrayList<>();
        for (var rel : rels) {
            String abilityKey = snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, rel.getTagId());
            String domainKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_DOMAIN, rel.getDomainId());
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(snapshotWriter.generateEdgeKey(abilityKey, EDGE_TYPE_BELONGS_TO_DOMAIN, domainKey));
            edge.setSourceNodeKey(abilityKey);
            edge.setTargetNodeKey(domainKey);
            edge.setEdgeType(EDGE_TYPE_BELONGS_TO_DOMAIN);
            edge.setWeightValue(BigDecimal.ONE);
            edges.add(edge);
        }
        snapshotWriter.batchInsertEdges(edges, ctx);
        counter.put(EDGE_TYPE_BELONGS_TO_DOMAIN, edges.size());
    }

    public void buildDomainHasKnowledgeNodeEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<KnowledgeNode> knowledgeNodes = knowledgeNodeMapper.selectList(
                    Wrappers.<KnowledgeNode>lambdaQuery()
                            .eq(KnowledgeNode::getIsDeleted, 0)
                            .eq(KnowledgeNode::getStatus, "ACTIVE"));
            List<KgGraphEdge> edges = new ArrayList<>();
            for (KnowledgeNode knowledgeNode : knowledgeNodes) {
                String domainKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_DOMAIN,
                        knowledgeNode.getDomainId());
                String nodeKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE, knowledgeNode.getId());
                KgGraphEdge edge = new KgGraphEdge();
                edge.setEdgeKey(snapshotWriter.generateEdgeKey(domainKey, EDGE_TYPE_HAS_KNOWLEDGE_NODE, nodeKey));
                edge.setSourceNodeKey(domainKey);
                edge.setTargetNodeKey(nodeKey);
                edge.setEdgeType(EDGE_TYPE_HAS_KNOWLEDGE_NODE);
                edge.setWeightValue(BigDecimal.ONE);
                edges.add(edge);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_HAS_KNOWLEDGE_NODE, edges.size());
        } catch (Exception e) {
            log.warn("构建知识领域-知识点边失败，已跳过: error={}", e.getMessage(), e);
            counter.put(EDGE_TYPE_HAS_KNOWLEDGE_NODE, 0);
        }
    }

    public void buildKnowledgeNodeParentOfEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<KnowledgeNode> knowledgeNodes = knowledgeNodeMapper.selectList(
                    Wrappers.<KnowledgeNode>lambdaQuery()
                            .eq(KnowledgeNode::getIsDeleted, 0)
                            .eq(KnowledgeNode::getStatus, "ACTIVE")
                            .isNotNull(KnowledgeNode::getParentId));
            List<KgGraphEdge> edges = new ArrayList<>();
            for (KnowledgeNode knowledgeNode : knowledgeNodes) {
                String parentKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE,
                        knowledgeNode.getParentId());
                String childKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE, knowledgeNode.getId());
                KgGraphEdge edge = new KgGraphEdge();
                edge.setEdgeKey(snapshotWriter.generateEdgeKey(parentKey, EDGE_TYPE_PARENT_OF, childKey));
                edge.setSourceNodeKey(parentKey);
                edge.setTargetNodeKey(childKey);
                edge.setEdgeType(EDGE_TYPE_PARENT_OF);
                edge.setWeightValue(BigDecimal.ONE);
                edges.add(edge);
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_PARENT_OF, edges.size());
        } catch (Exception e) {
            log.warn("构建知识点父子关系边失败，已跳过: error={}", e.getMessage(), e);
            counter.put(EDGE_TYPE_PARENT_OF, 0);
        }
    }

    public void buildKnowledgeNodePrerequisiteEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        try {
            List<KnowledgeNode> knowledgeNodes = knowledgeNodeMapper.selectList(
                    Wrappers.<KnowledgeNode>lambdaQuery()
                            .eq(KnowledgeNode::getIsDeleted, 0)
                            .eq(KnowledgeNode::getStatus, "ACTIVE"));
            Set<Long> validNodeIds = knowledgeNodes.stream()
                    .map(KnowledgeNode::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<KgGraphEdge> edges = new ArrayList<>();
            for (KnowledgeNode dependentNode : knowledgeNodes) {
                if (dependentNode.getId() == null) {
                    continue;
                }
                for (Long prerequisiteId : knowledgeNodeDependencyResolver.parsePrerequisiteIds(dependentNode)) {
                    if (!validNodeIds.contains(prerequisiteId) || prerequisiteId.equals(dependentNode.getId())) {
                        continue;
                    }
                    String prerequisiteKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE, prerequisiteId);
                    String dependentKey = snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE,
                            dependentNode.getId());
                    KgGraphEdge edge = new KgGraphEdge();
                    edge.setEdgeKey(
                            snapshotWriter.generateEdgeKey(EDGE_TYPE_PREREQUISITE_OF, prerequisiteKey, dependentKey));
                    edge.setSourceNodeKey(prerequisiteKey);
                    edge.setTargetNodeKey(dependentKey);
                    edge.setEdgeType(EDGE_TYPE_PREREQUISITE_OF);
                    edge.setWeightValue(BigDecimal.ONE);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("source", "knowledge_node.prerequisites_json");
                    metadata.put("sourceRefs",
                            List.of(SourceRefConstants.PREFIX_KG + "KNOWLEDGE_NODE:" + dependentNode.getId()));
                    metadata.put("governanceMode", "KNOWLEDGE_NODE_MASTER_DATA");
                    metadata.put("reviewStatus", "SYSTEM_VERIFIED");
                    edge.setMetadataJson(toJson(metadata));
                    edges.add(edge);
                }
            }
            snapshotWriter.batchInsertEdges(edges, ctx);
            counter.put(EDGE_TYPE_PREREQUISITE_OF, edges.size());
        } catch (Exception e) {
            log.warn("构建知识节点前置依赖边失败，已跳过: error={}", e.getMessage(), e);
            counter.put(EDGE_TYPE_PREREQUISITE_OF, 0);
        }
    }

    public void buildApprovedRelatedToEdges(Map<String, Integer> counter, GraphBuildContext ctx) {
        List<KgRelationCandidate> candidates = relationCandidateMapper.selectList(
                Wrappers.<KgRelationCandidate>lambdaQuery()
                        .eq(KgRelationCandidate::getRelationType, EDGE_TYPE_RELATED_TO)
                        .eq(KgRelationCandidate::getReviewStatus, "APPROVED"));
        List<KgGraphEdge> edges = new ArrayList<>();
        for (KgRelationCandidate candidate : candidates) {
            if (!snapshotWriter.graphNodeExists(candidate.getSourceNodeKey())
                    || !snapshotWriter.graphNodeExists(candidate.getTargetNodeKey())) {
                log.warn("Skip approved RELATED_TO candidate {} because graph nodes are unavailable",
                        candidate.getCandidateCode());
                continue;
            }
            BigDecimal score = candidate.getSemanticScore();
            if (score == null) {
                log.warn("Skip approved RELATED_TO candidate {} because semantic score is missing",
                        candidate.getCandidateCode());
                continue;
            }
            KgGraphEdge edge = new KgGraphEdge();
            edge.setEdgeKey(snapshotWriter.generateEdgeKey(EDGE_TYPE_RELATED_TO,
                    candidate.getSourceNodeKey(), candidate.getTargetNodeKey()));
            edge.setSourceNodeKey(candidate.getSourceNodeKey());
            edge.setTargetNodeKey(candidate.getTargetNodeKey());
            edge.setEdgeType(EDGE_TYPE_RELATED_TO);
            edge.setWeightValue(score);
            edge.setConfidenceScore(score.multiply(BigDecimal.valueOf(100)));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceRefs", snapshotWriter.readSourceRefs(candidate.getSourceRefsJson()));
            metadata.put("reviewStatus", "APPROVED");
            metadata.put("discoveryMethod", candidate.getDiscoveryMethod());
            metadata.put("relationCandidateId", candidate.getId());
            edge.setMetadataJson(toJson(metadata));
            edges.add(edge);
        }
        snapshotWriter.batchInsertEdges(edges, ctx);
        counter.put(EDGE_TYPE_RELATED_TO, edges.size());
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
