package com.example.matching.service.kg.build;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.kg.KnowledgeDomainMapper;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.support.KnowledgeNodeDependencyResolver;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphNodeProjectionService {

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
    private static final String NODE_TYPE_LEARNING_PLAN = "LEARNING_PLAN";
    private static final String NODE_TYPE_LEARNING_STEP = "LEARNING_STEP";
    private static final String NODE_TYPE_PROJECT_TASK = "PROJECT_TASK";
    private static final String NODE_TYPE_KNOWLEDGE_DOMAIN = "KNOWLEDGE_DOMAIN";
    private static final String NODE_TYPE_KNOWLEDGE_NODE = "KNOWLEDGE_NODE";

    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final LearningQueryPort learningQueryPort;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final ContestQueryPort contestQueryPort;
    private final EvolutionQueryPort evolutionQueryPort;

    private final KnowledgeDomainMapper knowledgeDomainMapper;
    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver;

    private final ObjectMapper objectMapper;
    private final GraphSnapshotWriter snapshotWriter;

    public Map<String, Integer> projectNodes(GraphBuildContext ctx) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        buildPostNodes(counter);
        buildPostCapabilityNodes(counter);
        buildPostFamilyNodes(counter);
        buildAbilityNodes(counter);
        buildEmployeeNodes(counter);
        buildEvidenceNodes(counter);
        buildRagDocumentNodes(counter);
        buildLearningResourceNodes(counter);
        buildEvolutionEventNodes(counter);
        buildLearningPlanNodes(counter);
        buildLearningStepNodes(counter);
        buildProjectTaskNodes(counter);
        buildKnowledgeDomainNodes(counter);
        buildKnowledgeNodeNodes(counter);
        return counter;
    }

    private void buildPostNodes(Map<String, Integer> counter) {
        var posts = postQueryPort.listActivePosts(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        for (var post : posts) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_POST, post.id()));
            node.setNodeType(NODE_TYPE_POST);
            node.setRefId(post.id());
            node.setLabel(post.postName());
            node.setCategory(post.postLevel());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("postCode", post.postCode());
            meta.put("departmentId", post.departmentId());
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_POST, nodes.size());
    }

    private void buildPostFamilyNodes(Map<String, Integer> counter) {
        var prototypes = postQueryPort.listActivePrototypes(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        for (var p : prototypes) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_POST_FAMILY, p.id()));
            node.setNodeType(NODE_TYPE_POST_FAMILY);
            node.setRefId(p.id());
            node.setLabel(p.prototypeName());
            node.setCategory(p.category());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("industry", p.industry());
            meta.put("description", p.description());
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_POST_FAMILY, nodes.size());
    }

    /**
     * Project the source-of-truth role model as independent skill-point facts.
     * A taxonomy tag is deliberately not needed for either node creation or display.
     */
    private void buildPostCapabilityNodes(Map<String, Integer> counter) {
        var models = postQueryPort.listActivePostAbilityModels(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        Map<String, KgGraphNode> stacks = new LinkedHashMap<>();
        for (var model : models) {
            if (model.id() == null || model.abilityName() == null || model.abilityName().isBlank()) {
                continue;
            }
            String stack = model.techStack() == null || model.techStack().isBlank()
                    ? "通用工程能力" : model.techStack().trim();
            KgGraphNode skillPoint = new KgGraphNode();
            skillPoint.setNodeKey("POST_SKILL_POINT:" + model.id());
            skillPoint.setNodeType(NODE_TYPE_POST_SKILL_POINT);
            skillPoint.setRefId(model.id());
            skillPoint.setLabel(model.abilityName().trim());
            skillPoint.setCategory(stack);
            skillPoint.setLevelValue(model.minRequiredLevel());
            skillPoint.setStatus("ACTIVE");
            skillPoint.setWeightValue(model.weight() == null ? BigDecimal.ONE : model.weight());
            Map<String, Object> skillMeta = new LinkedHashMap<>();
            skillMeta.put("postId", model.postId());
            skillMeta.put("abilityName", model.abilityName().trim());
            skillMeta.put("techStack", stack);
            skillMeta.put("skillPointKey", model.skillPointKey());
            skillMeta.put("tagId", model.tagId());
            skillMeta.put("minRequiredLevel", model.minRequiredLevel());
            skillMeta.put("isCore", model.isCore());
            skillMeta.put("isRequired", model.isRequired());
            skillPoint.setMetadataJson(snapshotWriter.toJson(skillMeta));
            nodes.add(skillPoint);

            stacks.computeIfAbsent(stack, ignored -> {
                KgGraphNode techStack = new KgGraphNode();
                techStack.setNodeKey("POST_TECH_STACK:" + stack);
                techStack.setNodeType(NODE_TYPE_TECH_STACK);
                techStack.setLabel(stack);
                techStack.setCategory("TECH_STACK");
                techStack.setStatus("ACTIVE");
                techStack.setWeightValue(BigDecimal.ONE);
                return techStack;
            });
        }
        nodes.addAll(stacks.values());
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_POST_SKILL_POINT, (int) nodes.stream()
                .filter(node -> NODE_TYPE_POST_SKILL_POINT.equals(node.getNodeType())).count());
        counter.put(NODE_TYPE_TECH_STACK, stacks.size());
    }

    private void buildAbilityNodes(Map<String, Integer> counter) {
        var tags = tagQueryPort.listActiveTags(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        Map<Long, String> formalNamesByTag = new LinkedHashMap<>();
        List<TalentQueryPort.EmployeeAbilityDTO> employeeAbilities = new ArrayList<>();
        int page = 1;
        List<TalentQueryPort.EmployeeAbilityDTO> batch;
        do {
            batch = talentQueryPort.listAbilitiesPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            employeeAbilities.addAll(batch);
            for (var ability : batch) {
                if (ability.tagId() != null && ability.abilityName() != null && !ability.abilityName().isBlank()) {
                    formalNamesByTag.putIfAbsent(ability.tagId(), ability.abilityName().trim());
                }
            }
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
        for (var tag : tags) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_ABILITY, tag.id()));
            node.setNodeType(NODE_TYPE_ABILITY);
            node.setRefId(tag.id());
            node.setLabel(formalNamesByTag.getOrDefault(tag.id(), tag.tagName()));
            node.setCategory(tag.tagCategory());
            node.setLevelValue(tag.tagLevel());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("tagCode", tag.tagCode());
            meta.put("parentId", tag.parentId());
            meta.put("formalAbilityName", formalNamesByTag.get(tag.id()));
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        for (var ability : employeeAbilities) {
            if (ability.tagId() != null || ability.id() == null || ability.abilityName() == null || ability.abilityName().isBlank()) {
                continue;
            }
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey("EMP_ABILITY:" + ability.id());
            node.setNodeType("ABILITY_FACT");
            node.setRefId(ability.id());
            node.setLabel(ability.abilityName().trim());
            node.setCategory("EMPLOYEE_ABILITY");
            node.setStatus("ACTIVE");
            node.setWeightValue(ability.sourceWeight() == null ? BigDecimal.ONE : ability.sourceWeight());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("empId", ability.empId());
            meta.put("abilityName", ability.abilityName().trim());
            meta.put("masteryLevel", ability.masteryLevel());
            meta.put("evaluationSource", ability.evaluationSource());
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_ABILITY, nodes.size());
    }

    private void buildEmployeeNodes(Map<String, Integer> counter) {
        int total = 0;
        int page = 1;
        List<TalentQueryPort.EmployeeDTO> batch;
        do {
            batch = talentQueryPort.listEmployeesPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            List<KgGraphNode> nodes = new ArrayList<>(batch.size());
            for (var emp : batch) {
                KgGraphNode node = new KgGraphNode();
                node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_EMPLOYEE, emp.id()));
                node.setNodeType(NODE_TYPE_EMPLOYEE);
                node.setRefId(emp.id());
                node.setLabel(emp.realName());
                node.setCategory(emp.level());
                node.setStatus("ACTIVE");
                node.setWeightValue(BigDecimal.ONE);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("employeeNo", emp.employeeNo());
                meta.put("departmentId", emp.departmentId());
                meta.put("currentPostId", emp.currentPostId());
                node.setMetadataJson(snapshotWriter.toJson(meta));
                nodes.add(node);
            }
            snapshotWriter.batchInsertNodes(nodes);
            total += nodes.size();
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
        counter.put(NODE_TYPE_EMPLOYEE, total);
    }

    private void buildEvidenceNodes(Map<String, Integer> counter) {
        try {
            int total = 0;
            int page = 1;
            List<ContestQueryPort.ContestEvidenceDTO> batch;
            do {
                batch = contestQueryPort.listEvidencePaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
                List<KgGraphNode> nodes = new ArrayList<>(batch.size());
                for (var item : batch) {
                    KgGraphNode node = new KgGraphNode();
                    node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_EVIDENCE, item.id()));
                    node.setNodeType(NODE_TYPE_EVIDENCE);
                    node.setRefId(item.id());
                    node.setLabel(item.sourceTitle());
                    node.setCategory(item.sourceType());
                    node.setStatus("ACTIVE");
                    node.setWeightValue(item.credibilityScore());
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("targetType", item.targetType());
                    meta.put("targetRefId", item.targetRefId());
                    meta.put("abilityName", item.abilityName());
                    meta.put("evidenceStatus", item.evidenceStatus());
                    node.setMetadataJson(snapshotWriter.toJson(meta));
                    nodes.add(node);
                }
                snapshotWriter.batchInsertNodes(nodes);
                total += nodes.size();
            } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
            counter.put(NODE_TYPE_EVIDENCE, total);
        } catch (Exception e) {
            log.error("构建证据节点失败", e);
            counter.put(NODE_TYPE_EVIDENCE, 0);
        }
    }

    private void buildRagDocumentNodes(Map<String, Integer> counter) {
        List<RagKnowledgeDocument> docs = knowledgeDocumentService.listAllActiveDocuments(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        for (RagKnowledgeDocument doc : docs) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_RAG_DOCUMENT, doc.getId()));
            node.setNodeType(NODE_TYPE_RAG_DOCUMENT);
            node.setRefId(doc.getId());
            node.setLabel(doc.getTitle());
            node.setCategory(doc.getSourceType());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sourceRefId", doc.getSourceRefId());
            meta.put("chunkCount", doc.getChunkCount());
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_RAG_DOCUMENT, nodes.size());
    }

    private void buildLearningResourceNodes(Map<String, Integer> counter) {
        var resources = learningQueryPort.listActiveResources(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        for (var r : resources) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_RESOURCE, r.id()));
            node.setNodeType(NODE_TYPE_LEARNING_RESOURCE);
            node.setRefId(r.id());
            node.setLabel(r.title());
            node.setCategory(r.resourceType());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("tagId", r.tagId());
            meta.put("abilityName", r.abilityName());
            meta.put("platform", r.platform());
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_LEARNING_RESOURCE, nodes.size());
    }

    private void buildEvolutionEventNodes(Map<String, Integer> counter) {
        List<EvolutionQueryPort.EvolutionTaskDTO> tasks = evolutionQueryPort.listAllTasks(0);
        List<KgGraphNode> nodes = new ArrayList<>();
        for (var t : tasks) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_EVOLUTION_EVENT, t.id()));
            node.setNodeType(NODE_TYPE_EVOLUTION_EVENT);
            node.setRefId(t.id());
            node.setLabel(t.taskName());
            node.setCategory("EVOLUTION");
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_EVOLUTION_EVENT, nodes.size());
    }

    private void buildLearningPlanNodes(Map<String, Integer> counter) {
        int total = 0;
        int page = 1;
        List<LearningQueryPort.LearningPathPlanDTO> batch;
        do {
            batch = learningQueryPort.listPlansPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            List<KgGraphNode> nodes = new ArrayList<>(batch.size());
            for (var plan : batch) {
                KgGraphNode node = new KgGraphNode();
                node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_PLAN, plan.id()));
                node.setNodeType(NODE_TYPE_LEARNING_PLAN);
                node.setRefId(plan.id());
                node.setLabel(plan.planTitle());
                node.setCategory(plan.planStatus());
                node.setStatus("ACTIVE");
                node.setWeightValue(plan.currentScore());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("empId", plan.empId());
                meta.put("postId", plan.postId());
                meta.put("matchingRecordId", plan.matchingRecordId());
                meta.put("targetScore", plan.targetScore());
                node.setMetadataJson(snapshotWriter.toJson(meta));
                nodes.add(node);
            }
            snapshotWriter.batchInsertNodes(nodes);
            total += nodes.size();
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
        counter.put(NODE_TYPE_LEARNING_PLAN, total);
    }

    private void buildLearningStepNodes(Map<String, Integer> counter) {
        int total = 0;
        int page = 1;
        List<LearningQueryPort.LearningPathStepDTO> batch;
        do {
            batch = learningQueryPort.listStepsPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            List<KgGraphNode> nodes = new ArrayList<>(batch.size());
            for (var s : batch) {
                KgGraphNode node = new KgGraphNode();
                node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_LEARNING_STEP, s.id()));
                node.setNodeType(NODE_TYPE_LEARNING_STEP);
                node.setRefId(s.id());
                node.setLabel(s.stepTitle());
                node.setCategory(s.status());
                node.setStatus("ACTIVE");
                node.setWeightValue(BigDecimal.ONE);
                nodes.add(node);
            }
            snapshotWriter.batchInsertNodes(nodes);
            total += nodes.size();
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
        counter.put(NODE_TYPE_LEARNING_STEP, total);
    }

    private void buildProjectTaskNodes(Map<String, Integer> counter) {
        int total = 0;
        int page = 1;
        List<LearningQueryPort.LearningProjectTaskDTO> batch;
        do {
            batch = learningQueryPort.listProjectTasksPaginated(page++, GRAPH_SOURCE_PAGE_SIZE);
            List<KgGraphNode> nodes = new ArrayList<>(batch.size());
            for (var t : batch) {
                KgGraphNode node = new KgGraphNode();
                node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_PROJECT_TASK, t.id()));
                node.setNodeType(NODE_TYPE_PROJECT_TASK);
                node.setRefId(t.id());
                node.setLabel(t.taskTitle());
                node.setCategory(t.status());
                node.setStatus("ACTIVE");
                node.setWeightValue(BigDecimal.ONE);
                nodes.add(node);
            }
            snapshotWriter.batchInsertNodes(nodes);
            total += nodes.size();
        } while (batch.size() >= GRAPH_SOURCE_PAGE_SIZE);
        counter.put(NODE_TYPE_PROJECT_TASK, total);
    }

    private void buildKnowledgeDomainNodes(Map<String, Integer> counter) {
        List<KnowledgeDomain> domains = knowledgeDomainMapper.selectList(
                Wrappers.<KnowledgeDomain>lambdaQuery()
                        .eq(KnowledgeDomain::getIsDeleted, 0)
                        .eq(KnowledgeDomain::getStatus, "ACTIVE"));
        List<KgGraphNode> nodes = new ArrayList<>();
        for (KnowledgeDomain d : domains) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_DOMAIN, d.getId()));
            node.setNodeType(NODE_TYPE_KNOWLEDGE_DOMAIN);
            node.setRefId(d.getId());
            node.setLabel(d.getDomainName());
            node.setCategory(d.getDomainCode());
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_KNOWLEDGE_DOMAIN, nodes.size());
    }

    private void buildKnowledgeNodeNodes(Map<String, Integer> counter) {
        List<KnowledgeNode> knowledgeNodes = knowledgeNodeMapper.selectList(
                Wrappers.<KnowledgeNode>lambdaQuery()
                        .eq(KnowledgeNode::getIsDeleted, 0)
                        .eq(KnowledgeNode::getStatus, "ACTIVE"));
        List<KgGraphNode> nodes = new ArrayList<>();
        for (KnowledgeNode n : knowledgeNodes) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey(snapshotWriter.generateNodeKey(NODE_TYPE_KNOWLEDGE_NODE, n.getId()));
            node.setNodeType(NODE_TYPE_KNOWLEDGE_NODE);
            node.setRefId(n.getId());
            node.setLabel(n.getNodeName());
            node.setCategory(String.valueOf(n.getNodeLevel()));
            node.setStatus("ACTIVE");
            node.setWeightValue(BigDecimal.ONE);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("domainId", n.getDomainId());
            meta.put("parentId", n.getParentId());
            meta.put("nodeDescription", n.getNodeDescription());
            meta.put("prerequisiteIds", knowledgeNodeDependencyResolver.parsePrerequisiteIds(n));
            node.setMetadataJson(snapshotWriter.toJson(meta));
            nodes.add(node);
        }
        snapshotWriter.batchInsertNodes(nodes);
        counter.put(NODE_TYPE_KNOWLEDGE_NODE, nodes.size());
    }
}
