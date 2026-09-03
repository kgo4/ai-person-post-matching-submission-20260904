package com.example.matching.service.matching;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.enums.MatchTypeEnum;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * MatchingAlgorithmService 四级语义匹配逻辑测试
 * <p>
 * 覆盖：EXACT -> CANONICAL -> CONFIRMED_SIMILAR -> SEMANTIC_FALLBACK
 * 以及一对一约束。
 */
@ExtendWith(MockitoExtension.class)
class MatchingAlgorithmServiceSemanticMatchingTest {

    @Mock
    private TagCanonicalResolver tagCanonicalResolver;
    @Mock
    private VectorEmbeddingService vectorEmbeddingService;
    @Mock
    private TagQueryPort tagQueryPort;
    @Mock
    private ObjectMapper objectMapper;

    private MatchingAlgorithmService service;

    @BeforeEach
    void setUp() {
        service = new MatchingAlgorithmServiceImpl(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, objectMapper);
        lenient().when(tagCanonicalResolver.batchFindConfirmedSimilarRelations(anyLong(), anyCollection()))
                .thenReturn(Collections.emptyMap());
        lenient().when(tagCanonicalResolver.batchFindConfirmedSimilarRelationsForSources(anyCollection(), anyCollection()))
                .thenReturn(Collections.emptyMap());
    }

    // ===== 精确匹配 =====

