package com.example.matching.service.kg.build;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.entity.kg.AbilityTagDomainRel;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgRelationCandidate;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.kg.AbilityTagDomainRelMapper;
import com.example.matching.mapper.kg.KgRelationCandidateMapper;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.post.PostQueryPort;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphEdgeProjectionService {

    private static final int GRAPH_SOURCE_PAGE_SIZE = 500;

    private static final String NODE_TYPE_POST = "POST";
    private static final String NODE_TYPE_POST_FAMILY = "POST_FAMILY";
    private static final String NODE_TYPE_ABILITY = "ABILITY";
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

    private static final String EDGE_TYPE_REQUIRES = "REQUIRES";
    private static final String EDGE_TYPE_HAS_ABILITY = "HAS_ABILITY";
    private static final String EDGE_TYPE_SUPPORTED_BY = "SUPPORTED_BY";
    private static final String EDGE_TYPE_DERIVED_FROM = "DERIVED_FROM";
    private static final String EDGE_TYPE_RECOMMENDS = "RECOMMENDS";
    private static final String EDGE_TYPE_EVOLVED_TO = "EVOLVED_TO";
    private static final String EDGE_TYPE_MATCHED_WITH = "MATCHED_WITH";
    private static final String EDGE_TYPE_EVALUATED_BY = "EVALUATED_BY";
    private static final String EDGE_TYPE_HAS_STEP = "HAS_STEP";
    private static final String EDGE_TYPE_TRAINS_ABILITY = "TRAINS_ABILITY";
    private static final String EDGE_TYPE_HAS_PROJECT_TASK = "HAS_PROJECT_TASK";
    private static final String EDGE_TYPE_HAS_LEARNING_PLAN = "HAS_LEARNING_PLAN";
    private static final String EDGE_TYPE_BELONGS_TO_DOMAIN = "BELONGS_TO_DOMAIN";
    private static final String EDGE_TYPE_HAS_KNOWLEDGE_NODE = "HAS_KNOWLEDGE_NODE";
    private static final String EDGE_TYPE_PARENT_OF = "PARENT_OF";
    private static final String EDGE_TYPE_PREREQUISITE_OF = "PREREQUISITE_OF";
    private static final String EDGE_TYPE_RELATED_TO = "RELATED_TO";

    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final LearningQueryPort learningQueryPort;
    private final MatchingQueryPort matchingQueryPort;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final ContestQueryPort contestQueryPort;
    private final EvolutionQueryPort evolutionQueryPort;

    private final KnowledgeNodeMapper knowledgeNodeMapper;
    private final AbilityTagDomainRelMapper abilityTagDomainRelMapper;
    private final KgRelationCandidateMapper relationCandidateMapper;
    private final KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver;

    private final GraphSnapshotWriter snapshotWriter;
    private final GraphBusinessEdgeProjector businessProjector;
    private final GraphKnowledgeEdgeProjector knowledgeProjector;

    public Map<String, Integer> projectEdges(GraphBuildContext ctx) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        businessProjector.buildPostRequiresAbilityEdges(counter, ctx);
        businessProjector.buildEmployeeHasAbilityEdges(counter, ctx);
        businessProjector.buildPostFamilyRequiresAbilityEdges(counter, ctx);
        businessProjector.buildEvidenceSupportedByEdges(counter, ctx);
        businessProjector.buildRagDocumentDerivedFromEdges(counter, ctx);
        businessProjector.buildLearningResourceRecommendsEdges(counter, ctx);
        businessProjector.buildEvolutionEventEdges(counter, ctx);
        businessProjector.buildMatchedWithEdges(counter, ctx);
        businessProjector.buildEvaluatedByEdges(counter);
        knowledgeProjector.buildLearningPlanEdges(counter, ctx);
        knowledgeProjector.buildLearningStepEdges(counter);
        knowledgeProjector.buildProjectTaskEdges(counter, ctx);
        knowledgeProjector.buildAbilityBelongsToDomainEdges(counter, ctx);
        knowledgeProjector.buildDomainHasKnowledgeNodeEdges(counter, ctx);
        knowledgeProjector.buildKnowledgeNodeParentOfEdges(counter, ctx);
        knowledgeProjector.buildKnowledgeNodePrerequisiteEdges(counter, ctx);
        knowledgeProjector.buildApprovedRelatedToEdges(counter, ctx);
        return counter;
    }
}