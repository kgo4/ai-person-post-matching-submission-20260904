package com.example.matching.service.kg.build;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link GraphEdgeProjectionService} 的单元测试。
 *
 * <p>它本身没有业务逻辑，价值在于**它是"图谱到底投影了哪些边"的唯一清单**：
 * 少调一个投影方法，对应的边就会在图里整类消失，而且不会有任何报错
 * （构建照样成功，只是图不完整）。所以这里锁的是**清单的完整性**，
 * 而不是某个投影内部的算法。
 */
class GraphEdgeProjectionServiceTest {

    private final PostQueryPort postQueryPort = mock(PostQueryPort.class);
    private final TalentQueryPort talentQueryPort = mock(TalentQueryPort.class);
    private final LearningQueryPort learningQueryPort = mock(LearningQueryPort.class);
    private final MatchingQueryPort matchingQueryPort = mock(MatchingQueryPort.class);
    private final KnowledgeDocumentService knowledgeDocumentService = mock(KnowledgeDocumentService.class);
    private final ContestQueryPort contestQueryPort = mock(ContestQueryPort.class);
    private final EvolutionQueryPort evolutionQueryPort = mock(EvolutionQueryPort.class);
    private final KnowledgeNodeMapper knowledgeNodeMapper = mock(KnowledgeNodeMapper.class);
    private final AbilityTagDomainRelMapper abilityTagDomainRelMapper = mock(AbilityTagDomainRelMapper.class);
    private final KgRelationCandidateMapper relationCandidateMapper = mock(KgRelationCandidateMapper.class);
    private final KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver =
            mock(KnowledgeNodeDependencyResolver.class);
    private final GraphSnapshotWriter snapshotWriter = mock(GraphSnapshotWriter.class);
    private final GraphBusinessEdgeProjector businessProjector = mock(GraphBusinessEdgeProjector.class);
    private final GraphKnowledgeEdgeProjector knowledgeProjector = mock(GraphKnowledgeEdgeProjector.class);

    private final GraphEdgeProjectionService service = new GraphEdgeProjectionService(
            postQueryPort, talentQueryPort, learningQueryPort, matchingQueryPort, knowledgeDocumentService,
            contestQueryPort, evolutionQueryPort, knowledgeNodeMapper, abilityTagDomainRelMapper,
            relationCandidateMapper, knowledgeNodeDependencyResolver, snapshotWriter,
            businessProjector, knowledgeProjector);

    private static final GraphBuildContext CTX = new GraphBuildContext("KGV_TEST", "2026-09-04T00:00:00");

    @Test
    @DisplayName("业务侧 9 类边全部要投影，且都拿到同一个 counter 与同一个构建上下文")
    void invokesEveryBusinessProjection() {
        service.projectEdges(CTX);

        verify(businessProjector).buildPostRequiresAbilityEdges(any(), same(CTX));
        verify(businessProjector).buildEmployeeHasAbilityEdges(any(), same(CTX));
        verify(businessProjector).buildPostFamilyRequiresAbilityEdges(any(), same(CTX));
        verify(businessProjector).buildEvidenceSupportedByEdges(any(), same(CTX));
        verify(businessProjector).buildRagDocumentDerivedFromEdges(any(), same(CTX));
        verify(businessProjector).buildLearningResourceRecommendsEdges(any(), same(CTX));
        verify(businessProjector).buildEvolutionEventEdges(any(), same(CTX));
        verify(businessProjector).buildMatchedWithEdges(any(), same(CTX));
        // 这一类不需要上下文（只依赖已落库的匹配评估结果）
        verify(businessProjector).buildEvaluatedByEdges(any());
    }

    @Test
    @DisplayName("知识侧 8 类边全部要投影（少一类 = 图里整类边消失且不报错）")
    void invokesEveryKnowledgeProjection() {
        service.projectEdges(CTX);

        verify(knowledgeProjector).buildLearningPlanEdges(any(), same(CTX));
        verify(knowledgeProjector).buildLearningStepEdges(any());
        verify(knowledgeProjector).buildProjectTaskEdges(any(), same(CTX));
        verify(knowledgeProjector).buildAbilityBelongsToDomainEdges(any(), same(CTX));
        verify(knowledgeProjector).buildDomainHasKnowledgeNodeEdges(any(), same(CTX));
        verify(knowledgeProjector).buildKnowledgeNodeParentOfEdges(any(), same(CTX));
        verify(knowledgeProjector).buildKnowledgeNodePrerequisiteEdges(any(), same(CTX));
        verify(knowledgeProjector).buildApprovedRelatedToEdges(any(), same(CTX));
    }

    @Test
    @DisplayName("17 类边共用一个计数器，投影累加的结果要原样返回给调用方")
    void returnsTheSameCounterFilledByBothCollaborators() {
        doAnswer(invocation -> {
            Map<String, Integer> counter = invocation.getArgument(0);
            counter.merge("REQUIRES", 3, Integer::sum);
            return null;
        }).when(businessProjector).buildPostRequiresAbilityEdges(any(), any());

        doAnswer(invocation -> {
            Map<String, Integer> counter = invocation.getArgument(0);
            counter.merge("REQUIRES", 2, Integer::sum);
            counter.merge("HAS_ABILITY", 5, Integer::sum);
            return null;
        }).when(businessProjector).buildEmployeeHasAbilityEdges(any(), any());

        doAnswer(invocation -> {
            Map<String, Integer> counter = invocation.getArgument(0);
            counter.merge("PARENT_OF", 4, Integer::sum);
            return null;
        }).when(knowledgeProjector).buildKnowledgeNodeParentOfEdges(any(), any());

        Map<String, Integer> counter = service.projectEdges(CTX);

        assertEquals(5, counter.get("REQUIRES"), "同一类边要累加而不是覆盖");
        assertEquals(5, counter.get("HAS_ABILITY"));
        assertEquals(4, counter.get("PARENT_OF"));
        assertEquals(3, counter.size());
    }

    @Test
    @DisplayName("没有任何边可投影时返回空表（而不是 null），且每次调用都是新计数器")
    void returnsEmptyMapWhenNothingProjected() {
        Map<String, Integer> first = service.projectEdges(CTX);
        Map<String, Integer> second = service.projectEdges(CTX);

        assertTrue(first.isEmpty(), "没有边时要返回空表而不是 null");
        assertNotSame(first, second, "计数器每次都要新建，不能复用上一次的残留计数");
    }
}
