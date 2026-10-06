package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.agent.config.AgentToolProvider;
import com.example.matching.agent.dto.PostTrendAiResult;
import com.example.matching.agent.lc4j.PostTrendAiService;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.config.PostTrendProperties;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.entity.post.PostTrendCandidate;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.mapper.post.PostTrendCandidateMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.post.PostSimilarityService;
import com.example.matching.service.post.PostTrendAnalysisService;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.service.post.support.TrendAbilityDiffService;
import com.example.matching.service.post.support.TrendAbilityResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 岗位趋势解析实现。
 * <p>
 * 编排顺序：<b>读材料 → LLM 抽取 → 标签归位 → 相似度分流 → Harness 判定 → 落候选</b>。
 * <p>
 * 三个刻意的设计取舍：
 * <ol>
 *   <li><b>直读切片而不是走 RAG Top-K</b>：趋势发现要「把材料里的岗位找全」，
 *       Top-K 相似度排序天然会截断长文档的后半部分，把真正的趋势岗位漏掉。
 *       这里按 documentId 直读切片，并跨材料轮转取样保证每份材料都有代表片段。</li>
 *   <li><b>新增能力名不自动进正式标签库</b>：只写 {@code ability_tag_candidate}。
 *       否则一次解析就能往标签体系里灌进几十个未经治理的标签（见设计文档 §7.3）。</li>
 *   <li><b>「材料未提及」不等于「应当删除」</b>：能力变更候选只产出 ADD / 升降级 / 权重调整，
 *       既有但材料未提及的能力只列进 payload 供人工参考，绝不生成删除建议。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTrendAnalysisServiceImpl implements PostTrendAnalysisService {

    /** 单个材料片段的字符上限，避免一个超长切片吃掉整个上下文预算 */
    private static final int MAX_SEGMENT_CHARS = 1_200;
    /** 候选证据原文的字符上限（列宽 4096） */
    private static final int MAX_EVIDENCE_CHARS = 3_600;
    /** 岗位描述列宽 */
    private static final int MAX_DESCRIPTION_CHARS = 2_000;
    /** 声明来源标识；不得落在 Harness 的「AI 自证」名单里 */
    private static final String CLAIM_SOURCE_TYPE = "AUTHORITY_MATERIAL";

    private final PostTrendTaskService taskService;
    private final KnowledgeSourceDocumentMapper sourceDocumentMapper;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final EvolutionSourceIngestionService sourceIngestionService;
    private final ObjectProvider<PostTrendAiService> aiServiceProvider;
    private final PostSimilarityService postSimilarityService;
    private final TrendAbilityResolver abilityResolver;
    private final TrendAbilityDiffService abilityDiffService;
    private final AiTrustHarnessService harnessService;
    private final PostTrendCandidateMapper candidateMapper;
    private final PostTrendProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PostTrendAnalysisSummary analyze(Long taskId, List<Long> sourceDocumentIds, List<String> sourceCategories) {
        List<Long> documentIds = sourceDocumentIds == null ? List.of()
                : sourceDocumentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (documentIds.isEmpty()) {
            return PostTrendAnalysisSummary.empty("本次任务没有可解析的材料，请重新上传后再发起");
        }

        taskService.reportProgress(taskId, "RETRIEVING", 10);
        List<String> unavailable = new ArrayList<>();
        List<MaterialDocument> documents = loadDocuments(documentIds, unavailable);
        if (documents.isEmpty()) {
            return PostTrendAnalysisSummary.empty(
                    "所选材料均不可用：" + String.join("；", unavailable) + "。请在材料列表中确认后重新发起");
        }

        List<MaterialSegment> segments = buildSegments(documents);
        if (segments.isEmpty()) {
            return PostTrendAnalysisSummary.empty(
                    "材料未提取到可用文本片段。若材料是扫描件 PDF（无文本层），请改用可复制文字的版本或转换为 Word 后重传");
        }

        taskService.reportProgress(taskId, "EXTRACTING", 35);
        PostTrendAiService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            // 配置类问题：直接失败，比返回「0 个候选」更容易被发现和修复
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR,
                    "AI 解析能力未启用（langchain4j.agents.enabled=false），无法从材料中抽取趋势岗位");
        }
        PostTrendAiResult aiResult = AgentToolProvider.withScope(() -> aiService.analyze(buildAiContext(segments)));
        List<PostTrendAiResult.TrendPost> extracted = aiResult == null || aiResult.getPosts() == null
                ? List.of() : aiResult.getPosts().stream().filter(Objects::nonNull).toList();

        List<EvaluatedPost> evaluated = evaluate(extracted, segments);
        int extractedCount = evaluated.size();
        int filteredByEmphasis = (int) evaluated.stream().filter(p -> !p.kept()).count();
        List<EvaluatedPost> kept = evaluated.stream().filter(EvaluatedPost::kept)
                .sorted(Comparator.comparing(EvaluatedPost::effectiveEmphasis).reversed())
                .toList();

        int overLimit = Math.max(0, kept.size() - properties.getMaxExtractedPosts());
        if (overLimit > 0) {
            kept = kept.subList(0, properties.getMaxExtractedPosts());
        }

        taskService.reportProgress(taskId, "MATCHING", 60);
        Set<String> existingFingerprints = loadExistingFingerprints(taskId);
        int newPostCount = 0;
        int changeCount = 0;
        int skippedNoChange = 0;
        int skippedNoAbility = 0;
        int skippedDuplicate = 0;
        boolean similarityAvailable = true;

        List<CandidateDraft> drafts = new ArrayList<>();
        for (EvaluatedPost post : kept) {
            CandidateDraft draft = buildCandidate(taskId, post, segments);
            if (draft.payload().getAbilities().isEmpty()) {
                // 没有能力的岗位候选无法落地成能力画像，产出来只会让审核人白点一次
                skippedNoAbility++;
                continue;
            }
            if (draft.abilityChange() && draft.diffCount() == 0) {
                // 材料完全印证了既有岗位的现状，没有需要确认的变更
                skippedNoChange++;
                continue;
            }
            if (!existingFingerprints.add(draft.fingerprint())) {
                skippedDuplicate++;
                continue;
            }
            if (!draft.similarityAvailable()) {
                similarityAvailable = false;
            }
            drafts.add(draft);
        }

        taskService.reportProgress(taskId, "HARNESS", 80);
        int harnessPass = 0;
        for (CandidateDraft draft : drafts) {
            HarnessOutcome outcome = verifyHarness(taskId, draft);
            // 治理理由写进载荷而不是 review_comment：后者是审核人自己的意见字段，
            // 混写会让「为什么被判待复核」看起来像人工留言。
            draft.payload().setHarnessReason(truncate(outcome.reason(), 1_000));
            persist(taskId, draft, outcome);
            if (PostTrendCandidate.TYPE_NEW_POST.equals(draft.candidateType())) {
                newPostCount++;
            } else {
                changeCount++;
            }
            if (AiHarnessDecisionDTO.PASS.equals(outcome.decision())) {
                harnessPass++;
            }
        }

        String diagnostics = truncate(buildDiagnostics(extractedCount, filteredByEmphasis, kept.size(),
                newPostCount, changeCount, overLimit, skippedNoChange, skippedNoAbility, skippedDuplicate,
                documents.size(), similarityAvailable, unavailable, harnessPass), 2_000);

        log.info("趋势解析完成: taskId={}, 材料={}, 片段={}, 抽取={}, 候选={}(新岗位{} / 变更{}), 诊断={}",
                taskId, documents.size(), segments.size(), extractedCount, drafts.size(), newPostCount, changeCount,
                diagnostics);

        return new PostTrendAnalysisSummary(documents.size(), extractedCount, drafts.size(),
                newPostCount, changeCount, filteredByEmphasis, similarityAvailable, diagnostics);
    }

    // ================================================================ 材料

    private List<MaterialDocument> loadDocuments(List<Long> documentIds, List<String> unavailable) {
        List<KnowledgeSourceDocument> found = sourceDocumentMapper.selectBatchIds(documentIds);
        Map<Long, KnowledgeSourceDocument> byId = new HashMap<>();
        if (found != null) {
            for (KnowledgeSourceDocument document : found) {
                if (document != null && document.getId() != null) {
                    byId.put(document.getId(), document);
                }
            }
        }

        List<MaterialDocument> documents = new ArrayList<>();
        for (Long documentId : documentIds) {
            KnowledgeSourceDocument document = byId.get(documentId);
            if (document == null) {
                unavailable.add("文档 " + documentId + " 不存在");
                continue;
            }
            if (!isIndexed(document)) {
                // 上传后只建了 RAG 文档、还没切片（status=PENDING）。用户传完直接点解析是最常见的路径，
                // 这里补一次索引，否则「上传了却永远解析不出东西」。
                try {
                    sourceIngestionService.indexKnowledgeSource(documentId);
                    document = sourceDocumentMapper.selectById(documentId);
                } catch (Exception e) {
                    log.warn("材料索引失败: documentId={}, err={}", documentId, e.getMessage());
                    unavailable.add(titleOf(document) + "（文本提取失败，可能是扫描件）");
                    continue;
                }
            }
            if (document == null || !isIndexed(document) || document.getRagDocumentId() == null) {
                unavailable.add(titleOf(byId.get(documentId)) + "（未完成索引）");
                continue;
            }
            documents.add(new MaterialDocument(document.getId(), document.getRagDocumentId(),
                    document.getSourceType(), document.getSourceCategory(), titleOf(document)));
        }
        return documents;
    }

    private boolean isIndexed(KnowledgeSourceDocument document) {
        return document != null
                && "ACTIVE".equals(document.getStatus())
                && document.getChunkCount() != null && document.getChunkCount() > 0
                && document.getRagDocumentId() != null;
    }

    private String titleOf(KnowledgeSourceDocument document) {
        if (document == null) {
            return "未知材料";
        }
        return StringUtils.hasText(document.getTitle()) ? document.getTitle() : "材料#" + document.getId();
    }

    /**
     * 跨材料轮转取样构建片段列表。
     * <p>
     * 若按材料顺序依次取满，材料一多后面的材料就永远拿不到片段，
     * 「多来源印证」（同一岗位被 ≥2 类材料提到）会永远算不出来。
     */
    private List<MaterialSegment> buildSegments(List<MaterialDocument> documents) {
        List<Deque<RagKnowledgeChunk>> queues = new ArrayList<>(documents.size());
        for (MaterialDocument document : documents) {
            queues.add(new ArrayDeque<>(loadChunks(document.ragDocumentId())));
        }

        List<MaterialSegment> segments = new ArrayList<>();
        int usedChars = 0;
        boolean progressed = true;
        while (segments.size() < properties.getMaxMaterialSegments()
                && usedChars < properties.getMaxMaterialChars()
                && progressed) {
            progressed = false;
            for (int i = 0; i < queues.size() && segments.size() < properties.getMaxMaterialSegments(); i++) {
                Deque<RagKnowledgeChunk> queue = queues.get(i);
                if (queue.isEmpty()) {
                    continue;
                }
                progressed = true;
                RagKnowledgeChunk polled = queue.poll();
                String text = normalizeText(polled.getChunkText());
                if (text.isEmpty()) {
                    continue;
                }
                if (text.length() > MAX_SEGMENT_CHARS) {
                    text = text.substring(0, MAX_SEGMENT_CHARS);
                }
                MaterialDocument document = documents.get(i);
                segments.add(new MaterialSegment(segments.size(), polled.getId(), text,
                        AbilityNameNormalizer.normalize(text), document));
                usedChars += text.length();
            }
        }
        return segments;
    }

    private List<RagKnowledgeChunk> loadChunks(Long ragDocumentId) {
        LambdaQueryWrapper<RagKnowledgeChunk> wrapper = new LambdaQueryWrapper<RagKnowledgeChunk>()
                .eq(RagKnowledgeChunk::getDocumentId, ragDocumentId)
                .eq(RagKnowledgeChunk::getChunkStatus, "ACTIVE")
                .orderByAsc(RagKnowledgeChunk::getChunkIndex)
                .last("LIMIT " + properties.getMaxChunksPerDocument());
        List<RagKnowledgeChunk> chunks = chunkMapper.selectList(wrapper);
        if (chunks == null) {
            return List.of();
        }
        // 同一文档可能被重复索引过，按 chunkIndex 去重保留首条，避免同一段材料喂两遍抬高强调度
        Map<Integer, RagKnowledgeChunk> distinct = new LinkedHashMap<>();
        for (RagKnowledgeChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            Integer index = chunk.getChunkIndex() != null ? chunk.getChunkIndex() : distinct.size();
            distinct.putIfAbsent(index, chunk);
        }
        return new ArrayList<>(distinct.values());
    }

    private String buildAiContext(List<MaterialSegment> segments) {
        StringBuilder builder = new StringBuilder();
        builder.append("材料片段总数：").append(segments.size()).append("\n\n");
        for (MaterialSegment segment : segments) {
            builder.append('[').append(segment.index()).append("] 来源：")
                    .append(describeCategory(segment.document().sourceCategory()))
                    .append('\n').append(segment.text()).append("\n\n");
        }
        return builder.toString();
    }

    private String describeCategory(String sourceCategory) {
        if (!StringUtils.hasText(sourceCategory)) {
            return "权威材料";
        }
        return switch (sourceCategory) {
            case "POLICY_DOCUMENT" -> "政策文件";
            case "MARKET_REPORT" -> "市场职业报告";
            case "OCCUPATION_STANDARD" -> "职业标准";
            case "INDUSTRY_WHITEPAPER" -> "行业白皮书";
            default -> sourceCategory;
        };
    }

    // ================================================================ 评估与过滤

    /** 去重 → 交叉校验强调度 → 按阈值过滤。 */
    private List<EvaluatedPost> evaluate(List<PostTrendAiResult.TrendPost> extracted, List<MaterialSegment> segments) {
        Map<String, PostTrendAiResult.TrendPost> deduped = new LinkedHashMap<>();
        for (PostTrendAiResult.TrendPost post : extracted) {
            if (post == null || !StringUtils.hasText(post.getPostName())) {
                continue;
            }
            String key = AbilityNameNormalizer.normalize(post.getPostName());
            if (key.isEmpty()) {
                continue;
            }
            deduped.merge(key, post, (left, right) -> rawEmphasis(left).compareTo(rawEmphasis(right)) >= 0 ? left : right);
        }

        List<EvaluatedPost> evaluated = new ArrayList<>(deduped.size());
        for (PostTrendAiResult.TrendPost post : deduped.values()) {
            int mentionCount = countMentions(segments, post.getPostName());
            int coverage = countCoverage(segments, post.getPostName());
            BigDecimal effective = effectiveEmphasis(rawEmphasis(post), mentionCount);
            evaluated.add(new EvaluatedPost(post, effective, mentionCount, coverage,
                    effective.doubleValue() >= properties.getMinEmphasis()));
        }
        return evaluated;
    }

    /**
     * 材料强调度交叉校验。
     * <p>
     * LLM 自评的强调度会「飘」：把只在长表格里出现过一次的岗位说成重点。这里用可验证的
     * 提及次数把它压回去——材料里一次都没提到却报了高分，一律压到最低档。
     */
    private BigDecimal effectiveEmphasis(BigDecimal llmScore, int mentionCount) {
        if (mentionCount == 0) {
            return llmScore.min(BigDecimal.ONE);
        }
        return llmScore;
    }

    private BigDecimal rawEmphasis(PostTrendAiResult.TrendPost post) {
        return post.getEmphasisScore() == null ? BigDecimal.ZERO : post.getEmphasisScore();
    }

    private int countMentions(List<MaterialSegment> segments, String postName) {
        String normalized = AbilityNameNormalizer.normalize(postName);
        int count = 0;
        for (MaterialSegment segment : segments) {
            if (segment.text().contains(postName)
                    || (!normalized.isEmpty() && segment.normalizedText().contains(normalized))) {
                count++;
            }
        }
        return count;
    }

    private int countCoverage(List<MaterialSegment> segments, String postName) {
        String normalized = AbilityNameNormalizer.normalize(postName);
        Set<String> categories = new LinkedHashSet<>();
        for (MaterialSegment segment : segments) {
            boolean mentioned = segment.text().contains(postName)
                    || (!normalized.isEmpty() && segment.normalizedText().contains(normalized));
            if (mentioned) {
                categories.add(segment.document().sourceCategory() == null ? "UNKNOWN" : segment.document().sourceCategory());
            }
        }
        return categories.size();
    }

    // ================================================================ 候选构建

    private CandidateDraft buildCandidate(Long taskId, EvaluatedPost evaluated, List<MaterialSegment> segments) {
        PostTrendAiResult.TrendPost post = evaluated.post();
        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.setResponsibilities(safeList(post.getResponsibilities()));
        payload.setBusinessScenarios(safeList(post.getBusinessScenarios()));
        payload.setLlmEmphasisScore(post.getEmphasisScore());
        payload.setEmphasisReason(post.getEmphasisReason());
        payload.setEffectiveEmphasisScore(evaluated.effectiveEmphasis());
        payload.setMentionCount(evaluated.mentionCount());
        payload.setSourceCoverage(evaluated.coverage());

        Map<Long, String> documentRefs = new LinkedHashMap<>();
        Set<Long> referencedChunks = new LinkedHashSet<>();
        Set<Integer> evidenceIndexes = new LinkedHashSet<>();

        for (PostTrendAiResult.TrendAbility ability : safeList(post.getAbilities())) {
            if (ability == null || !StringUtils.hasText(ability.getAbilityName())) {
                continue;
            }
            MaterialSegment segment = resolveSegment(segments,
                    ability.getEvidenceRef() != null ? ability.getEvidenceRef() : post.getEvidenceRef());
            TrendAbilityResolver.TagResolution resolution = abilityResolver.resolve(
                    ability.getAbilityName(), taskId, segment == null ? null : segment.text());

            TrendCandidatePayload.TrendAbilityItem item = new TrendCandidatePayload.TrendAbilityItem();
            item.setAbilityName(ability.getAbilityName().trim());
            item.setTagId(resolution.tagId());
            item.setMatchedTagName(resolution.matchedTagName());
            item.setResolved(resolution.resolved());
            item.setSimilarTagId(resolution.similarTagId());
            item.setSimilarTagName(resolution.similarTagName());
            item.setSimilarity(resolution.similarity());
            item.setTagCandidateId(resolution.tagCandidateId());
            item.setSuggestedLevel(clampLevel(ability.getSuggestedLevel()));
            item.setSuggestedWeight(clampWeight(ability.getSuggestedWeight()));
            item.setIsCore(Integer.valueOf(1).equals(ability.getIsCore()) ? 1 : 0);
            if (segment != null) {
                item.setEvidenceRef(segment.index());
                item.setSourceRef(segment.sourceRef());
                referencedChunks.add(segment.chunkId());
                documentRefs.putIfAbsent(segment.document().ragDocumentId(), segment.sourceRef());
                evidenceIndexes.add(segment.index());
            }
            payload.getAbilities().add(item);
        }

        List<String> abilityNames = payload.getAbilities().stream()
                .map(TrendCandidatePayload.TrendAbilityItem::getAbilityName)
                .toList();
        PostSimilarityService.PostSimilarityResult similarity = abilityNames.isEmpty()
                ? PostSimilarityService.PostSimilarityResult.unavailable()
                : postSimilarityService.findSimilarPosts(abilityNames, properties.getPostMatchTopK(), null);
        PostSimilarityService.PostSimilarityMatch best = similarity.best();
        boolean abilityChange = similarity.available() && best != null
                && best.similarity() >= properties.getSimilarityThreshold();

        int diffCount = 0;
        if (abilityChange) {
            diffCount = abilityDiffService.applyDiff(best.postId(), payload);
        } else {
            abilityDiffService.markAllAsAdded(payload);
        }

        BigDecimal similarityScore = best == null ? null
                : BigDecimal.valueOf(best.similarity()).setScale(4, RoundingMode.HALF_UP);
        String candidateType = abilityChange ? PostTrendCandidate.TYPE_ABILITY_CHANGE : PostTrendCandidate.TYPE_NEW_POST;
        Long matchedPostId = abilityChange ? best.postId() : null;
        String matchedPostName = abilityChange ? best.postName() : null;

        return new CandidateDraft(
                candidateType,
                post.getPostName().trim(),
                truncate(post.getPostDescription(), MAX_DESCRIPTION_CHARS),
                payload,
                matchedPostId,
                matchedPostName,
                similarityScore,
                evaluated.effectiveEmphasis().setScale(2, RoundingMode.HALF_UP),
                evaluated.coverage(),
                buildEvidenceText(segments, evidenceIndexes),
                new ArrayList<>(documentRefs.values()),
                new ArrayList<>(referencedChunks),
                fingerprint(post.getPostName(), candidateType, matchedPostId),
                abilityChange,
                diffCount,
                similarity.available());
    }

    private MaterialSegment resolveSegment(List<MaterialSegment> segments, Integer index) {
        if (index == null || index < 0 || index >= segments.size()) {
            return null;
        }
        return segments.get(index);
    }

    private String buildEvidenceText(List<MaterialSegment> segments, Set<Integer> indexes) {
        StringBuilder builder = new StringBuilder();
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= segments.size()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n---\n");
            }
            builder.append(segments.get(index).text());
            if (builder.length() >= MAX_EVIDENCE_CHARS) {
                break;
            }
        }
        return truncate(builder.toString(), MAX_EVIDENCE_CHARS);
    }

    // ================================================================ 治理判定

    private HarnessOutcome verifyHarness(Long taskId, CandidateDraft draft) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        try {
            // 复用演化场景分支：EMERGING_POST 恒 REVIEW（永不自动放行新建岗位），
            // 能力变更则按来源数量与影响面给出 PASS / REVIEW / BLOCK。
            claim.setScenario(SourceRefConstants.SCENARIO_INDUSTRY_TREND_ANALYSIS);
            claim.setSourceType(CLAIM_SOURCE_TYPE);
            claim.setSourceRefId(taskId);
            claim.setEvidenceText(draft.evidenceText());
            claim.setSourceRefs(new ArrayList<>(draft.sourceRefs()));
            claim.setRagChunkIds(new ArrayList<>(draft.chunkIds()));
            claim.setConfidence(Math.min(100D, draft.emphasisScore().doubleValue() * 10D));

            if (PostTrendCandidate.TYPE_NEW_POST.equals(draft.candidateType())) {
                claim.setClaimType(SourceRefConstants.CLAIM_TYPE_EMERGING_POST);
                claim.setBusinessTargetType("POST");
                claim.setClaimText("权威材料显示出现趋势岗位：" + draft.postName()
                        + "（材料强调度 " + draft.emphasisScore() + "，印证来源 " + draft.sourceCoverage() + " 类）");
            } else {
                claim.setClaimType(SourceRefConstants.CLAIM_TYPE_POST_ABILITY_CHANGE);
                claim.setChangeType(resolveChangeType(draft.payload()));
                claim.setBusinessTargetType("POST_ABILITY_MODEL");
                claim.setBusinessTargetId(draft.matchedPostId());
                claim.setClaimText("既有岗位「" + draft.matchedPostName() + "」需要变更能力："
                        + describeDiff(draft.payload()));
            }

            AiHarnessDecisionDTO decision = harnessService.verify(claim);
            return new HarnessOutcome(decision.getDecision(), decision.getRiskLevel(),
                    String.join("；", decision.getReasons()),
                    decision.getAcceptedSourceRefs() == null ? 0 : decision.getAcceptedSourceRefs().size());
        } catch (Exception e) {
            // 治理判定不可用不能拖垮整次解析：降级为「待人工复核」，由人兜底
            log.warn("Harness 判定失败，候选降级为待复核: taskId={}, post={}, err={}",
                    taskId, draft.postName(), e.getMessage());
            return new HarnessOutcome(AiHarnessDecisionDTO.REVIEW, "MEDIUM",
                    "治理判定服务不可用，已降级为人工复核", 0);
        }
    }

    private String resolveChangeType(TrendCandidatePayload payload) {
        boolean hasUpgrade = false;
        boolean hasDowngrade = false;
        boolean hasAdd = false;
        boolean hasWeight = false;
        for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
            String type = item.getChangeType();
            if ("UPGRADE_LEVEL".equals(type)) {
                hasUpgrade = true;
            } else if ("DOWNGRADE_LEVEL".equals(type)) {
                hasDowngrade = true;
            } else if ("ADD".equals(type)) {
                hasAdd = true;
            } else if ("UPDATE_WEIGHT".equals(type)) {
                hasWeight = true;
            }
        }
        // 影响面优先：只要涉及等级调整就按高影响变更走（Harness 会强制人工复核）
        if (hasUpgrade) {
            return "UPGRADE_LEVEL";
        }
        if (hasDowngrade) {
            return "DOWNGRADE_LEVEL";
        }
        if (hasAdd) {
            return "ADD_ABILITY";
        }
        if (hasWeight) {
            return "UPDATE_WEIGHT";
        }
        return "UPDATE_ABILITY";
    }

    private String describeDiff(TrendCandidatePayload payload) {
        List<String> parts = new ArrayList<>();
        for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
            String type = item.getChangeType();
            if (type == null || "UNCHANGED".equals(type)) {
                continue;
            }
            switch (type) {
                case "ADD" -> parts.add("新增 " + item.getAbilityName());
                case "UPGRADE_LEVEL" -> parts.add(item.getAbilityName() + " 由 "
                        + item.getExistingLevel() + " 升到 " + item.getSuggestedLevel());
                case "DOWNGRADE_LEVEL" -> parts.add(item.getAbilityName() + " 由 "
                        + item.getExistingLevel() + " 降到 " + item.getSuggestedLevel());
                case "UPDATE_WEIGHT" -> parts.add(item.getAbilityName() + " 权重调整为 " + item.getSuggestedWeight());
                default -> { }
            }
        }
        if (parts.isEmpty()) {
            return "无实质变更";
        }
        if (parts.size() > 8) {
            return String.join("，", parts.subList(0, 8)) + " 等 " + parts.size() + " 项";
        }
        return String.join("，", parts);
    }

    // ================================================================ 落库

    private void persist(Long taskId, CandidateDraft draft, HarnessOutcome outcome) {
        PostTrendCandidate entity = new PostTrendCandidate();
        entity.setTaskId(taskId);
        entity.setCandidateType(draft.candidateType());
        entity.setPostName(draft.postName());
        entity.setPostDescription(draft.postDescription());
        entity.setCandidatePayload(writeJson(draft.payload()));
        entity.setMatchedPostId(draft.matchedPostId());
        entity.setMatchedPostName(draft.matchedPostName());
        entity.setSimilarityScore(draft.similarityScore());
        entity.setEmphasisScore(draft.emphasisScore());
        entity.setSourceCoverage(draft.sourceCoverage());
        entity.setEvidenceText(draft.evidenceText());
        entity.setSourceRefs(writeJson(draft.sourceRefs()));
        entity.setHarnessDecision(outcome.decision());
        entity.setRiskLevel(outcome.riskLevel());
        entity.setConfirmStatus(PostTrendCandidate.CONFIRM_PENDING);
        entity.setFingerprint(draft.fingerprint());
        candidateMapper.insert(entity);
    }

    private Set<String> loadExistingFingerprints(Long taskId) {
        List<PostTrendCandidate> existing = candidateMapper.selectList(
                new LambdaQueryWrapper<PostTrendCandidate>()
                        .eq(PostTrendCandidate::getTaskId, taskId)
                        .select(PostTrendCandidate::getFingerprint));
        Set<String> fingerprints = new HashSet<>();
        if (existing != null) {
            for (PostTrendCandidate candidate : existing) {
                if (StringUtils.hasText(candidate.getFingerprint())) {
                    fingerprints.add(candidate.getFingerprint());
                }
            }
        }
        return fingerprints;
    }

    // ================================================================ 诊断

    private String buildDiagnostics(int extractedCount, int filteredByEmphasis, int keptCount,
                                    int newPostCount, int changeCount, int overLimit,
                                    int skippedNoChange, int skippedNoAbility, int skippedDuplicate,
                                    int documentCount, boolean similarityAvailable,
                                    List<String> unavailable, int harnessPass) {
        List<String> parts = new ArrayList<>();
        parts.add("已解析 " + documentCount + " 份材料，识别出 " + extractedCount + " 个岗位");

        if (keptCount == 0) {
            if (extractedCount == 0) {
                parts.add("AI 未从中识别出任何岗位：请确认材料确实涉及职业 / 岗位要求（政策文件、职业报告、标准均可）");
            } else {
                String skipped = describeSkipped(filteredByEmphasis, skippedNoAbility,
                        skippedNoChange, skippedDuplicate, overLimit);
                parts.add(StringUtils.hasText(skipped)
                        ? "抽出的岗位全部未通过筛选（" + skipped + "）"
                        : "抽出的岗位全部未通过筛选");
            }
        } else {
            parts.add("产出 " + (newPostCount + changeCount) + " 个候选（新岗位 " + newPostCount
                    + " / 能力变更 " + changeCount + "）");
            parts.add("其中 " + harnessPass + " 个治理判定为可批量确认");
            String skipped = describeSkipped(filteredByEmphasis, skippedNoAbility, skippedNoChange,
                    skippedDuplicate, overLimit);
            if (StringUtils.hasText(skipped)) {
                parts.add("已跳过 " + skipped);
            }
        }

        if (!similarityAvailable) {
            parts.add("⚠ 岗位相似度检索不可用，本次候选一律按「新岗位」处理，请人工确认是否与既有岗位重复");
        }
        if (!unavailable.isEmpty()) {
            parts.add("未能解析的材料：" + String.join("；", unavailable));
        }
        return String.join("。", parts) + "。";
    }

    private String describeSkipped(int filteredByEmphasis, int skippedNoAbility, int skippedNoChange,
                                   int skippedDuplicate, int overLimit) {
        List<String> items = new ArrayList<>();
        if (filteredByEmphasis > 0) {
            items.add(filteredByEmphasis + " 个材料强调度不足");
        }
        if (skippedNoAbility > 0) {
            items.add(skippedNoAbility + " 个未解析出能力");
        }
        if (skippedNoChange > 0) {
            items.add(skippedNoChange + " 个与既有岗位能力一致、无变更");
        }
        if (skippedDuplicate > 0) {
            items.add(skippedDuplicate + " 个与已有候选重复");
        }
        if (overLimit > 0) {
            items.add(overLimit + " 个超出单次上限");
        }
        return String.join("、", items);
    }

    // ================================================================ 工具

    private Integer clampLevel(Integer level) {
        if (level == null) {
            return null;
        }
        return Math.max(1, Math.min(5, level));
    }

    private BigDecimal clampWeight(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        return weight.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").trim();
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("趋势候选载荷序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /** 去重指纹：归一化岗位名 + 候选类型 + 锚定岗位，保证同一任务重跑不会重复产出。 */
    private String fingerprint(String postName, String candidateType, Long matchedPostId) {
        String raw = AbilityNameNormalizer.normalize(postName) + "|" + candidateType + "|"
                + (matchedPostId == null ? "-" : matchedPostId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.substring(0, 40);
        } catch (Exception e) {
            return raw.length() <= 64 ? raw : raw.substring(0, 64);
        }
    }

    // ================================================================ 内部类型

    private record MaterialDocument(Long id, Long ragDocumentId, String sourceType,
                                    String sourceCategory, String title) {
    }

    private record MaterialSegment(int index, Long chunkId, String text, String normalizedText,
                                   MaterialDocument document) {
        /**
         * 标准来源引用，格式 {@code source:{sourceType}:{ragDocumentId}:{chunkId}}。
         * <p>
         * Harness 解析 {@code source:} 前缀时只按（sourceType, ragDocumentId）回查材料是否存在，
         * 片段号仅用于追溯具体位置。
         */
        String sourceRef() {
            return SourceRefConstants.knowledgeSourceRef(
                    document.sourceType(), document.ragDocumentId(), String.valueOf(chunkId));
        }
    }

    private record EvaluatedPost(PostTrendAiResult.TrendPost post, BigDecimal effectiveEmphasis,
                                 int mentionCount, int coverage, boolean kept) {
    }

    private record HarnessOutcome(String decision, String riskLevel, String reason, int acceptedRefCount) {
    }

    private record CandidateDraft(String candidateType, String postName, String postDescription,
                                  TrendCandidatePayload payload, Long matchedPostId, String matchedPostName,
                                  BigDecimal similarityScore, BigDecimal emphasisScore, Integer sourceCoverage,
                                  String evidenceText, List<String> sourceRefs, List<Long> chunkIds,
                                  String fingerprint, boolean abilityChange, int diffCount,
                                  boolean similarityAvailable) {
    }
}
