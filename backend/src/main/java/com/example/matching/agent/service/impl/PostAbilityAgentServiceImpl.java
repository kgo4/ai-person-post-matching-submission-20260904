package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.PostAbilityAgentRequest;
import com.example.matching.agent.dto.PostAbilityAgentResult;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.lc4j.PostAbilityAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackConfidencePolicy;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.application.agent.PostRequirementSnapshot;
import com.example.matching.infrastructure.llm.JsonNodeValueReader;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 岗位能力Agent服务实现
 * <p>
 * 职责：
 * - 解释岗位能力模型
 * - 说明核心能力和权重
 * - 发现岗位模型风险
 * - 给出优化建议
 * <p>
 * 注意：只读不写，不直接修改PostAbilityModel
 *
 * @author system
 */
@Slf4j
@Service
public class PostAbilityAgentServiceImpl extends AbstractAgentService implements PostAbilityAgentService {

    private final LangChain4jAgentProperties properties;
    private final AgentContextPackageService contextPackageService;
    private final AgentFallbackService fallbackService;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser llmResponseParser;
    private final AgentFallbackConfidencePolicy fallbackConfidencePolicy = new AgentFallbackConfidencePolicy();
    private final AgentMemoryContextService memoryContextService;
    private final AgentMemoryRuleEnforcer memoryRuleEnforcer;
    private final com.example.matching.ai.validation.PostAbilityExtractionValidator extractionValidator;

    private final PostAbilityAiService postAbilityAiService;

    /** Task10：可选注入的结构化指标（测试环境无 MeterRegistry 时为空） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.agent.config.ExtractionMetrics extractionMetrics;

    public PostAbilityAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            AgentFallbackService fallbackService,
            ObjectMapper objectMapper,
            LlmResponseParser llmResponseParser,
            AgentRunConfidencePolicy confidencePolicy,
            AgentMemoryContextService memoryContextService,
            AgentMemoryRuleEnforcer memoryRuleEnforcer,
            com.example.matching.ai.validation.PostAbilityExtractionValidator extractionValidator,
            ObjectProvider<PostAbilityAiService> aiServiceProvider) {
        super(confidencePolicy);
        this.properties = properties;
        this.contextPackageService = contextPackageService;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
        this.llmResponseParser = llmResponseParser;
        this.memoryContextService = memoryContextService;
        this.memoryRuleEnforcer = memoryRuleEnforcer;
        this.extractionValidator = extractionValidator;
        this.postAbilityAiService = aiServiceProvider.getIfAvailable();
    }

    @Override
    public PostAbilityAgentResult analyze(PostAbilityAgentRequest request) {
        AgentContextPackage context = contextPackageService.buildForPost(request.getPostId());

        if (!properties.isEnabled() || postAbilityAiService == null) {
            log.info("LangChain4j未启用或PostAbilityAiService未注册，使用降级方案");
            return fallbackPostAnalysis(context);
        }

        return runWithFallback(
                () -> {
                    String contextJson = objectMapper.writeValueAsString(context);
                    log.info("岗位能力Agent(LangChain4j)分析: postId={}", request.getPostId());
                    PostAbilityAgentResult result = com.example.matching.agent.config.AgentToolProvider
                            .withScope(() -> postAbilityAiService.analyze(contextJson));
                    if (result == null) {
                        throw new IllegalStateException("Post ability analysis returned no structured result");
                    }
                    String serializedResult = objectMapper.writeValueAsString(result);
                    return finalizeRun(result, context.getSourceRefs(), false, serializedResult);
                },
                e -> {
                    log.error("岗位能力Agent LangChain4j调用失败，使用降级方案", e);
                    return fallbackPostAnalysis(context);
                });
    }

    /**
     * 降级方案：基于上下文数据生成基础分析
     */
    private PostAbilityAgentResult fallbackPostAnalysis(AgentContextPackage context) {
        PostAbilityAgentResult result = new PostAbilityAgentResult();
        result.setFallbackUsed(true);

        if (context == null) {
            result.setModelSummary("无法获取岗位上下文数据");
            result.setCoreAbilities(Collections.emptyList());
            result.setWeightRisks(Collections.emptyList());
            result.setMissingAbilities(Collections.emptyList());
            result.setSuggestions(Collections.emptyList());
            return result;
        }

        List<PostRequirementSnapshot> requirements = context.getPostRequirements();

        StringBuilder summary = new StringBuilder();
        summary.append("岗位能力模型分析");

        if (requirements != null && !requirements.isEmpty()) {
            summary.append("，共").append(requirements.size()).append("项能力要求");
        }

        result.setModelSummary(summary.toString());

        result.setCoreAbilities(new ArrayList<>());
        if (requirements != null) {
            for (PostRequirementSnapshot req : requirements) {
                PostAbilityAgentResult.AbilityItem item = new PostAbilityAgentResult.AbilityItem();
                item.setAbilityTagId(req.abilityTagId());
                item.setAbilityName(req.abilityName());
                item.setRequiredLevel(req.requiredLevel());
                item.setWeight(req.weight() != null ? req.weight().intValue() : null);
                item.setCore(req.core());

                if (req.core()) {
                    result.getCoreAbilities().add(item);
                }
            }
        }

        result.setWeightRisks(new ArrayList<>());
        if (requirements != null) {
            for (PostRequirementSnapshot req : requirements) {
                if (req.weight() != null && req.weight().compareTo(new BigDecimal("30")) > 0) {
                    PostAbilityAgentResult.WeightRiskItem risk = new PostAbilityAgentResult.WeightRiskItem();
                    risk.setAbilityTagId(req.abilityTagId());
                    risk.setAbilityName(req.abilityName());
                    risk.setCurrentWeight(req.weight().intValue());
                    risk.setRiskType("HIGH_WEIGHT");
                    risk.setRiskDescription("权重过高，可能导致匹配结果过度依赖该能力");
                    result.getWeightRisks().add(risk);
                }
            }
        }

        result.setMissingAbilities(new ArrayList<>());

        result.setSuggestions(new ArrayList<>());
        if (result.getCoreAbilities().isEmpty()) {
            result.getSuggestions().add("建议定义核心能力项");
        }
        if (!result.getWeightRisks().isEmpty()) {
            result.getSuggestions().add("建议调整" + result.getWeightRisks().size() + "项权重过高的能力");
        }

        return finalizeRun(result, context.getSourceRefs(), true, null);
    }

