package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.agent.dto.PostTrendAiResult;
import com.example.matching.agent.lc4j.PostTrendAiService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.PostTrendProperties;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostSimilarityService;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.service.post.support.TrendAbilityDiffService;
import com.example.matching.service.post.support.TrendAbilityResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位趋势解析单测。
 * <p>
 * 核心回归点：
 * <ol>
 *   <li><b>「检索不可用」不能被当成「没有相似岗位」</b> —— 否则与既有岗位高度重合的岗位会被
 *       重复创建成新岗位；</li>
 *   <li><b>无候选时必须给出可执行原因</b> —— 使用者拿到空结果不能只能反复重传材料；</li>
 *   <li><b>绝不产出删除建议</b> —— 材料没提到某项能力不等于岗位不再需要它。</li>
 * </ol>
 */
@DisplayName("岗位趋势解析（S3）")
class PostTrendAnalysisServiceImplTest {

    private static final Long TASK_ID = 500L;

    private final PostTrendTaskService taskService = mock(PostTrendTaskService.class);
    private final KnowledgeSourceDocumentMapper sourceDocumentMapper = mock(KnowledgeSourceDocumentMapper.class);
    private final RagKnowledgeChunkMapper chunkMapper = mock(RagKnowledgeChunkMapper.class);
    private final EvolutionSourceIngestionService sourceIngestionService = mock(EvolutionSourceIngestionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PostTrendAiService> aiProvider = mock(ObjectProvider.class);
    private final PostTrendAiService aiService = mock(PostTrendAiService.class);
    private final PostSimilarityService postSimilarityService = mock(PostSimilarityService.class);
    private final TrendAbilityResolver abilityResolver = mock(TrendAbilityResolver.class);
    private final AiTrustHarnessService harnessService = mock(AiTrustHarnessService.class);
    private final PostTrendCandidateMapper candidateMapper = mock(PostTrendCandidateMapper.class);
    private final PostAbilityModelService postAbilityModelService = mock(PostAbilityModelService.class);

    private final PostTrendProperties properties = new PostTrendProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 用真实差异计算组件，保证「不产出删除」「等级提升识别」等断言测的是生产逻辑而非桩 */
    private final TrendAbilityDiffService abilityDiffService = new TrendAbilityDiffService(postAbilityModelService);

    private final PostTrendAnalysisServiceImpl service = new PostTrendAnalysisServiceImpl(
            taskService, sourceDocumentMapper, chunkMapper, sourceIngestionService, aiProvider,
            postSimilarityService, abilityResolver, abilityDiffService, harnessService, candidateMapper,
            properties, objectMapper);

    /**
     * 无 Spring 上下文时 MyBatis-Plus 的 lambda 列名缓存不会自动初始化，
     * 而 {@code LambdaQueryWrapper.select(...)} 会立即解析列名并抛
     * 「can not find lambda cache」。这里按仓库既有范式手动登记实体。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PostTrendCandidate.class);
        TableInfoHelper.initTableInfo(assistant, RagKnowledgeChunk.class);
    }

    @BeforeEach
    void setUp() {
        when(aiProvider.getIfAvailable()).thenReturn(aiService);
        when(harnessService.verify(any())).thenReturn(decision(AiHarnessDecisionDTO.REVIEW, "MEDIUM", "需人工复核"));
        when(candidateMapper.selectList(any())).thenReturn(List.of());
        when(candidateMapper.insert(any(PostTrendCandidate.class))).thenReturn(1);
        stubDocument(1L, 11L, "POLICY_DOCUMENT", 2);
        stubChunks(List.of(
                chunk(101L, 11L, 0, "本方案提出重点培养生成式人工智能训练师，要求掌握大模型微调与数据标注。"),
                chunk(102L, 11L, 1, "支持生成式人工智能训练师岗位建设，需掌握提示词工程。")));
    }

    // ---------------------------------------------------------------- 前置失败

    @Test
    @DisplayName("AI 未启用时直接失败，而不是返回「0 个候选」")
    void missingAiServiceIsReportedAsFailure() {
        when(aiProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 解析能力未启用");
        verify(candidateMapper, never()).insert(any(PostTrendCandidate.class));
    }

    @Test
    @DisplayName("材料未提取到文本时给出「扫描件无文本层」这类可执行诊断")
    void emptyMaterialYieldsActionableDiagnostics() {
        stubChunks(List.of());

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.candidateCount()).isZero();
        assertThat(summary.diagnostics()).contains("扫描件");
        verify(aiProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("材料已删除 / 不存在时不静默产出空结果")
    void missingDocumentYieldsDiagnostics() {
        when(sourceDocumentMapper.selectBatchIds(any())).thenReturn(List.of());

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.candidateCount()).isZero();
        assertThat(summary.diagnostics()).contains("均不可用");
    }

    // ---------------------------------------------------------------- 强调度

    @Test
    @DisplayName("材料强调度不足的岗位不产出候选，但计入诊断")
    void lowEmphasisPostIsFilteredWithDiagnostics() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 1.0D, 0,
                ability("大模型微调", 4, 40))));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.extractedPostCount()).isEqualTo(1);
        assertThat(summary.filteredByEmphasisCount()).isEqualTo(1);
        assertThat(summary.candidateCount()).isZero();
        assertThat(summary.diagnostics()).contains("强调度不足");
        verify(candidateMapper, never()).insert(any(PostTrendCandidate.class));
    }

    @Test
    @DisplayName("LLM 把材料里没出现过的岗位报成高强调度 → 用提及次数交叉校验压回去")
    void emphasisIsCrossCheckedAgainstMaterialMentions() {
        // 材料中根本没有「量子计算工程师」，LLM 却给了 9 分
        when(aiService.analyze(anyString())).thenReturn(ai(post("量子计算工程师", 9.0D, 0,
                ability("量子算法", 4, 40))));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.filteredByEmphasisCount()).isEqualTo(1);
        assertThat(summary.candidateCount()).isZero();
        assertThat(summary.diagnostics()).contains("强调度不足");
    }

    // ---------------------------------------------------------------- 分流

    @Test
    @DisplayName("相似度低 → 新岗位候选，且治理判定按「新兴岗位」声明（永不自动放行）")
    void lowSimilarityProducesNewPostCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40), ability("数据标注", 3, 20))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.31D)));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.newPostCount()).isEqualTo(1);
        assertThat(summary.changeCount()).isZero();

        PostTrendCandidate stored = capturedCandidates().get(0);
        assertThat(stored.getCandidateType()).isEqualTo(PostTrendCandidate.TYPE_NEW_POST);
        assertThat(stored.getMatchedPostId()).isNull();
        assertThat(stored.getConfirmStatus()).isEqualTo(PostTrendCandidate.CONFIRM_PENDING);

        AiHarnessClaimDTO claim = capturedClaims().get(0);
        assertThat(claim.getClaimType()).isEqualTo("EMERGING_POST");
        assertThat(claim.getScenario()).isEqualTo("INDUSTRY_TREND_ANALYSIS");
        assertThat(claim.getSourceRefs()).isNotEmpty();
    }

    @Test
    @DisplayName("相似度高 → 能力变更候选并锚定既有岗位")
    void highSimilarityProducesAbilityChangeCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "人工智能训练师", 0.93D)));
        // 既有岗位没有「大模型微调」→ 纯新增，非高影响变更
        when(postAbilityModelService.listByPostId(9L)).thenReturn(List.of(existingAbility("提示词工程", 3, 30)));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.changeCount()).isEqualTo(1);

        PostTrendCandidate stored = capturedCandidates().get(0);
        assertThat(stored.getCandidateType()).isEqualTo(PostTrendCandidate.TYPE_ABILITY_CHANGE);
        assertThat(stored.getMatchedPostId()).isEqualTo(9L);
        assertThat(stored.getMatchedPostName()).isEqualTo("人工智能训练师");
        assertThat(stored.getSimilarityScore()).isEqualByComparingTo(BigDecimal.valueOf(0.9300));

        TrendCandidatePayload payload = payloadOf(stored);
        assertThat(payload.getAbilities()).hasSize(1);
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("ADD");

        AiHarnessClaimDTO claim = capturedClaims().get(0);
        assertThat(claim.getClaimType()).isEqualTo("POST_ABILITY_CHANGE");
        assertThat(claim.getChangeType()).isEqualTo("ADD_ABILITY");
        assertThat(claim.getBusinessTargetId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("相似度检索不可用 → 不伪装成「无相似岗位」，候选按新岗位处理并显式警示")
    void unavailableSimilarityIsNotTreatedAsNoSimilarPost() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40))));
        stubResolverAllResolved();
        when(postSimilarityService.findSimilarPosts(any(), anyInt(), any()))
                .thenReturn(PostSimilarityService.PostSimilarityResult.unavailable());

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.similarityServiceAvailable()).isFalse();
        assertThat(summary.diagnostics()).contains("岗位相似度检索不可用");
        assertThat(capturedCandidates().get(0).getCandidateType()).isEqualTo(PostTrendCandidate.TYPE_NEW_POST);
    }

    // ---------------------------------------------------------------- 能力变更差异

    @Test
    @DisplayName("材料与既有岗位能力完全一致时不出候选，避免空变更打扰审核")
    void unchangedAbilityModelProducesNoCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 3, 30))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "人工智能训练师", 0.95D)));
        when(postAbilityModelService.listByPostId(9L)).thenReturn(List.of(existingAbility("大模型微调", 3, 30)));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.candidateCount()).isZero();
        assertThat(summary.diagnostics()).contains("与既有岗位能力一致");
        verify(candidateMapper, never()).insert(any(PostTrendCandidate.class));
    }

    @Test
    @DisplayName("等级提升被识别为高影响变更，且材料未提及的既有能力只作参考、绝不建议删除")
    void levelUpgradeIsDetectedAndMissingAbilitiesAreNeverProposedForRemoval() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 5, 60))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "人工智能训练师", 0.90D)));
        when(postAbilityModelService.listByPostId(9L)).thenReturn(List.of(
                existingAbility("大模型微调", 2, 40),
                existingAbility("知识蒸馏", 3, 20)));

        service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        PostTrendCandidate stored = capturedCandidates().get(0);
        TrendCandidatePayload payload = payloadOf(stored);

        TrendCandidatePayload.TrendAbilityItem upgraded = payload.getAbilities().get(0);
        assertThat(upgraded.getChangeType()).isEqualTo("UPGRADE_LEVEL");
        assertThat(upgraded.getExistingLevel()).isEqualTo(2);
        assertThat(upgraded.getSuggestedLevel()).isEqualTo(5);

        assertThat(payload.getUnmatchedExistingAbilities()).containsExactly("知识蒸馏");
        assertThat(payload.getAbilities())
                .noneMatch(item -> item.getChangeType() != null && item.getChangeType().contains("REMOVE"));

        assertThat(capturedClaims().get(0).getChangeType()).isEqualTo("UPGRADE_LEVEL");
    }

    // ---------------------------------------------------------------- 标签归位

    @Test
    @DisplayName("未归位能力保留原始能力名并挂上标签候选ID，tagId 为空")
    void unresolvedAbilityKeepsNameAndLinksTagCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("智能体编排", 4, 30))));
        when(abilityResolver.resolve(eq("智能体编排"), eq(TASK_ID), any()))
                .thenReturn(new TrendAbilityResolver.TagResolution(
                        null, "智能体编排", false, 55L, "工作流编排",
                        BigDecimal.valueOf(0.7500), 999L));
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.20D)));

        service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        TrendCandidatePayload.TrendAbilityItem item = payloadOf(capturedCandidates().get(0)).getAbilities().get(0);
        assertThat(item.isResolved()).isFalse();
        assertThat(item.getTagId()).isNull();
        assertThat(item.getAbilityName()).isEqualTo("智能体编排");
        assertThat(item.getTagCandidateId()).isEqualTo(999L);
        assertThat(item.getSimilarTagName()).isEqualTo("工作流编排");
        assertThat(item.getSimilarity()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
    }

    // ---------------------------------------------------------------- 治理判定

    @Test
    @DisplayName("治理判定服务异常时降级为待人工复核，候选照常产出")
    void harnessFailureDegradesToReviewInsteadOfDroppingCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.25D)));
        when(harnessService.verify(any())).thenThrow(new IllegalStateException("harness down"));

        service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        PostTrendCandidate stored = capturedCandidates().get(0);
        assertThat(stored.getHarnessDecision()).isEqualTo(AiHarnessDecisionDTO.REVIEW);
        assertThat(stored.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(payloadOf(stored).getHarnessReason()).contains("治理判定服务不可用");
    }

    @Test
    @DisplayName("治理判定结果与理由写入候选，且不污染审核意见字段")
    void harnessOutcomeIsPersistedOnCandidate() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.25D)));
        when(harnessService.verify(any())).thenReturn(
                decision(AiHarnessDecisionDTO.PASS, "LOW", "multiple source support with evidence"));

        service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        PostTrendCandidate stored = capturedCandidates().get(0);
        assertThat(stored.getHarnessDecision()).isEqualTo(AiHarnessDecisionDTO.PASS);
        assertThat(stored.getReviewComment()).isNull();
        assertThat(payloadOf(stored).getHarnessReason()).contains("multiple source support");
    }

    @Test
    @DisplayName("同一任务重跑不重复产出同一候选")
    void rerunDoesNotDuplicateCandidates() {
        when(aiService.analyze(anyString())).thenReturn(ai(post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40))));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.25D)));

        // 第一次解析把候选写库
        service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));
        PostTrendCandidate first = capturedCandidates().get(0);

        // 第二次解析时该指纹已存在
        when(candidateMapper.selectList(any())).thenReturn(List.of(first));
        PostTrendAnalysisSummary second = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(second.candidateCount()).isZero();
        assertThat(second.diagnostics()).contains("重复");
    }

    @Test
    @DisplayName("诊断里带出强调度、多来源印证与候选构成，便于事后校准阈值")
    void diagnosticsSummarizeThresholdOutcome() {
        PostTrendAiResult.TrendPost strong = post("生成式人工智能训练师", 8.0D, 0,
                ability("大模型微调", 4, 40));
        PostTrendAiResult.TrendPost weak = post("人工智能训练师助理", 1.5D, 1,
                ability("数据标注", 2, 10));
        when(aiService.analyze(anyString())).thenReturn(ai(strong, weak));
        stubResolverAllResolved();
        stubSimilarity(List.of(new PostSimilarityService.PostSimilarityMatch(9L, "算法工程师", 0.25D)));

        PostTrendAnalysisSummary summary = service.analyze(TASK_ID, List.of(1L), List.of("POLICY_DOCUMENT"));

        assertThat(summary.extractedPostCount()).isEqualTo(2);
        assertThat(summary.candidateCount()).isEqualTo(1);
        assertThat(summary.filteredByEmphasisCount()).isEqualTo(1);
        assertThat(summary.diagnostics()).contains("已解析 1 份材料").contains("新岗位 1");
    }

    // ---------------------------------------------------------------- helpers

    private void stubDocument(Long id, Long ragDocumentId, String sourceType, int chunkCount) {
        KnowledgeSourceDocument document = new KnowledgeSourceDocument();
        document.setId(id);
        document.setRagDocumentId(ragDocumentId);
        document.setSourceType(sourceType);
        document.setSourceCategory(sourceType);
        document.setTitle("关于加快数字技能人才培养的行动方案");
        document.setStatus("ACTIVE");
        document.setChunkCount(chunkCount);
        when(sourceDocumentMapper.selectBatchIds(any())).thenReturn(List.of(document));
    }

    private void stubChunks(List<RagKnowledgeChunk> chunks) {
        when(chunkMapper.selectList(any())).thenReturn(chunks);
    }

    private RagKnowledgeChunk chunk(long id, long ragDocumentId, int index, String text) {
        RagKnowledgeChunk chunk = new RagKnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(ragDocumentId);
        chunk.setChunkIndex(index);
        chunk.setChunkText(text);
        chunk.setChunkStatus("ACTIVE");
        return chunk;
    }

    private void stubSimilarity(List<PostSimilarityService.PostSimilarityMatch> matches) {
        when(postSimilarityService.findSimilarPosts(any(), anyInt(), any()))
                .thenReturn(new PostSimilarityService.PostSimilarityResult(true, matches));
    }

    private void stubResolverAllResolved() {
        when(abilityResolver.resolve(anyString(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(0);
                    return new TrendAbilityResolver.TagResolution(77L, name, true, null, null, null, null);
                });
    }

    private PostTrendAiResult ai(PostTrendAiResult.TrendPost... posts) {
        PostTrendAiResult result = new PostTrendAiResult();
        result.setPosts(new ArrayList<>(List.of(posts)));
        return result;
    }

    private PostTrendAiResult.TrendPost post(String name, double emphasis, Integer evidenceRef,
                                             PostTrendAiResult.TrendAbility... abilities) {
        PostTrendAiResult.TrendPost post = new PostTrendAiResult.TrendPost();
        post.setPostName(name);
        post.setPostDescription(name + " 岗位描述草案");
        post.setEmphasisScore(BigDecimal.valueOf(emphasis));
        post.setEmphasisReason("材料中列为重点培养方向");
        post.setEvidenceRef(evidenceRef);
        post.setAbilities(new ArrayList<>(List.of(abilities)));
        return post;
    }

    private PostTrendAiResult.TrendAbility ability(String name, Integer level, double weight) {
        PostTrendAiResult.TrendAbility ability = new PostTrendAiResult.TrendAbility();
        ability.setAbilityName(name);
        ability.setSuggestedLevel(level);
        ability.setSuggestedWeight(BigDecimal.valueOf(weight));
        ability.setIsCore(1);
        return ability;
    }

    private PostAbilityModel existingAbility(String name, Integer level, double weight) {
        PostAbilityModel model = new PostAbilityModel();
        model.setAbilityName(name);
        model.setMinRequiredLevel(level);
        model.setWeight(BigDecimal.valueOf(weight));
        return model;
    }

    private AiHarnessDecisionDTO decision(String decision, String riskLevel, String reason) {
        AiHarnessDecisionDTO dto = new AiHarnessDecisionDTO();
        dto.setDecision(decision);
        dto.setRiskLevel(riskLevel);
        dto.setReasons(new ArrayList<>(List.of(reason)));
        return dto;
    }

    private List<PostTrendCandidate> capturedCandidates() {
        ArgumentCaptor<PostTrendCandidate> captor = ArgumentCaptor.forClass(PostTrendCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        return captor.getAllValues();
    }

    private List<AiHarnessClaimDTO> capturedClaims() {
        ArgumentCaptor<AiHarnessClaimDTO> captor = ArgumentCaptor.forClass(AiHarnessClaimDTO.class);
        verify(harnessService, org.mockito.Mockito.atLeastOnce()).verify(captor.capture());
        return captor.getAllValues();
    }

    private TrendCandidatePayload payloadOf(PostTrendCandidate candidate) {
        try {
            return objectMapper.readValue(candidate.getCandidatePayload(), TrendCandidatePayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("候选载荷反序列化失败", e);
        }
    }
}
