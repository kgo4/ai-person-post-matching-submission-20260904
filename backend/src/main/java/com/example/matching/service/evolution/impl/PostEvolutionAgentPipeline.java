package com.example.matching.service.evolution.impl;

import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal;
import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.agent.dto.PostEvolutionAiResult;
import com.example.matching.agent.lc4j.PostEvolutionAiService;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.evolution.support.EvolutionAbilityTagCatalog;
import com.example.matching.service.evolution.support.ResolvedEvolutionAbility;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService.RetrievalResult;
import com.example.matching.service.evolution.PostEvolutionSignalService.EvolutionSignal;
import com.example.matching.integration.zhihu.ZhihuApiProperties;
import com.example.matching.integration.zhihu.ZhihuSearchClient;
import com.example.matching.integration.zhihu.ZhihuSearchItem;
import com.example.matching.integration.zhihu.ZhihuSearchResponse;
import com.example.matching.service.evolution.ExternalResourceCleaningService;
import com.example.matching.service.evolution.EvolutionHarnessOrchestrator;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService;
import com.example.matching.service.evolution.PostEvolutionSignalService;
import com.example.matching.common.constant.SourceRefConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位演化 Agent 流水线：行业/内部证据检索、信号生成、模型对比、变更提议、证据落库。
 * <p>
 * 从 PostEvolutionAgentServiceImpl（660 行）中拆分的 Agent 流水线组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionAgentPipeline {

    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;
    private final PostEvolutionEvidenceMapper evidenceMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    private final PostEvolutionSignalService signalService;
    private final EvolutionHarnessOrchestrator harnessOrchestrator;
    private final EvolutionAbilityTagResolver abilityTagResolver;
    private final ObjectMapper objectMapper;
    private final ZhihuSearchClient zhihuSearchClient;
    private final ZhihuApiProperties zhihuApiProperties;
    private final ExternalResourceCleaningService externalResourceCleaningService;
    private final EvolutionAbilityTagCatalog abilityTagCatalog;
    private final EnterpriseChatLanguageModel enterpriseChatLanguageModel;
    /** 岗位演化 AI Agent（LangChain4j 可选 Bean，未启用时回退规则链路） */
    private final ObjectProvider<PostEvolutionAiService> aiServiceProvider;
    public List<RetrievalResult> retrieveIndustryEvidence(PostEvolutionAgentRequest request,
                                                            List<PostAbilityModel> currentAbilities) {
        List<String> keywords = buildKeywords(request, currentAbilities);
        String industry = request.getIndustry() != null ? request.getIndustry() : "信息技术";

        return knowledgeRetrievalService.retrieveIndustryTrends(industry, keywords, 20);
    }

    /**
     * 检索内部证据
     */
    public List<RetrievalResult> retrieveInternalEvidence(PostEvolutionAgentRequest request,
                                                            List<PostAbilityModel> currentAbilities) {
        List<String> keywords = buildKeywords(request, currentAbilities);
        String businessDomain = request.getBusinessDomain() != null ? request.getBusinessDomain() : "软件开发";

        return knowledgeRetrievalService.retrieveBusinessChanges(businessDomain, keywords, 20);
    }

    /** 市场发现提供的是受控原文证据线索，不是直接应用能力模型的指令。 */
    public List<RetrievalResult> retrieveMarketEvidence(PostEvolutionAgentRequest request,
                                                         List<PostAbilityModel> currentAbilities) {
        return knowledgeRetrievalService.retrieveMarketEvolutionClues(
                request.getPostId(),
                currentAbilities.stream().map(PostAbilityModel::getTagId).filter(Objects::nonNull).toList(),
                20);
    }

    /**
     * 知乎只提供外部趋势信号，经过清洗后转换为统一检索结果；不可用时静默降级，
     * 不影响岗位演化主链路。
     */
    public List<RetrievalResult> retrieveZhihuEvidence(PostEvolutionAgentRequest request,
                                                        List<PostAbilityModel> currentAbilities) {
        if (Boolean.FALSE.equals(request.getIncludeZhihu())) {
            return List.of();
        }
        String query = buildZhihuQuery(request, currentAbilities);
        if (query.isBlank()) return List.of();
        if (!zhihuApiProperties.isUsable()) return List.of();
        ZhihuSearchResponse response;
        try {
            response = zhihuSearchClient.search(query, 10);
        } catch (RuntimeException exception) {
            log.debug("知乎趋势检索降级: reason={}", exception.getClass().getSimpleName());
            return List.of();
        }
        List<ExternalTrendResourceDTO> raw = response == null || response.items() == null
                ? List.of()
                : response.items().stream().filter(Objects::nonNull).map(this::toExternalResource).toList();
        List<ExternalTrendResourceDTO> cleaned = externalResourceCleaningService.clean(raw).items();
        return cleaned.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.summary() != null && !item.summary().isBlank())
                .map(item -> {
                    String externalId = item.contentId() == null || item.contentId().isBlank()
                            ? String.valueOf(stableZhihuId(null, item.url(), item.title()))
                            : item.contentId().trim();
                    long zhihuId = stableZhihuId(externalId, item.url(), item.title());
                    return new RetrievalResult(
                        "ZHIHU:" + externalId,
                        item.summary(),
                        item.title(),
                        "ZHIHU_TREND",
                        SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_ZHIHU_TREND, zhihuId),
                        zhihuRelevance(item),
                        item.url());
                })
                .toList();
    }

    private long stableZhihuId(String contentId, String url, String title) {
        String value = contentId != null && !contentId.isBlank()
                ? contentId.trim()
                : (url != null && !url.isBlank() ? url : (title == null ? "zhihu" : title));
        return Integer.toUnsignedLong(value.hashCode());
    }

    private ExternalTrendResourceDTO toExternalResource(ZhihuSearchItem item) {
        return new ExternalTrendResourceDTO(item.title(), item.contentType(), item.contentId(), item.contentText(),
                item.url(), item.commentCount(), item.voteUpCount(), "ZHIHU_TREND", false, false);
    }

    private String buildZhihuQuery(PostEvolutionAgentRequest request, List<PostAbilityModel> currentAbilities) {
        List<String> parts = new ArrayList<>();
        if (request.getPostName() != null && !request.getPostName().isBlank()) parts.add(request.getPostName().trim());
        if (request.getIndustry() != null && !request.getIndustry().isBlank()) parts.add(request.getIndustry().trim());
        if (parts.isEmpty() && request.getBusinessDomain() != null && !request.getBusinessDomain().isBlank()) {
            parts.add(request.getBusinessDomain().trim());
        }
        return String.join(" ", parts);
    }

    private double zhihuRelevance(com.example.matching.dto.evolution.ExternalTrendResourceDTO item) {
        int votes = item.voteUpCount() == null ? 0 : Math.max(0, item.voteUpCount());
        int comments = item.commentCount() == null ? 0 : Math.max(0, item.commentCount());
        return Math.min(0.75D, 0.35D + Math.log1p(votes + comments * 2) / 20D);
    }

    /**
     * 构建检索关键词
     */
    public List<String> buildKeywords(PostEvolutionAgentRequest request, List<PostAbilityModel> currentAbilities) {
        List<String> keywords = new ArrayList<>();

        // 岗位名称
        if (request.getPostName() != null) {
            keywords.add(request.getPostName());
        }

        if (request.getIndustry() != null && !request.getIndustry().isBlank()) {
            keywords.add(request.getIndustry());
        }
        if (request.getBusinessDomain() != null && !request.getBusinessDomain().isBlank()) {
            keywords.add(request.getBusinessDomain());
        }

        Set<Long> tagIds = currentAbilities == null ? Set.of() : currentAbilities.stream()
                .map(PostAbilityModel::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!tagIds.isEmpty()) {
            abilityTagCatalog.activeTags().stream()
                    .filter(tag -> tag.getId() != null && tagIds.contains(tag.getId()))
                    .map(com.example.matching.entity.system.AbilityTag::getTagName)
                    .filter(name -> name != null && !name.isBlank())
                    .limit(30)
                    .forEach(keywords::add);
        }

        // 当前能力标签
        // Note: 这里简化处理，实际应该查询能力标签名称
        keywords.add("岗位要求");
        keywords.add("能力要求");
        keywords.add("技能要求");

        return keywords.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().limit(40).toList();
    }

    /**
     * 生成演化信号
     */
    public List<EvolutionSignal> generateSignals(PostEvolutionAgentRequest request,
                                                    List<RetrievalResult> industryEvidence,
                                                    List<RetrievalResult> internalEvidence,
                                                    List<RetrievalResult> marketEvidence) {
        List<EvolutionSignal> signals = new ArrayList<>();

        for (RetrievalResult result : industryEvidence) {
            if ("ABILITY_REQUIREMENT".equals(result.chunkType())) {
                createResolvedSignal("ABILITY_ADD", "ADD", result, 0.8D).ifPresent(signals::add);
            }
        }

        for (RetrievalResult result : internalEvidence) {
            if ("BUSINESS_CHANGE".equals(result.chunkType())) {
                createResolvedSignal("ABILITY_LEVEL_UP", "UPDATE", result, 0.9D).ifPresent(signals::add);
            }
        }

        for (RetrievalResult result : marketEvidence) {
            if ("MARKET_ABILITY_REQUIREMENT".equals(result.chunkType())) {
                createResolvedSignal("MARKET_ABILITY_DEMAND", "UPDATE", result, 0.85D).ifPresent(signals::add);
            }
        }

        return aggregateSignals(signals);
    }

    public List<EvolutionSignal> generateSignals(PostEvolutionAgentRequest request,
                                                    List<RetrievalResult> industryEvidence,
                                                    List<RetrievalResult> internalEvidence,
                                                    List<RetrievalResult> marketEvidence,
                                                    List<RetrievalResult> zhihuEvidence,
                                                    List<PostAbilityModel> currentAbilities) {
        List<EvolutionSignal> signals = new ArrayList<>(
                generateSignals(request, industryEvidence, internalEvidence, marketEvidence));
        for (RetrievalResult result : zhihuEvidence) {
            if ("ZHIHU_TREND".equals(result.chunkType())) {
                createZhihuSignal(result, currentAbilities).ifPresent(signals::add);
            }
        }
        return aggregateSignals(signals);
    }

    /**
     * Zhihu content is trend evidence and often does not contain an exact
     * catalog tag label. Keep it in the evolution signal stream by resolving
     * against the selected post's formal ability names first, then falling
     * back to the tag catalog. This prevents external trend evidence from
     * disappearing merely because tagId/tag wording is absent.
     */
    private Optional<EvolutionSignal> createZhihuSignal(RetrievalResult result,
                                                         List<PostAbilityModel> currentAbilities) {
        ResolvedEvolutionAbility resolved = abilityTagResolver.resolve(result.chunkText());
        if (resolved != null) {
            return Optional.of(new EvolutionSignal("ZHIHU_TREND", resolved.abilityName(), resolved.tagId(),
                    "UPDATE", result.chunkText(), List.of(result.sourceRef()), result.relevanceScore(),
                    result.relevanceScore() * 0.55D));
        }
        String text = ((result.sectionTitle() == null ? "" : result.sectionTitle()) + " "
                + (result.chunkText() == null ? "" : result.chunkText())).toLowerCase(Locale.ROOT);
        PostAbilityModel best = currentAbilities == null ? null : currentAbilities.stream()
                .filter(a -> a.getAbilityName() != null && !a.getAbilityName().isBlank())
                .filter(a -> text.contains(a.getAbilityName().trim().toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (best == null) {
            log.info("知乎趋势作为外部证据保留，但未命中岗位能力: sourceRef={}", result.sourceRef());
            return Optional.empty();
        }
        return Optional.of(new EvolutionSignal("ZHIHU_TREND", best.getAbilityName(), best.getTagId(),
                "UPDATE", result.chunkText(), List.of(result.sourceRef()), result.relevanceScore(),
                result.relevanceScore() * 0.55D));
    }


    /**
     * 生成变更建议（LLM 优先，规则兜底）
     * <p>
     * AI Agent 综合「岗位当前能力状态 + 检索证据片段」产出变更建议。
     * 防幻觉：建议必须引用输入证据列表中的真实编号；能力名必须出现在所引用证据片段中
     * （归一化匹配）；已有能力变更名必须匹配岗位能力表。LLM 未启用/调用失败/无有效建议时回退规则链路。
     */
    public ProposalGenerationOutcome generateAiProposals(PostEvolutionAgentRequest request,
                                                                  List<PostAbilityModel> currentAbilities,
                                                                  List<RetrievalResult> industryEvidence,
                                                                  List<RetrievalResult> internalEvidence,
                                                                  List<RetrievalResult> marketEvidence,
                                                                  List<RetrievalResult> zhihuEvidence) {
        List<RetrievalResult> allEvidence = new ArrayList<>();
        allEvidence.addAll(industryEvidence);
        allEvidence.addAll(internalEvidence);
        allEvidence.addAll(marketEvidence);
        allEvidence.addAll(zhihuEvidence);

        List<PostEvolutionChangeProposal> ruleProposals = ruleBasedProposals(request, currentAbilities,
                industryEvidence, internalEvidence, marketEvidence, zhihuEvidence);

        PostEvolutionAiService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null || allEvidence.isEmpty()) {
            log.warn("岗位演化 Agent 未执行: serviceAvailable={}, evidenceCount={}, fallback=RULE",
                    aiService != null, allEvidence.size());
            return ProposalGenerationOutcome.ruleFallback(ruleProposals, 0,
                    aiService == null ? "AI_UNAVAILABLE" : "NO_EVIDENCE");
        }
        try {
            String modelName = valueOrEmpty(enterpriseChatLanguageModel.getCurrentModelName());
            long startedAt = System.nanoTime();
            log.info("岗位演化 Agent 开始调用: model={}, evidenceCount={}, currentAbilityCount={}",
                    modelName, allEvidence.size(), currentAbilities == null ? 0 : currentAbilities.size());
            String context = buildAiContext(request, currentAbilities, allEvidence);
            PostEvolutionAiResult aiResult = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> aiService.analyze(context));
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int rawSuggestionCount = aiResult == null || aiResult.getSuggestions() == null
                    ? 0 : aiResult.getSuggestions().size();
            List<PostEvolutionChangeProposal> aiProposals =
                    validateAndConvertAiSuggestions(aiResult, allEvidence, currentAbilities);
            log.info("岗位演化 Agent 调用完成: model={}, elapsedMs={}, rawSuggestions={}, acceptedSuggestions={}",
                    modelName, elapsedMillis, rawSuggestionCount, aiProposals.size());
            if (!aiProposals.isEmpty()) {
                log.info("岗位演化 AI Agent 生成建议: ai={}, rule={}", aiProposals.size(), ruleProposals.size());
                return ProposalGenerationOutcome.withAi(mergeProposals(ruleProposals, aiProposals),
                        rawSuggestionCount, aiProposals.size(), ruleProposals.size());
            }
            log.warn("岗位演化 Agent 返回但无有效建议: ruleFallbackCount={}", ruleProposals.size());
            return ProposalGenerationOutcome.ruleFallback(ruleProposals, rawSuggestionCount, "AI_EMPTY_OR_REJECTED");
        } catch (Exception exception) {
            log.error("岗位演化 Agent 调用失败，使用规则链路: {}", exception.getMessage(), exception);
            return ProposalGenerationOutcome.ruleFallback(ruleProposals, 0, "AI_CALL_FAILED");
        }
    }

    public record ProposalGenerationOutcome(List<PostEvolutionChangeProposal> proposals,
                                            int aiRawSuggestionCount,
                                            int aiAcceptedSuggestionCount,
                                            int ruleProposalCount,
                                            boolean ruleFallback,
                                            String fallbackReason) {
        static ProposalGenerationOutcome withAi(List<PostEvolutionChangeProposal> proposals,
                                                int rawCount, int acceptedCount, int ruleCount) {
            return new ProposalGenerationOutcome(proposals, rawCount, acceptedCount, ruleCount, false, null);
        }

        static ProposalGenerationOutcome ruleFallback(List<PostEvolutionChangeProposal> proposals,
                                                      int rawCount, String reason) {
            return new ProposalGenerationOutcome(proposals, rawCount, 0, proposals.size(), true, reason);
        }
    }

    private List<PostEvolutionChangeProposal> ruleBasedProposals(PostEvolutionAgentRequest request,
                                                                  List<PostAbilityModel> currentAbilities,
                                                                  List<RetrievalResult> industryEvidence,
                                                                  List<RetrievalResult> internalEvidence,
                                                                  List<RetrievalResult> marketEvidence,
                                                                  List<RetrievalResult> zhihuEvidence) {
        List<EvolutionSignal> signals = generateSignals(request, industryEvidence, internalEvidence,
                marketEvidence, zhihuEvidence, currentAbilities);
        return compareWithCurrentModel(signals, currentAbilities);
    }

    private String buildAiContext(PostEvolutionAgentRequest request, List<PostAbilityModel> currentAbilities,
                                  List<RetrievalResult> allEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位名称：").append(valueOrEmpty(request.getPostName())).append("\n");
        sb.append("行业：").append(valueOrEmpty(request.getIndustry())).append("\n");
        sb.append("业务域：").append(valueOrEmpty(request.getBusinessDomain())).append("\n\n");
        sb.append("当前岗位能力状态：\n");
        if (currentAbilities == null || currentAbilities.isEmpty()) {
            sb.append("（无）\n");
        } else {
            for (PostAbilityModel m : currentAbilities) {
                sb.append("- ").append(valueOrEmpty(m.getAbilityName()))
                        .append("：等级").append(m.getMinRequiredLevel() == null ? "?" : m.getMinRequiredLevel())
                        .append("，权重").append(m.getWeight() == null ? "?" : m.getWeight())
                        .append("，").append(m.getIsCore() != null && m.getIsCore() == 1 ? "核心" : "普通")
                        .append("\n");
            }
        }
        sb.append("\n检索到的证据片段（编号、来源、内容，仅这些可作为参考）：\n");
        int idx = 0;
        for (RetrievalResult ev : allEvidence) {
            sb.append("[").append(idx++).append("] ")
                    .append(valueOrEmpty(ev.chunkType())).append("｜").append(valueOrEmpty(ev.sectionTitle()))
                    .append("\n").append(valueOrEmpty(ev.chunkText())).append("\n\n");
        }
        return sb.toString();
    }

    private List<PostEvolutionChangeProposal> validateAndConvertAiSuggestions(
            PostEvolutionAiResult aiResult, List<RetrievalResult> allEvidence,
            List<PostAbilityModel> currentAbilities) {
        List<PostEvolutionChangeProposal> proposals = new ArrayList<>();
        if (aiResult == null || aiResult.getSuggestions() == null) {
            log.warn("岗位演化 Agent 返回空建议: resultPresent={}, suggestionCount=0",
                    aiResult != null);
            return proposals;
        }
        log.info("岗位演化 Agent 建议数量: {}", aiResult.getSuggestions().size());
        for (PostEvolutionAiResult.ChangeSuggestion suggestion : aiResult.getSuggestions()) {
            PostEvolutionChangeProposal proposal = convertSuggestion(suggestion, allEvidence, currentAbilities);
            if (proposal != null) {
                proposals.add(proposal);
            }
        }
        return proposals;
    }

    private PostEvolutionChangeProposal convertSuggestion(PostEvolutionAiResult.ChangeSuggestion suggestion,
                                                          List<RetrievalResult> allEvidence,
                                                          List<PostAbilityModel> currentAbilities) {
        if (suggestion == null) {
            log.warn("岗位演化 AI 建议为空，丢弃: reason=NULL_SUGGESTION");
            return null;
        }
        String action = suggestion.getAction() == null ? "" : suggestion.getAction().trim().toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            log.warn("岗位演化 AI 建议动作为空，丢弃: reason=EMPTY_ACTION");
            return null;
        }
        String abilityName = suggestion.getAbilityName();
        if (abilityName == null || abilityName.isBlank()) {
            log.warn("岗位演化 AI 建议能力名为空，丢弃: reason=EMPTY_ABILITY");
            return null;
        }
        Integer ref = suggestion.getEvidenceRef();
        if (ref == null || ref < 0 || ref >= allEvidence.size()) {
            log.warn("AI 建议缺少有效证据引用，丢弃: ability={}, evidenceRef={}, reason=INVALID_EVIDENCE_REF",
                    abilityName, ref);
            return null;
        }
        RetrievalResult evidence = resolveEvidence(ref, abilityName, allEvidence);
        if (evidence == null || evidence.chunkText() == null || evidence.chunkText().isBlank()) {
            log.warn("AI 建议引用证据为空，丢弃: ability={}, evidenceRef={}, reason=EMPTY_EVIDENCE",
                    abilityName, ref);
            return null;
        }
        String normName = normalizeName(abilityName);
        PostAbilityModel existing = findAbilityByName(currentAbilities, normName);
        // 变更建议最终由人工审核。这里不再要求能力名逐字出现在证据中，
        // 只要求引用真实片段；这样可以保留语义表达、同义词和组合能力，避免 Agent 建议被全部拦截。
        if (normName.isBlank()) {
            log.warn("AI 建议能力名规范化后为空，丢弃: ability={}, evidenceRef={}, reason=EMPTY_NORMALIZED_ABILITY",
                    abilityName, ref);
            return null;
        }
        log.info("岗位演化 AI 建议通过基础校验: action={}, ability={}, evidenceRef={}, evidenceSource={}",
                action, abilityName, ref, evidence.sourceRef());
        int oldLevel = existing != null && existing.getMinRequiredLevel() != null ? existing.getMinRequiredLevel() : 3;
        BigDecimal oldWeight = existing != null && existing.getWeight() != null ? existing.getWeight() : BigDecimal.ZERO;
        int oldCore = existing != null && existing.getIsCore() != null ? existing.getIsCore() : 0;

        PostEvolutionChangeProposal proposal = new PostEvolutionChangeProposal();
        proposal.setAbilityName(abilityName);
        proposal.setEvidenceText(evidence.chunkText());
        proposal.setSourceRefs(List.of(evidence.sourceRef()));
        proposal.setReason(suggestion.getReason());
        double relevance = evidence.relevanceScore() == null ? 0.7D : evidence.relevanceScore();
        double confidence = Math.round(Math.max(0D, Math.min(1D, relevance)) * 10000D) / 100D;
        proposal.setConfidenceScore(confidence);
        proposal.setSupportScore(confidence);

        switch (action) {
            case "ADD" -> {
                if (existing != null) {
                    return null;
                }
                proposal.setChangeType("ADD");
                proposal.setOldLevel(0);
                proposal.setOldWeight(BigDecimal.ZERO);
                proposal.setOldIsCore(0);
                proposal.setNewLevel(clampLevel(suggestion.getNewLevel(), 2));
                proposal.setNewWeight(clampWeight(suggestion.getNewWeight(), new BigDecimal("20")));
                proposal.setNewIsCore(clampCore(suggestion.getNewIsCore(), 0));
            }
            case "UPDATE_LEVEL" -> {
                if (existing == null) {
                    return null;
                }
                proposal.setChangeType("UPDATE");
                proposal.setOldLevel(oldLevel);
                proposal.setNewLevel(clampLevel(suggestion.getNewLevel(), oldLevel));
                proposal.setOldWeight(oldWeight);
                proposal.setNewWeight(oldWeight);
                proposal.setOldIsCore(oldCore);
                proposal.setNewIsCore(oldCore);
            }
            case "UPDATE_WEIGHT" -> {
                if (existing == null) {
                    return null;
                }
                proposal.setChangeType("UPDATE");
                proposal.setOldLevel(oldLevel);
                proposal.setNewLevel(oldLevel);
                proposal.setOldWeight(oldWeight);
                proposal.setNewWeight(clampWeight(suggestion.getNewWeight(), oldWeight));
                proposal.setOldIsCore(oldCore);
                proposal.setNewIsCore(oldCore);
            }
            case "UPDATE_CORE" -> {
                if (existing == null) {
                    return null;
                }
                proposal.setChangeType("UPDATE");
                proposal.setOldLevel(oldLevel);
                proposal.setNewLevel(oldLevel);
                proposal.setOldWeight(oldWeight);
                proposal.setNewWeight(oldWeight);
                proposal.setOldIsCore(oldCore);
                proposal.setNewIsCore(clampCore(suggestion.getNewIsCore(), oldCore));
            }
            case "REMOVE" -> {
                if (existing == null) {
                    return null;
                }
                proposal.setChangeType("REMOVE");
                proposal.setOldLevel(oldLevel);
                proposal.setNewLevel(oldLevel);
                proposal.setOldWeight(oldWeight);
                proposal.setNewWeight(oldWeight);
                proposal.setOldIsCore(oldCore);
                proposal.setNewIsCore(oldCore);
            }
            default -> {
                log.warn("AI 建议动作未知，丢弃: action={}, ability={}", action, abilityName);
                return null;
            }
        }
        if ("UPDATE".equals(proposal.getChangeType()) && !hasEffectiveChange(proposal)) {
            log.info("岗位演化 AI 建议无实际数值变化，跳过落库: action={}, ability={}, level={}=>{}, weight={}=>{}, core={}=>{}",
                    action, abilityName, proposal.getOldLevel(), proposal.getNewLevel(),
                    proposal.getOldWeight(), proposal.getNewWeight(), proposal.getOldIsCore(), proposal.getNewIsCore());
            return null;
        }
        return proposal;
    }

    /**
     * 优先使用模型声明的 0-based 编号；当模型误用 1-based 编号或引用编号与能力不一致时，
     * 在同一批真实证据中寻找能支撑该能力的片段并重新绑定，绝不创建新的证据文本。
     */
    private RetrievalResult resolveEvidence(Integer ref, String abilityName, List<RetrievalResult> allEvidence) {
        RetrievalResult primary = allEvidence.get(ref);
        if (evidenceSupportsAbility(normalizeName(abilityName), normalizeName(primary.chunkText()))) {
            return primary;
        }
        if (ref > 0) {
            RetrievalResult oneBased = allEvidence.get(ref - 1);
            if (evidenceSupportsAbility(normalizeName(abilityName), normalizeName(oneBased.chunkText()))) {
                log.info("岗位演化 AI 证据编号按 1-based 兼容修正: ability={}, evidenceRef={} -> {}",
                        abilityName, ref, ref - 1);
                return oneBased;
            }
        }
        // 证据编号合法但能力名不逐字出现时，仍使用该真实片段交由人工审核。
        return primary;
    }

    private boolean evidenceSupportsAbility(String normalizedName, String normalizedEvidence) {
        if (normalizedName.isBlank() || normalizedEvidence.isBlank()) return false;
        if (normalizedEvidence.contains(normalizedName)) return true;
        String[] tokens = normalizedName.split("[^\\p{L}\\p{N}]+|(?<=[a-z])(?=[A-Z])");
        int meaningful = 0;
        int matched = 0;
        for (String token : tokens) {
            if (token.length() < 2) continue;
            meaningful++;
            if (normalizedEvidence.contains(token.toLowerCase(Locale.ROOT))) matched++;
        }
        return meaningful > 0 && matched >= Math.max(1, (meaningful + 1) / 2);
    }

    private List<PostEvolutionChangeProposal> mergeProposals(
            List<PostEvolutionChangeProposal> ruleProposals,
            List<PostEvolutionChangeProposal> aiProposals) {
        Map<String, PostEvolutionChangeProposal> merged = new LinkedHashMap<>();
        for (PostEvolutionChangeProposal proposal : ruleProposals) merged.put(proposalKey(proposal), proposal);
        for (PostEvolutionChangeProposal proposal : aiProposals) {
            String key = proposalKey(proposal);
            PostEvolutionChangeProposal existing = merged.get(key);
            if (existing == null || (proposal.getReason() != null && !proposal.getReason().isBlank())) {
                merged.put(key, proposal);
            }
        }
        return List.copyOf(merged.values());
    }

    private String proposalKey(PostEvolutionChangeProposal proposal) {
        return normalizeName(proposal.getAbilityName()) + "|" +
                (proposal.getChangeType() == null ? "" : proposal.getChangeType().toUpperCase(Locale.ROOT));
    }

    private PostAbilityModel findAbilityByName(List<PostAbilityModel> abilities, String normalizedName) {
        if (abilities == null) {
            return null;
        }
        for (PostAbilityModel m : abilities) {
            if (normalizeName(m.getAbilityName()).equals(normalizedName)) {
                return m;
            }
        }
        return null;
    }

    private String normalizeName(String text) {
        return AbilityNameNormalizer.normalize(text);
    }

    private String valueOrEmpty(String text) {
        return text == null ? "" : text;
    }

    private int clampLevel(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(1, Math.min(5, value));
    }

    private BigDecimal clampWeight(BigDecimal value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(new BigDecimal("100"));
    }

    private int clampCore(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return value == 1 ? 1 : 0;
    }

    private List<EvolutionSignal> aggregateSignals(List<EvolutionSignal> signals) {
        if (signals == null || signals.isEmpty()) return List.of();
        Map<String, EvolutionSignal> grouped = new LinkedHashMap<>();
        for (EvolutionSignal signal : signals) {
            String key = signal.abilityTagId() != null
                    ? "tag:" + signal.abilityTagId()
                    : "name:" + (signal.abilityName() == null ? "" : signal.abilityName().trim().toLowerCase(Locale.ROOT));
            EvolutionSignal previous = grouped.get(key);
            if (previous == null) {
                grouped.put(key, signal);
                continue;
            }
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            refs.addAll(previous.sourceRefs() == null ? List.of() : previous.sourceRefs());
            refs.addAll(signal.sourceRefs() == null ? List.of() : signal.sourceRefs());
            String evidence = previous.evidenceText();
            if (signal.evidenceText() != null && !signal.evidenceText().isBlank()
                    && !signal.evidenceText().equals(previous.evidenceText())) {
                evidence = (evidence == null || evidence.isBlank())
                        ? signal.evidenceText() : evidence + "\n\n" + signal.evidenceText();
            }
            double confidence = Math.max(valueOrZero(previous.confidenceScore()), valueOrZero(signal.confidenceScore()));
            double support = Math.min(1D, valueOrZero(previous.supportScore()) + valueOrZero(signal.supportScore()));
            String changeType = "UPDATE".equals(signal.changeType()) || "UPDATE".equals(previous.changeType())
                    ? "UPDATE" : previous.changeType();
            String signalType = valueOrZero(signal.supportScore()) >= valueOrZero(previous.supportScore())
                    ? signal.signalType() : previous.signalType();
            grouped.put(key, new EvolutionSignal(signalType, previous.abilityName(), previous.abilityTagId(),
                    changeType, evidence, List.copyOf(refs), confidence, support));
        }
        return List.copyOf(grouped.values());
    }

    private double valueOrZero(Double value) {
        return value == null ? 0D : value;
    }

    public Optional<EvolutionSignal> createResolvedSignal(String signalType, String changeType,
                                                            RetrievalResult result, double supportMultiplier) {
        ResolvedEvolutionAbility ability = abilityTagResolver.resolve(result.chunkText());
        if (ability == null) {
            log.debug("Skipping evolution evidence without a matched ability tag: sourceRef={}", result.sourceRef());
            return Optional.empty();
        }
        return Optional.of(new EvolutionSignal(
                signalType,
                ability.abilityName(),
                ability.tagId(),
                changeType,
                result.chunkText(),
                List.of(result.sourceRef()),
                result.relevanceScore(),
                result.relevanceScore() * supportMultiplier
        ));
    }

    /**
     * 与当前能力模型对比，生成变更建议
     */
    public List<PostEvolutionChangeProposal> compareWithCurrentModel(List<EvolutionSignal> signals,
                                                                       List<PostAbilityModel> currentAbilities) {
        List<PostEvolutionChangeProposal> proposals = new ArrayList<>();

        // 构建当前能力映射
        Map<Long, PostAbilityModel> currentMap = currentAbilities.stream()
                // Untagged role abilities are valid profile facts but have no canonical identity
                // for agent signal comparison.
                .filter(model -> model.getTagId() != null)
                .collect(Collectors.toMap(PostAbilityModel::getTagId, m -> m, (a, b) -> a));

        for (EvolutionSignal signal : signals) {
            PostEvolutionChangeProposal proposal = new PostEvolutionChangeProposal();
            proposal.setSignalType(signal.signalType());
            proposal.setAbilityName(signal.abilityName());
            proposal.setAbilityTagId(signal.abilityTagId());
            proposal.setChangeType(signal.changeType());
            proposal.setEvidenceText(signal.evidenceText());
            proposal.setSourceRefs(signal.sourceRefs());
            proposal.setConfidenceScore(toPercent(signal.confidenceScore()));
            proposal.setSupportScore(toPercent(signal.supportScore()));

            // 与当前模型对比
            if (signal.abilityTagId() != null && currentMap.containsKey(signal.abilityTagId())) {
                PostAbilityModel current = currentMap.get(signal.abilityTagId());
                proposal.setOldLevel(current.getMinRequiredLevel());
                proposal.setOldWeight(current.getWeight());
                proposal.setOldIsCore(current.getIsCore());

                // 根据信号类型设置新值
                switch (signal.signalType()) {
                    case "ABILITY_LEVEL_UP":
                        proposal.setNewLevel(current.getMinRequiredLevel() + 1);
                        proposal.setNewWeight(current.getWeight());
                        proposal.setNewIsCore(current.getIsCore());
                        proposal.setChangeType("UPDATE");
                        break;
                    case "ABILITY_WEIGHT_UP":
                        proposal.setNewLevel(current.getMinRequiredLevel());
                        proposal.setNewWeight(current.getWeight().add(BigDecimal.TEN));
                        proposal.setNewIsCore(current.getIsCore());
                        proposal.setChangeType("UPDATE");
                        break;
                    case "ABILITY_CORE_CHANGE":
                        proposal.setNewLevel(current.getMinRequiredLevel());
                        proposal.setNewWeight(current.getWeight());
                        proposal.setNewIsCore(1);
                        proposal.setChangeType("UPDATE");
                        break;
                    default:
                        proposal.setNewLevel(current.getMinRequiredLevel());
                        proposal.setNewWeight(current.getWeight());
                        proposal.setNewIsCore(current.getIsCore());
                }
            } else {
                // 新增能力
                proposal.setOldLevel(0);
                proposal.setOldWeight(BigDecimal.ZERO);
                proposal.setOldIsCore(0);
                proposal.setNewLevel(2);
                proposal.setNewWeight(BigDecimal.valueOf(20));
                proposal.setNewIsCore(0);
                proposal.setChangeType("ADD");
            }

            if (signal.abilityTagId() != null && currentMap.containsKey(signal.abilityTagId())
                    && !hasEffectiveChange(proposal)) {
                continue;
            }

            // 设置风险等级
            proposal.setRiskLevel(calculateRiskLevel(proposal));

            proposals.add(proposal);
        }

        return proposals;
    }

    private boolean hasEffectiveChange(PostEvolutionChangeProposal proposal) {
        return !Objects.equals(proposal.getOldLevel(), proposal.getNewLevel())
                || !Objects.equals(proposal.getOldWeight(), proposal.getNewWeight())
                || !Objects.equals(proposal.getOldIsCore(), proposal.getNewIsCore());
    }

    /**
     * 计算风险等级
     */
    public String calculateRiskLevel(PostEvolutionChangeProposal proposal) {
        if (proposal.getConfidenceScore() == null) {
            return "HIGH";
        }

        if (proposal.getConfidenceScore() >= 80) {
            return "LOW";
        } else if (proposal.getConfidenceScore() >= 60) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    private double toPercent(Double value) {
        if (value == null) {
            return 0D;
        }
        double normalized = value <= 1D ? value * 100D : value;
        return Math.round(Math.max(0D, Math.min(100D, normalized)) * 100D) / 100D;
    }

    /**
     * 创建证据列表
     */
    public List<PostEvolutionEvidence> createEvidences(List<RetrievalResult> industryEvidence,
                                                         List<RetrievalResult> internalEvidence,
                                                         List<RetrievalResult> marketEvidence) {
        List<PostEvolutionEvidence> evidences = new ArrayList<>();

        // 行业证据
        for (RetrievalResult result : industryEvidence) {
            PostEvolutionEvidence evidence = new PostEvolutionEvidence();
            evidence.setSourceType("INDUSTRY_WHITEPAPER");
            evidence.setEvidenceText(result.chunkText());
            evidence.setSourceRef(result.sourceRef());
            evidence.setSimilarityScore(BigDecimal.valueOf(result.relevanceScore()));
            evidence.setTrustScore(BigDecimal.valueOf(80));
            evidence.setCollectedTime(LocalDateTime.now());
            evidences.add(evidence);
        }

        // 内部证据
        for (RetrievalResult result : internalEvidence) {
            PostEvolutionEvidence evidence = new PostEvolutionEvidence();
            evidence.setSourceType("CLOUD_KNOWLEDGE_INTERNAL");
            evidence.setEvidenceText(result.chunkText());
            evidence.setSourceRef(result.sourceRef());
            evidence.setSimilarityScore(BigDecimal.valueOf(result.relevanceScore()));
            evidence.setTrustScore(BigDecimal.valueOf(85));
            evidence.setCollectedTime(LocalDateTime.now());
            evidences.add(evidence);
        }

        for (RetrievalResult result : marketEvidence) {
            PostEvolutionEvidence evidence = new PostEvolutionEvidence();
            evidence.setSourceType("MARKET_JD");
            evidence.setSourceTitle(result.sectionTitle());
            evidence.setEvidenceText(result.chunkText());
            evidence.setSourceRef(result.sourceRef());
            evidence.setSimilarityScore(BigDecimal.valueOf(result.relevanceScore()));
            evidence.setTrustScore(BigDecimal.valueOf(75));
            evidence.setCollectedTime(LocalDateTime.now());
            evidences.add(evidence);
        }

        return evidences;
    }

    public List<PostEvolutionEvidence> createEvidencesWithZhihu(List<RetrievalResult> industryEvidence,
                                                                  List<RetrievalResult> internalEvidence,
                                                                  List<RetrievalResult> marketEvidence,
                                                                  List<RetrievalResult> zhihuEvidence) {
        List<PostEvolutionEvidence> evidences = new ArrayList<>(createEvidences(industryEvidence, internalEvidence, marketEvidence));
        for (RetrievalResult result : zhihuEvidence) {
            PostEvolutionEvidence evidence = new PostEvolutionEvidence();
            evidence.setSourceType("ZHIHU_TREND");
            evidence.setSourceTitle(result.sectionTitle());
            evidence.setSourceUrl(result.sourceUrl() != null && !result.sourceUrl().isBlank()
                    ? result.sourceUrl() : result.sourceRef());
            evidence.setEvidenceText(result.chunkText());
            evidence.setSourceRef(result.sourceRef());
            evidence.setSimilarityScore(BigDecimal.valueOf(result.relevanceScore() == null ? 0D : result.relevanceScore()));
            evidence.setSourceWeight(BigDecimal.valueOf(0.45D));
            evidence.setTrustScore(BigDecimal.valueOf(0.55D));
            evidence.setCollectedTime(LocalDateTime.now());
            evidences.add(evidence);
        }

        return evidences;
    }

    /**
     * 保存变更项到数据库
     */
    public int saveChangeItems(Long taskId, List<PostEvolutionChangeProposal> proposals,
                               List<PostEvolutionEvidence> persistedEvidences) {
        if (proposals == null || proposals.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (PostEvolutionChangeProposal proposal : proposals) {
            // 跳过 BLOCK 的变更
            if ("BLOCK".equals(proposal.getHarnessDecision())) {
                continue;
            }

            List<PostEvolutionEvidence> relatedEvidences = findRelatedEvidences(proposal, persistedEvidences);
            if (relatedEvidences.isEmpty()) {
                log.warn("岗位演化变更没有关联的真实证据，不创建审核项: taskId={}, ability={}",
                        taskId, proposal.getAbilityName());
                continue;
            }

            com.example.matching.entity.evolution.PostEvolutionChangeItem item =
                    new com.example.matching.entity.evolution.PostEvolutionChangeItem();
            item.setTaskId(taskId);
            item.setChangeType(mapChangeType(proposal));
            item.setTagId(proposal.getAbilityTagId());
            item.setAbilityName(proposal.getAbilityName());
            item.setOldLevel(proposal.getOldLevel());
            item.setNewLevel(proposal.getNewLevel());
            item.setOldWeight(proposal.getOldWeight());
            item.setNewWeight(proposal.getNewWeight());
            item.setOldIsCore(proposal.getOldIsCore());
            item.setNewIsCore(proposal.getNewIsCore());
            item.setEvidenceText(proposal.getEvidenceText());
            item.setConfidenceScore(BigDecimal.valueOf(proposal.getConfidenceScore() != null ? proposal.getConfidenceScore() : 0));
            item.setSupportScore(BigDecimal.valueOf(proposal.getSupportScore() != null ? proposal.getSupportScore() : 0));
            item.setHarnessDecision(proposal.getHarnessDecision());
            item.setRiskLevel(proposal.getRiskLevel());
            item.setConfirmStatus("PENDING");
            item.setSourceType("AGENT");

            if (proposal.getSourceRefs() != null) {
                try {
                    item.setSourceRefsJson(objectMapper.writeValueAsString(proposal.getSourceRefs()));
                } catch (Exception e) {
                    log.warn("序列化 sourceRefs 失败", e);
                }
            }

            // 保存到数据库
            changeItemMapper.insert(item);
            for (PostEvolutionEvidence evidence : relatedEvidences) {
                evidence.setChangeItemId(item.getId());
                evidenceMapper.updateById(evidence);
            }
            count++;
        }

        return count;
    }

    private List<PostEvolutionEvidence> findRelatedEvidences(PostEvolutionChangeProposal proposal,
                                                               List<PostEvolutionEvidence> persistedEvidences) {
        if (persistedEvidences == null || persistedEvidences.isEmpty()) {
            return List.of();
        }
        Set<String> sourceRefs = proposal.getSourceRefs() == null ? Set.of() : proposal.getSourceRefs().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(ref -> !ref.isEmpty())
                .collect(Collectors.toSet());
        return persistedEvidences.stream()
                .filter(Objects::nonNull)
                .filter(evidence -> evidence.getEvidenceText() != null && !evidence.getEvidenceText().isBlank())
                .filter(evidence -> sourceRefs.contains(normalizeEvidenceKey(evidence.getSourceRef()))
                        || overlapsEvidence(proposal.getEvidenceText(), evidence.getEvidenceText()))
                .toList();
    }

    private boolean overlapsEvidence(String proposalEvidence, String persistedEvidence) {
        if (proposalEvidence == null || proposalEvidence.isBlank()
                || persistedEvidence == null || persistedEvidence.isBlank()) {
            return false;
        }
        return proposalEvidence.contains(persistedEvidence) || persistedEvidence.contains(proposalEvidence);
    }

    private String normalizeEvidenceKey(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 映射变更类型
     * <p>
     * UPDATE 按实际变化的新值精确映射为 UPDATED_LEVEL / UPDATED_WEIGHT / UPDATED_CORE，
     * 避免权重/核心变更在应用时丢失。
     */
    public String mapChangeType(PostEvolutionChangeProposal proposal) {
        String changeType = proposal.getChangeType();
        if (changeType == null) {
            return "ADDED";
        }
        switch (changeType) {
            case "ADD":
                return "ADDED";
            case "REMOVE":
                return "REMOVED";
            case "UPDATE":
                if (proposal.getNewLevel() != null && !proposal.getNewLevel().equals(proposal.getOldLevel())) {
                    return "UPDATED_LEVEL";
                }
                if (proposal.getNewWeight() != null && proposal.getOldWeight() != null
                        && proposal.getNewWeight().compareTo(proposal.getOldWeight()) != 0) {
                    return "UPDATED_WEIGHT";
                }
                if (proposal.getNewIsCore() != null && !proposal.getNewIsCore().equals(proposal.getOldIsCore())) {
                    return "UPDATED_CORE";
                }
                return "UPDATED_LEVEL";
            default:
                return changeType;
        }
    }

    /**
     * 生成摘要
     */
    public String generateSummary(PostEvolutionAgentResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位演化分析完成。");
        sb.append("生成信号 ").append(result.getSignals() != null ? result.getSignals().size() : 0).append(" 个，");
        sb.append("变更建议 ").append(result.getProposals() != null ? result.getProposals().size() : 0).append(" 个。");

        if (result.getHarnessSummary() != null) {
            sb.append("Harness 校验：通过 ").append(result.getHarnessSummary().getPass());
            sb.append("，待审 ").append(result.getHarnessSummary().getReview());
            sb.append("，拒绝 ").append(result.getHarnessSummary().getBlock()).append("。");
        }

        return sb.toString();
    }
}