    @Override
    public PostAbilityExtractionResult extractAbilities(PostAbilityExtractRequest request) {
        log.info("提取岗位能力: postId={}, sourceType={}, sourceRefId={}", request.getPostId(), request.getSourceType(), request.getSourceRefId());

        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setPostId(request.getPostId());
        result.setSourceType(request.getSourceType());
        result.setSourceRefId(request.getSourceRefId());

        String rawContextText = buildRawContextText(request);
        AgentMemoryContextService.ContextRules contextRules = memoryContextService.resolveRules(
                rawContextText, AgentMemoryContextService.SCOPE_POST);

        long startTime = System.currentTimeMillis();

        try {
            if (!properties.isEnabled() || postAbilityAiService == null) {
                log.info("LangChain4j未启用或PostAbilityAiService未注册，岗位提取待重试/人工复核");
                markExtractionUnavailable(result, "AI 服务未启用或不可用");
            } else {
                try {
                    List<com.example.matching.agent.service.ExtractionChunker.Chunk> chunks =
                            com.example.matching.agent.service.ExtractionChunker.chunk(
                                    request.getSourceText(), com.example.matching.agent.service.ExtractionChunker.DEFAULT_MAX_CHARS);
                    if (chunks.size() > 1) {
                        // Task8：长文本分块提取（逐块调用、合并、按规范化名称去重；失败块标记 RETRY/REVIEW）
                        result.setClaims(extractChunkedClaims(request, contextRules, chunks, result, startTime));
                        result.setFallbackUsed(false);
                        result.setSummary(result.getSummary() != null ? result.getSummary() : "AI提取完成");
                        return result;
                    }
                    String contextJson = buildExtractContextJsonWithMemory(request, contextRules);
                    PostAbilityExtractionResult aiResult = com.example.matching.agent.config.AgentToolProvider
                            .withScope(() -> postAbilityAiService.extractAbilities(contextJson));
                    if (aiResult == null) {
                        throw new IllegalStateException("Post ability extraction returned no structured result");
                    }
                    // rawModelOutput: 序列化后的结构化提取结果（parsed structured result，非 raw LLM completion）
                    String serializedResult = objectMapper.writeValueAsString(aiResult);
                    result.setRawModelOutput(serializedResult);
                    memoryRuleEnforcer.auditGuidanceResponse(contextRules, serializedResult,
                            "POST_ABILITY_EXTRACTION", request.getSourceType(),
                            request.getSourceRefId(), rawContextText);

                    // 校验器：数值字段、原文证据定位、受控引用集合、同批次去重
                    // （位置：normalize 之后；失败返回空 claims + fallbackUsed，不得用旧模型冒充新 JD 提取结果）
                    normalizeExtractedClaims(aiResult.getClaims(), request);
                    var individualValidation = extractionValidator.validateIndividually(
                            aiResult.getClaims(), trustedSourceText(request), buildServerRefs(request));
                    if (!individualValidation.acceptedClaims().isEmpty()) {
                        if (!individualValidation.rejectedClaims().isEmpty()) {
                            log.warn("[AI_OUTPUT_PARTIAL] 岗位能力提取丢弃无效项: postId={}, rejected={}",
                                    request.getPostId(), individualValidation.rejectedClaims().size());
                            individualValidation.rejectedClaims().forEach(rejected ->
                                    recordValidationFailure(rejected.reason()));
                        }
                        aiResult.setClaims(new java.util.ArrayList<>(individualValidation.acceptedClaims()));
                        // Keep legacy de-duplication and its public contract after filtering.
                        extractionValidator.validateAgainstTrustedSource(
                                aiResult, trustedSourceText(request), buildServerRefs(request));
                    } else {
                        String validationMessage = individualValidation.rejectedClaims().isEmpty()
                                ? "能力声明列表为空"
                                : individualValidation.rejectedClaims().get(0).reason();
                        recordValidationFailure(validationMessage);
                        log.warn("[AI_OUTPUT_INVALID] 岗位能力提取结果不合法，要求 Agent 修复一次: {}", validationMessage);
                        try {
                            aiResult = com.example.matching.agent.config.AgentToolProvider.withScope(
                                    () -> postAbilityAiService.extractAbilities(
                                            buildEvidenceRepairContextJson(request, contextRules, validationMessage)));
                            if (aiResult == null) {
                                throw new IllegalStateException("Post ability extraction repair returned no structured result");
                            }
                            normalizeExtractedClaims(aiResult.getClaims(), request);
                            extractionValidator.validateAgainstTrustedSource(
                                    aiResult, trustedSourceText(request), buildServerRefs(request));
                            result.setRawModelOutput(objectMapper.writeValueAsString(aiResult));
                        } catch (com.example.matching.ai.validation.AiOutputValidationException repairError) {
                            recordValidationFailure(repairError.getMessage());
                            log.warn("[AI_OUTPUT_INVALID] 岗位能力提取修复后仍不合法: {}", repairError.getMessage());
                            result.setFallbackUsed(true);
                            result.setClaims(new java.util.ArrayList<>());
                            result.setSummary("提取失败：AI 输出校验未通过（" + repairError.getMessage() + "）");
                            result.setDurationMs(System.currentTimeMillis() - startTime);
                            return result;
                        }
                    }
                    result.setClaims(aiResult.getClaims());
                    result.setFallbackUsed(false);
                    result.setSummary(aiResult.getSummary() != null ? aiResult.getSummary() : "AI提取完成");
                } catch (Exception e) {
                    log.error("岗位能力Agent LangChain4j调用失败，岗位提取待重试/人工复核", e);
                    markExtractionUnavailable(result, "AI 调用失败");
                }
            }

            if (contextRules.hardRules() != null && !contextRules.hardRules().isEmpty()) {
                AgentMemoryRuleEnforcer.EnforcementResult enforced = memoryRuleEnforcer.enforcePost(
                        result, contextRules, request.getSourceType(), request.getSourceRefId(), rawContextText);
                result.setClaims(enforced.claimsAs());
            }
        } catch (Exception e) {
            log.error("提取岗位能力失败", e);
            result.setFallbackUsed(true);
            result.setClaims(Collections.emptyList());
            result.setSummary("提取失败: " + e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        // Task10：提取日志只含统计与来源，不记录岗位全文或敏感证据
        log.info("岗位能力提取完成: postId={}, sourceType={}, sourceRefId={}, claimCount={}, fallbackUsed={}, failedChunks={}",
                request.getPostId(), request.getSourceType(), request.getSourceRefId(),
                result.getClaims() != null ? result.getClaims().size() : 0,
                result.isFallbackUsed(), result.getFailedChunkCount());
        return result;
    }

    /** Task10：按失败原因分类记录结构化指标。 */
    private void recordValidationFailure(String message) {
        if (extractionMetrics == null || message == null) {
            return;
        }
        if (message.contains("无法在 sourceText 中定位")) {
            extractionMetrics.evidenceNotLocatable(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_POST);
        } else if (message.contains("sourceRefs")) {
            extractionMetrics.sourceRefInvalid(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_POST);
        } else {
            extractionMetrics.validationFailed(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_POST);
        }
    }

    private String buildExtractContextJsonWithMemory(PostAbilityExtractRequest request,
                                                      AgentMemoryContextService.ContextRules contextRules) {
        try {
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("request", request);

            if (contextRules.guidancePrompt() != null && !contextRules.guidancePrompt().isBlank()) {
                context.put("governanceGuidance", contextRules.guidancePrompt());
            }
            if (contextRules.hardRuleSummary() != null && !contextRules.hardRuleSummary().isBlank()) {
                context.put("hardRuleSummary", contextRules.hardRuleSummary());
            }

            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.error("构建提取上下文失败", e);
            return "{}";
        }
    }

    private String buildRawContextText(PostAbilityExtractRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return request.getPostId() + " " + request.getSourceType();
        }
    }

    /**
     * Task8：长文本分块提取。逐块调用 AI 并校验（normalize 后校验，位置与单块一致）；
     * 失败块仅标记 RETRY/REVIEW，不把旧事实复制成新提取结果；
     * 合并后按规范化名称去重并保留证据偏移（已修正为全量偏移）。
     */
    private List<PostAbilityClaim> extractChunkedClaims(PostAbilityExtractRequest request,
                                                        AgentMemoryContextService.ContextRules contextRules,
                                                        List<com.example.matching.agent.service.ExtractionChunker.Chunk> chunks,
                                                        PostAbilityExtractionResult result,
                                                        long startTime) {
        List<PostAbilityClaim> merged = new java.util.ArrayList<>();
        int failed = 0;
        for (com.example.matching.agent.service.ExtractionChunker.Chunk chunk : chunks) {
            try {
                PostAbilityExtractRequest chunkRequest = new PostAbilityExtractRequest();
                chunkRequest.setPostId(request.getPostId());
                chunkRequest.setPostName(request.getPostName());
                chunkRequest.setSourceType(request.getSourceType());
                chunkRequest.setSourceRefId(request.getSourceRefId());
                chunkRequest.setSourceText(chunk.text());
                chunkRequest.setSourceRefs(request.getSourceRefs());
                chunkRequest.setExistingRequirements(request.getExistingRequirements());
                chunkRequest.setChunkIndex(chunk.chunkIndex());
                chunkRequest.setChunkStartOffset(chunk.start());

                AgentMemoryContextService.ContextRules chunkRules = memoryContextService.resolveRules(
                        buildRawContextText(chunkRequest), AgentMemoryContextService.SCOPE_POST);
                String contextJson = buildExtractContextJsonWithMemory(chunkRequest, chunkRules);
                PostAbilityExtractionResult aiResult = com.example.matching.agent.config.AgentToolProvider
                        .withScope(() -> postAbilityAiService.extractAbilities(contextJson));
                if (aiResult == null || aiResult.getClaims() == null || aiResult.getClaims().isEmpty()) {
                    failed++;
                    log.warn("[EXTRACTION_CHUNK_FAILED] 分块无结果，标记 RETRY/REVIEW: chunkIndex={}", chunk.chunkIndex());
                    continue;
                }
                // 分块内 normalize（引用回填+切片补全，偏移相对块文本）
                normalizeExtractedClaims(aiResult.getClaims(), chunkRequest);
                // A chunk has its own coordinate system until validation is complete.
                // Validating its relative offsets/text against the full JD here was the
                // source of long-JD false rejections in earlier versions.
                var validation = extractionValidator.validateIndividually(
                        aiResult.getClaims(), trustedSourceText(chunkRequest), buildServerRefs(request));
                if (validation.acceptedClaims().isEmpty()) {
                    failed++;
                    validation.rejectedClaims().forEach(rejected -> recordValidationFailure(rejected.reason()));
                    log.warn("[EXTRACTION_CHUNK_FAILED] 分块无有效原文证据，标记 RETRY/REVIEW: chunkIndex={}",
                            chunk.chunkIndex());
                    continue;
                }
                if (!validation.rejectedClaims().isEmpty()) {
                    validation.rejectedClaims().forEach(rejected -> recordValidationFailure(rejected.reason()));
                    log.warn("[AI_OUTPUT_PARTIAL] 分块丢弃无效能力项: chunkIndex={}, rejected={}",
                            chunk.chunkIndex(), validation.rejectedClaims().size());
                }
                List<PostAbilityClaim> chunkClaims = new java.util.ArrayList<>(validation.acceptedClaims());
                // 证据偏移修正为全量原文偏移
                int base = chunk.start();
                for (PostAbilityClaim claim : chunkClaims) {
                    materializeEvidenceOffsets(claim, trustedSourceText(chunkRequest));
                    if (claim.getEvidenceStart() != null) {
                        claim.setEvidenceStart(claim.getEvidenceStart() + base);
                    }
                    if (claim.getEvidenceEnd() != null) {
                        claim.setEvidenceEnd(claim.getEvidenceEnd() + base);
                    }
                }
                merged.addAll(chunkClaims);
            } catch (Exception e) {
                failed++;
                log.warn("[EXTRACTION_CHUNK_FAILED] 分块提取异常，标记 RETRY/REVIEW: chunkIndex={}, error={}",
                        chunk.chunkIndex(), e.getMessage());
            }
        }
        if (failed > 0) {
            result.setFailedChunkCount(failed);
            result.setSummary("分块提取完成，但 " + failed + " 个分块失败（RETRY/REVIEW），未复制旧事实");
            log.warn("岗位能力分块提取: postId={}, chunks={}, failed={}",
                    request.getPostId(), chunks.size(), failed);
        }
        return deduplicateClaims(merged);
    }

    /**
     * Task8：合并去重——按规范化名称（去空白）保留证据更长且置信度更高的项。
     */
    private List<PostAbilityClaim> deduplicateClaims(List<PostAbilityClaim> claims) {
        Map<String, PostAbilityClaim> byKey = new java.util.LinkedHashMap<>();
        for (PostAbilityClaim claim : claims) {
            String name = claim.getNormalizedAbilityName() != null ? claim.getNormalizedAbilityName() : claim.getAbilityName();
            String key = name == null ? "" : name.replaceAll("\\s+", "");
            PostAbilityClaim existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, claim);
            } else {
                int existingLen = existing.getEvidenceText() != null ? existing.getEvidenceText().length() : 0;
                int claimLen = claim.getEvidenceText() != null ? claim.getEvidenceText().length() : 0;
                java.math.BigDecimal existingConf = existing.getConfidenceScore() != null
                        ? existing.getConfidenceScore() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal claimConf = claim.getConfidenceScore() != null
                        ? claim.getConfidenceScore() : java.math.BigDecimal.ZERO;
                boolean claimBetter = claimLen > existingLen
                        || (claimLen == existingLen && claimConf.compareTo(existingConf) > 0);
                if (claimBetter) {
                    byKey.put(key, claim);
                }
            }
        }
        return new java.util.ArrayList<>(byKey.values());
    }

    private List<PostAbilityClaim> normalizeExtractedClaims(List<PostAbilityClaim> claims,
                                                            PostAbilityExtractRequest request) {
        if (claims == null) return new ArrayList<>();
        // 服务端标准引用：source:{sourceType}:{sourceRefId}；模型自报引用一律忽略
        List<String> serverRefs = buildServerRefs(request);
        for (PostAbilityClaim claim : claims) {
            claim.setPostId(request.getPostId());
            claim.setSourceType(request.getSourceType());
            claim.setSourceRefId(request.getSourceRefId());
            if (claim.getNormalizedAbilityName() == null) claim.setNormalizedAbilityName(claim.getAbilityName());
            if (claim.getConfidenceScore() == null) claim.setConfidenceScore(new java.math.BigDecimal("80"));
            claim.setSourceRefs(serverRefs);
            // Evidence slices and the validator must use the same trusted text.
            // sourceText may be a chunk, while evidenceText is the original JD.
            sliceEvidenceFromSource(claim, trustedSourceText(request));
            try {
                claim.setRawModelOutput(objectMapper.writeValueAsString(claim));
            } catch (Exception e) {
                claim.setRawModelOutput(null);
            }
        }
        return new ArrayList<>(claims);
    }

    /**
     * Task4：服务端标准引用（忽略模型自报的外部引用）。
     */
    private List<String> buildServerRefs(PostAbilityExtractRequest request) {
        if (request.getSourceRefId() != null) {
            return List.of(com.example.matching.common.constant.SourceRefConstants.sourceRef(
                    request.getSourceType(), request.getSourceRefId()));
        }
        return request.getSourceRefs() != null ? request.getSourceRefs() : List.of();
    }

    private String buildEvidenceRepairContextJson(PostAbilityExtractRequest request,
                                                   AgentMemoryContextService.ContextRules contextRules,
                                                   String validationError) {
        try {
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("request", request);
            context.put("validationFeedback", "The previous output was rejected: " + validationError
                    + ". Return a complete replacement JSON. For every claim, copy evidenceText verbatim "
                    + "from request.sourceText. Do not output evidenceStart or evidenceEnd.");
            if (contextRules.guidancePrompt() != null && !contextRules.guidancePrompt().isBlank()) {
                context.put("governanceGuidance", contextRules.guidancePrompt());
            }
            if (contextRules.hardRuleSummary() != null && !contextRules.hardRuleSummary().isBlank()) {
                context.put("hardRuleSummary", contextRules.hardRuleSummary());
            }
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.error("构建岗位提取证据修复上下文失败", e);
            return buildExtractContextJsonWithMemory(request, contextRules);
        }
    }

    private String trustedSourceText(PostAbilityExtractRequest request) {
        return request.getEvidenceText() != null && !request.getEvidenceText().isBlank()
                ? request.getEvidenceText()
                : request.getSourceText();
    }

    /**
     * Task4：模型给出 evidenceStart/evidenceEnd 偏移时，服务端从原文切片生成
     * evidenceText 并核验边界；无偏移或越界时保持模型证据文本（由校验器把关）。
     */
    private void sliceEvidenceFromSource(PostAbilityClaim claim, String sourceText) {
        if (claim.getEvidenceStart() == null || claim.getEvidenceEnd() == null
                || sourceText == null) {
            return;
        }
        int start = claim.getEvidenceStart();
        int end = claim.getEvidenceEnd();
        if (start < 0 || end > sourceText.length() || start >= end) {
            log.warn("[AI_OUTPUT_INVALID] 证据切片边界越界，忽略偏移: start={}, end={}, len={}",
                    start, end, sourceText.length());
            claim.setEvidenceStart(null);
            claim.setEvidenceEnd(null);
            return;
        }
        String sliced = sourceText.substring(start, end);
        if (sliced != null && !sliced.isBlank()) {
            claim.setEvidenceText(sliced.trim());
        }
    }

    /** Fill missing positions server-side after evidence has passed text grounding. */
    private void materializeEvidenceOffsets(PostAbilityClaim claim, String sourceText) {
        if (claim.getEvidenceStart() != null || claim.getEvidenceText() == null || sourceText == null) return;
        int start = sourceText.indexOf(claim.getEvidenceText());
        if (start >= 0) {
            claim.setEvidenceStart(start);
            claim.setEvidenceEnd(start + claim.getEvidenceText().length());
        }
    }

    private void markExtractionUnavailable(PostAbilityExtractionResult result, String reason) {
        result.setFallbackUsed(true);
        result.setClaims(Collections.emptyList());
        result.setSummary("岗位能力提取未完成，待重试/人工复核：" + reason);
    }
}