    @Test
    void performSemanticMatching_exactMatch_returnsExactType() {
        // 员工拥有 tagId=10，岗位要求 tagId=10
        Map<Long, BigDecimal> fusedLevels = Map.of(10L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(10L, 3);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> canonicalMap = Map.of(10L, 10L);
        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection())).thenReturn(canonicalMap);
        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.EXACT);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(10L);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("1.00");
        assertThat(details.get(0).isPassed()).isTrue();
    }

    // ===== 规范化匹配 =====

    @Test
    void performSemanticMatching_canonicalMatch_whenSameCanonicalDifferentTagId() {
        // 员工拥有 tagId=11（规范化=10），岗位要求 tagId=12（规范化=10）
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 3);
        MatchingRequirementSnapshot req = createRequirement(12L, 2, false, false);

        // 两个标签的规范化 ID 均为 10
        Map<Long, Long> postCanonicalMap = Map.of(12L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 10L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.CANONICAL);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(11L);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("1.00");
        assertThat(details.get(0).isPassed()).isTrue();
    }

    // ===== 已确认相似匹配 =====

    @Test
    void performSemanticMatching_confirmedSimilarMatch_whenRelationExists() {
        // 员工拥有 tagId=11，岗位要求 tagId=10，存在已确认的相似关系
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 3);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);

        // 模拟已确认的相似关系
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSourceTagId(10L);
        relation.setTargetTagId(11L);
        relation.setSimilarityScore(new BigDecimal("0.90"));
        when(tagCanonicalResolver.batchFindConfirmedSimilarRelationsForSources(Set.of(10L), Set.of(11L)))
                .thenReturn(Map.of(10L, Map.of(11L, relation)));
        when(tagCanonicalResolver.getSimilarCoefficient(relation)).thenReturn(new BigDecimal("0.90"));

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.CONFIRMED_SIMILAR);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(11L);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("0.90");
    }

    // ===== 语义降级匹配 =====

    @Test
    void performSemanticMatching_semanticFallback_whenHighSimilarity() {
        // 员工拥有 tagId=11，岗位要求 tagId=10，无精确/规范化/已确认相似匹配
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 3);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        // 模拟向量
        AbilityTag postTag = new AbilityTag();
        postTag.setId(10L);
        postTag.setEmbeddingVector(List.of(1.0f, 0.0f, 0.0f));

        AbilityTag empTag = new AbilityTag();
        empTag.setId(11L);
        empTag.setEmbeddingVector(List.of(0.95f, 0.1f, 0.0f));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(toTagDtos(postTag, empTag));

        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f, 0.0f),
                List.of(0.95f, 0.1f, 0.0f)))
                .thenReturn(0.92f);

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(11L);
        assertThat(details.get(0).getSimilarityScore()).isEqualByComparingTo("0.92");
    }

    // ===== 无匹配（低于阈值） =====

    @Test
    void performSemanticMatching_noMatch_whenBelowThreshold() {
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 3);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        // 模拟向量 with low similarity
        AbilityTag postTag = new AbilityTag();
        postTag.setId(10L);
        postTag.setEmbeddingVector(List.of(1.0f, 0.0f, 0.0f));

        AbilityTag empTag = new AbilityTag();
        empTag.setId(11L);
        empTag.setEmbeddingVector(List.of(0.0f, 1.0f, 0.0f));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(toTagDtos(postTag, empTag));

        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f, 0.0f),
                List.of(0.0f, 1.0f, 0.0f)))
                .thenReturn(0.10f);

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.NONE);
        assertThat(details.get(0).getMatchedEmpTagId()).isNull();
    }

    // ===== 能力语义匹配但员工等级不足 =====

    @Test
    void performSemanticMatching_requiredNotPassed_whenSemanticMatchButLevelTooLow() {
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("1.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 1);
        MatchingRequirementSnapshot req = createRequirement(10L, 4, true, false); // required, level 4

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        // 高相似度向量匹配
        AbilityTag postTag = new AbilityTag();
        postTag.setId(10L);
        postTag.setEmbeddingVector(List.of(1.0f, 0.0f));

        AbilityTag empTag = new AbilityTag();
        empTag.setId(11L);
        empTag.setEmbeddingVector(List.of(0.98f, 0.1f));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(toTagDtos(postTag, empTag));

        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f),
                List.of(0.98f, 0.1f)))
                .thenReturn(0.95f);

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.SEMANTIC_FALLBACK);
        // 员工等级 1 < 要求等级 4 => 不通过
        assertThat(details.get(0).isPassed()).isFalse();
    }

    // ===== 一对一约束 =====

    @Test
    void performSemanticMatching_oneToOneConstraint_semanticFallbackOnlyMatchesOnce() {
        // 2个员工标签，2个岗位标签，标签ID均不同
        // 语义降级应只匹配一对（相似度最高的一对）
        Map<Long, BigDecimal> fusedLevels = new HashMap<>();
        fusedLevels.put(11L, new BigDecimal("3.0"));
        fusedLevels.put(12L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot emp1 = createEmpAbility(11L, 3);
        MatchingAbilitySnapshot emp2 = createEmpAbility(12L, 3);
        MatchingRequirementSnapshot req1 = createRequirement(10L, 2, false, false);
        MatchingRequirementSnapshot req2 = createRequirement(20L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L, 20L, 20L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L, 12L, 12L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        // 模拟4个标签的向量
        AbilityTag postTag10 = new AbilityTag();
        postTag10.setId(10L);
        postTag10.setEmbeddingVector(List.of(1.0f, 0.0f));

        AbilityTag postTag20 = new AbilityTag();
        postTag20.setId(20L);
        postTag20.setEmbeddingVector(List.of(0.0f, 1.0f));

        AbilityTag empTag11 = new AbilityTag();
        empTag11.setId(11L);
        empTag11.setEmbeddingVector(List.of(0.9f, 0.1f));

        AbilityTag empTag12 = new AbilityTag();
        empTag12.setId(12L);
        empTag12.setEmbeddingVector(List.of(0.1f, 0.9f));
        when(tagQueryPort.batchGetTags(anyList()))
                .thenReturn(toTagDtos(postTag10, postTag20, empTag11, empTag12));

        // 相似度矩阵：
        // req10 vs emp11 = 0.9（高）
        // req10 vs emp12 = 0.1（低）
        // req20 vs emp11 = 0.1（低）
        // req20 vs emp12 = 0.9（高）
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f), List.of(0.9f, 0.1f))).thenReturn(0.9f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(0.0f, 1.0f), List.of(0.1f, 0.9f))).thenReturn(0.9f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f), List.of(0.1f, 0.9f))).thenReturn(0.1f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(0.0f, 1.0f), List.of(0.9f, 0.1f))).thenReturn(0.1f);

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(emp1, emp2), List.of(req1, req2));

        assertThat(details).hasSize(2);

        // 两者都应为语义降级匹配
        long semanticCount = details.stream()
                .filter(d -> d.getMatchType() == MatchTypeEnum.SEMANTIC_FALLBACK)
                .count();
        assertThat(semanticCount).isEqualTo(2);

        // req10 匹配 emp11，req20 匹配 emp12（贪心策略：全局最高优先）
        MatchDetailDTO detail0 = details.get(0);
        MatchDetailDTO detail1 = details.get(1);
        assertThat(detail0.getMatchedEmpTagId()).isNotNull();
        assertThat(detail1.getMatchedEmpTagId()).isNotNull();
        // 应匹配不同的员工标签（一对一约束）
        assertThat(detail0.getMatchedEmpTagId()).isNotEqualTo(detail1.getMatchedEmpTagId());
    }

    // ===== 精确匹配优先于规范化匹配 =====

    @Test
    void performSemanticMatching_exactTakesPrecedence_overCanonical() {
        // 岗位要求 tagId=10，员工同时拥有 tagId=10 和 tagId=11（同一规范化 ID）
        Map<Long, BigDecimal> fusedLevels = new HashMap<>();
        fusedLevels.put(10L, new BigDecimal("3.0"));
        fusedLevels.put(11L, new BigDecimal("4.0"));

        MatchingAbilitySnapshot emp1 = createEmpAbility(10L, 3);
        MatchingAbilitySnapshot emp2 = createEmpAbility(11L, 4);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 100L);
        Map<Long, Long> empCanonicalMap = Map.of(10L, 100L, 11L, 100L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(emp1, emp2), List.of(req));

        assertThat(details).hasSize(1);
        // 应为精确匹配（tagId=10 匹配 tagId=10），而非规范化匹配
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.EXACT);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(10L);
        // tagId=10 的员工等级为 3（不是 tagId=11 的 4）
        assertThat(details.get(0).getEmployeeRawLevel()).isEqualByComparingTo("3.00");
    }

    // ===== 优先级：已确认相似匹配优先于语义降级匹配 =====

    @Test
    void performSemanticMatching_confirmedSimilarTakesPrecedence_overSemanticFallback() {
        Map<Long, BigDecimal> fusedLevels = Map.of(11L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot empAbility = createEmpAbility(11L, 3);
        MatchingRequirementSnapshot req = createRequirement(10L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);

        // 存在已确认的相似关系
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSourceTagId(10L);
        relation.setTargetTagId(11L);
        relation.setSimilarityScore(new BigDecimal("0.88"));
        when(tagCanonicalResolver.batchFindConfirmedSimilarRelationsForSources(Set.of(10L), Set.of(11L)))
                .thenReturn(Map.of(10L, Map.of(11L, relation)));
        when(tagCanonicalResolver.getSimilarCoefficient(relation)).thenReturn(new BigDecimal("0.88"));

        // 注意：不应调用向量搜索，因为已优先找到已确认的相似关系

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(empAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.CONFIRMED_SIMILAR);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("0.88");
    }

    @Test
    void performSemanticMatching_confirmedSimilarChoosesHighestCoefficient() {
        Map<Long, BigDecimal> fusedLevels = new HashMap<>();
        fusedLevels.put(11L, new BigDecimal("5.0"));
        fusedLevels.put(12L, new BigDecimal("5.0"));

        MatchingAbilitySnapshot lowSimilarityAbility = createEmpAbility(11L, 5);
        MatchingAbilitySnapshot highSimilarityAbility = createEmpAbility(12L, 5);
        MatchingRequirementSnapshot req = createRequirement(10L, 3, true, false);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(Map.of(10L, 10L))
                .thenReturn(Map.of(11L, 11L, 12L, 12L));

        AbilityTagRelation lowRelation = new AbilityTagRelation();
        lowRelation.setSourceTagId(10L);
        lowRelation.setTargetTagId(11L);
        lowRelation.setSimilarityScore(new BigDecimal("0.91"));
        AbilityTagRelation highRelation = new AbilityTagRelation();
        highRelation.setSourceTagId(10L);
        highRelation.setTargetTagId(12L);
        highRelation.setSimilarityScore(new BigDecimal("0.99"));

        Map<Long, AbilityTagRelation> relations = new LinkedHashMap<>();
        relations.put(11L, lowRelation);
        relations.put(12L, highRelation);
        when(tagCanonicalResolver.batchFindConfirmedSimilarRelationsForSources(Set.of(10L), Set.of(11L, 12L)))
                .thenReturn(Map.of(10L, relations));
        when(tagCanonicalResolver.getSimilarCoefficient(lowRelation)).thenReturn(new BigDecimal("0.91"));
        when(tagCanonicalResolver.getSimilarCoefficient(highRelation)).thenReturn(new BigDecimal("0.99"));

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(lowSimilarityAbility, highSimilarityAbility), List.of(req));

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getMatchType()).isEqualTo(MatchTypeEnum.CONFIRMED_SIMILAR);
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(12L);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("0.99");
    }

    // ===== M-05：Hungarian 全局最优分配 =====

    @Test
    void performSemanticMatching_hungarianFindsGlobalOptimum_whereGreedyFails() {
        // 贪心会先取全局最高 req10->emp11=0.95，导致 req20 只剩 0.10 无法匹配；
        // Hungarian 全局最优：req10->emp12(0.90) + req20->emp11(0.90)，两个要求都被匹配。
        Map<Long, BigDecimal> fusedLevels = new HashMap<>();
        fusedLevels.put(11L, new BigDecimal("3.0"));
        fusedLevels.put(12L, new BigDecimal("3.0"));

        MatchingAbilitySnapshot emp1 = createEmpAbility(11L, 3);
        MatchingAbilitySnapshot emp2 = createEmpAbility(12L, 3);
        MatchingRequirementSnapshot req1 = createRequirement(10L, 2, true, false);
        MatchingRequirementSnapshot req2 = createRequirement(20L, 2, false, false);

        Map<Long, Long> postCanonicalMap = Map.of(10L, 10L, 20L, 20L);
        Map<Long, Long> empCanonicalMap = Map.of(11L, 11L, 12L, 12L);

        when(tagCanonicalResolver.batchGetCanonicalTagIds(anyCollection()))
                .thenReturn(postCanonicalMap)
                .thenReturn(empCanonicalMap);
        AbilityTag reqTag10 = new AbilityTag();
        reqTag10.setId(10L);
        reqTag10.setEmbeddingVector(List.of(1.0f, 0.0f));
        AbilityTag reqTag20 = new AbilityTag();
        reqTag20.setId(20L);
        reqTag20.setEmbeddingVector(List.of(0.9f, 0.9f));
        AbilityTag empTag11 = new AbilityTag();
        empTag11.setId(11L);
        empTag11.setEmbeddingVector(List.of(0.95f, 0.3f));
        AbilityTag empTag12 = new AbilityTag();
        empTag12.setId(12L);
        empTag12.setEmbeddingVector(List.of(0.3f, 0.95f));
        when(tagQueryPort.batchGetTags(anyList()))
                .thenReturn(toTagDtos(reqTag10, reqTag20, empTag11, empTag12));

        // 相似度矩阵（直接桩定 cosine 结果，绕开真实向量计算）：
        // req10 vs emp11 = 0.95（贪心首选，会破坏全局最优）
        // req10 vs emp12 = 0.91
        // req20 vs emp11 = 0.91
        // req20 vs emp12 = 0.10（低于可选阈值 0.85，不可匹配）
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f), List.of(0.95f, 0.3f))).thenReturn(0.95f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(1.0f, 0.0f), List.of(0.3f, 0.95f))).thenReturn(0.91f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(0.9f, 0.9f), List.of(0.95f, 0.3f))).thenReturn(0.91f);
        when(vectorEmbeddingService.cosineSimilarity(
                List.of(0.9f, 0.9f), List.of(0.3f, 0.95f))).thenReturn(0.10f);

        List<MatchDetailDTO> details = service.performSemanticMatching(
                fusedLevels, List.of(emp1, emp2), List.of(req1, req2));

        assertThat(details).hasSize(2);
        assertThat(details).allMatch(d -> d.getMatchType() == MatchTypeEnum.SEMANTIC_FALLBACK);
        // 全局最优分配：req10 -> emp12，req20 -> emp11（贪心只能匹配 req10 -> emp11）
        assertThat(details.get(0).getMatchedEmpTagId()).isEqualTo(12L);
        assertThat(details.get(1).getMatchedEmpTagId()).isEqualTo(11L);
        assertThat(details.get(0).getMatchCoefficient()).isEqualByComparingTo("0.91");
        assertThat(details.get(1).getMatchCoefficient()).isEqualByComparingTo("0.91");
    }

    // ===== 辅助方法 =====

    private MatchingAbilitySnapshot createEmpAbility(Long tagId, int level) {
        return new MatchingAbilitySnapshot(null, tagId, null, level, null, "MANUAL", null, null);
    }

    private MatchingRequirementSnapshot createRequirement(Long tagId, int minLevel, boolean isRequired, boolean isCore) {
        return new MatchingRequirementSnapshot(tagId, null, minLevel, new BigDecimal("100"),
                isRequired ? 1 : 0, isCore ? 1 : 0, null);
    }

    private static List<TagQueryPort.TagDTO> toTagDtos(AbilityTag... tags) {
        return java.util.Arrays.stream(tags)
                .map(t -> new TagQueryPort.TagDTO(t.getId(), t.getTagName(), t.getTagCode(), t.getTagCategory(),
                        t.getDomain(), t.getTagLevel(), t.getParentId(), t.getCanonicalTagId(), t.getSourceType(),
                        t.getDescription(), t.getEmbeddingVector(), t.getCreatedTime()))
                .toList();
    }
}
