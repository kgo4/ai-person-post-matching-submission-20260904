package com.example.matching.service.post.impl;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.ai.validation.PostAbilityExtractionValidator;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.post.AbilityExtractResultDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 岗位能力提取支持：Agent 提取、结果转换、标签解析、AI 响应解析。
 * <p>
 * 从 PostCapabilityGenerationServiceImpl（700 行）中拆分的提取/解析组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCapabilityExtractionSupport {

    private final PostAbilityAgentService postAbilityAgentService;
    private final ObjectMapper objectMapper;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final AbilityTagService abilityTagService;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    private static final double SIMILARITY_THRESHOLD_HIGH = 0.85;
    private static final double SIMILARITY_THRESHOLD_LOW = 0.6;
    public PostAbilityExtractionResult convertItemsToExtractionResult(Long postId,
                                                                        List<JdAbilityItemDTO> items,
                                                                        Map<String, AbilityTag> tagNameMap,
                                                                        String postText) {
        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setPostId(postId);
        result.setSourceType("JD_IMPORT");
        List<PostAbilityClaim> claims = new ArrayList<>();
        List<PostAbilityClaim> deferredClaims = new ArrayList<>();
        List<PostAbilityClaim> rejectedClaims = new ArrayList<>();

        if (items != null) {
            for (JdAbilityItemDTO item : items) {
                if (!PostAbilityExtractionValidator.isAssessableAbility(item.getSuggestedName(), item.getAbilityType())) {
                    PostAbilityClaim rejected = buildClaim(postId, item, null);
                    rejected.setExtractReason("准入条件不得写入岗位能力模型，应配置为岗位硬性条件");
                    rejectedClaims.add(rejected);
                    log.info("岗位准入条件已从能力模型写入链路拦截: postId={}, item={}",
                            postId, item.getSuggestedName());
                    continue;
                }
                if (!isTrustedForApply(item, postText)) {
                    // Harness BLOCK/REVIEW 或非 MATCHED/SIMILAR：拒绝项，原因可查，不静默丢弃
                    PostAbilityClaim rejected = buildClaim(postId, item, null);
                    rejected.setExtractReason(item.getReasoning());
                    rejectedClaims.add(rejected);
                    log.info("岗位能力项未通过可信门禁（拒绝）: postId={}, ability={}, matchStatus={}, reasoning={}",
                            postId, item.getSuggestedName(), item.getMatchStatus(), item.getReasoning());
                    continue;
                }

                // 确定标签（通过统一准入中心）
                TagResolution resolution = resolveTag(item, tagNameMap, postId, postText);
                if (resolution == null) {
                    log.warn("准入中心返回空结果，按拒绝处理: postId={}, ability={}", postId, item.getSuggestedName());
                    PostAbilityClaim rejected = buildClaim(postId, item, null);
                    rejected.setExtractReason("准入中心返回空结果");
                    rejectedClaims.add(rejected);
                    continue;
                }
                if (resolution.getDecision() == TagResolutionDecision.CANDIDATE) {
                    // Tag governance is independent from the role profile. Keep the
                    // candidate for taxonomy review, but retain the source-validated
                    // ability for direct role-profile persistence.
                    PostAbilityClaim deferred = buildClaim(postId, item, null);
                    deferred.setCandidateId(resolution.getCandidateId());
                    deferred.setExtractReason(resolution.getReason());
                    deferredClaims.add(deferred);
                    claims.add(deferred);
                    log.info("岗位能力未匹配正式标签，岗位画像继续写入: postId={}, ability={}, reason={}",
                            postId, item.getSuggestedName(), resolution.getReason());
                    continue;
                }
                if (resolution.getDecision() == TagResolutionDecision.REJECTED) {
                    PostAbilityClaim rejected = buildClaim(postId, item, null);
                    rejected.setExtractReason(resolution.getReason());
                    rejectedClaims.add(rejected);
                    claims.add(rejected);
                    log.info("岗位能力标签候选被拒绝，岗位画像继续写入: postId={}, ability={}, reason={}",
                            postId, item.getSuggestedName(), resolution.getReason());
                    continue;
                }

                // FORMAL/REUSED：正式 claim 写入岗位模型
                PostAbilityClaim claim = buildClaim(postId, item, resolution.getTagId());
                claim.setExtractReason(item.getReasoning());
                claims.add(claim);
            }
        }

        result.setClaims(claims);
        result.setDeferredClaims(deferredClaims);
        result.setRejectedClaims(rejectedClaims);
        result.setFormalCount(claims.size());
        result.setPendingCount(deferredClaims.size());
        result.setRejectedCount(rejectedClaims.size());
        log.info("岗位能力转换完成: postId={}, formal={}, pending={}, rejected={}",
                postId, claims.size(), deferredClaims.size(), rejectedClaims.size());
        return result;
    }

    private PostAbilityClaim buildClaim(Long postId, JdAbilityItemDTO item, Long tagId) {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setPostId(postId);
        claim.setSourceType("JD_IMPORT");
        claim.setAbilityName(item.getSuggestedName());
        claim.setNormalizedAbilityName(item.getSuggestedName());
        claim.setTechStack(item.getTechStack());
        claim.setAbilityTagId(tagId);
        claim.setRequiredLevel(item.getMinRequiredLevel());
        claim.setWeight(item.getWeight());
        claim.setIsCore(item.getIsCore() != null && item.getIsCore() == 1);
        claim.setIsRequired(item.getIsRequired() != null && item.getIsRequired() == 1);
        claim.setSourceRefId(postId);
        claim.setEvidenceText(item.getEvidenceText());
        claim.setEvidenceAnchor(item.getEvidenceAnchor());
        claim.setAbilityType(item.getAbilityType());
        claim.setEvidenceStart(item.getEvidenceStart());
        claim.setEvidenceEnd(item.getEvidenceEnd());
        claim.setSourceRefs(item.getSourceRefs() != null && !item.getSourceRefs().isEmpty()
                ? new ArrayList<>(item.getSourceRefs())
                : List.of("source:JD_IMPORT:" + postId));
        return claim;
    }

    /** 标签解析决策 */
    public enum TagResolutionDecision { FORMAL, CANDIDATE, REJECTED }

    /** 标签解析结果（正式 tagId 或候选/拒绝信息） */
    public static class TagResolution {
        private final TagResolutionDecision decision;
        private final Long tagId;
        private final Long candidateId;
        private final String reason;

        public TagResolution(TagResolutionDecision decision, Long tagId, Long candidateId, String reason) {
            this.decision = decision;
            this.tagId = tagId;
            this.candidateId = candidateId;
            this.reason = reason;
        }

        public TagResolutionDecision getDecision() { return decision; }
        public Long getTagId() { return tagId; }
        public Long getCandidateId() { return candidateId; }
        public String getReason() { return reason; }
    }

    /**
     * 解析标签ID（处理MATCHED/SIMILAR/NEW状态）
     * <p>
     * 仅返回可直接复用的正式标签；新能力进入独立候选池。
     */
    public Long resolveTagId(JdAbilityItemDTO item, Map<String, AbilityTag> tagNameMap, Long postId, String postText) {
        TagResolution resolution = resolveTag(item, tagNameMap, postId, postText);
        return resolution != null && resolution.getDecision() == TagResolutionDecision.FORMAL
                ? resolution.getTagId() : null;
    }

    /**
     * 解析标签（处理MATCHED/SIMILAR/NEW状态），返回完整决策。
     * <p>
     * 仅解析已有标签；未匹配能力以无标签岗位能力返回。
     * 标签候选在岗位模型成功写入后由独立事件链路处理。
     */
    public TagResolution resolveTag(JdAbilityItemDTO item, Map<String, AbilityTag> tagNameMap, Long postId, String postText) {
        // MATCHED/SIMILAR：Agent 返回的标签ID必须二次校验存在且未删除，
        // 防止幻觉/张冠李戴的错误标签直接写入 post_ability_model 污染下游匹配
        if (("MATCHED".equals(item.getMatchStatus()) || "SIMILAR".equals(item.getMatchStatus()))
                && item.getMatchedTagId() != null) {
            AbilityTag matchedTag = abilityTagService.getById(item.getMatchedTagId());
            if (matchedTag != null && matchedTag.getIsDeleted() != null && matchedTag.getIsDeleted() == 1) {
                log.warn("Agent 返回的标签已被删除，降级按 NEW 处理: tagId={}, status={}, name={}",
                        item.getMatchedTagId(), item.getMatchStatus(), item.getSuggestedName());
            } else if (matchedTag != null) {
                return new TagResolution(TagResolutionDecision.FORMAL, matchedTag.getId(), null, "MATCHED");
            } else {
                log.warn("Agent 返回的标签不存在（幻觉），降级按 NEW 处理: tagId={}, status={}, name={}",
                        item.getMatchedTagId(), item.getMatchStatus(), item.getSuggestedName());
            }
        } else {
            // NEW 状态：先复用已加载的正式标签。
            if (tagNameMap != null) {
                AbilityTag existingTag = tagNameMap.get(item.getSuggestedName());
                if (existingTag != null) {
                    return new TagResolution(TagResolutionDecision.FORMAL, existingTag.getId(), null, "NEW_NAME_MAP_HIT");
                }
            }
        }

        // MATCHED/SIMILAR 校验失败或 NEW 状态：岗位画像继续使用能力名称。
        if (item.getSuggestedName() == null || item.getSuggestedName().isBlank()) {
            log.warn("岗位能力标签名称为空，拒绝准入: postId={}", postId);
            return new TagResolution(TagResolutionDecision.REJECTED, null, null, "能力名称为空");
        }
        return new TagResolution(TagResolutionDecision.CANDIDATE, null, null,
                "SOURCE_VALIDATED_UNTAGGED_ABILITY");
    }

    // ===== 内部方法 =====

    /**
     * 通过岗位能力Agent提取能力声明
     * <p>
     * 替代原来的直接ChatClient调用，统一走Agent链路。
     *
     * @param postName    岗位名称
     * @param postText    岗位文本（已含RAG上下文）
     * @param sourceType  来源类型
     * @param sourceRefId 来源引用ID
     * @param sourceRefs  来源引用列表
     * @return 提取结果
     */
    public PostAbilityExtractionResult extractAbilitiesViaAgent(String postName, String postText,
                                                                  String sourceType, Long sourceRefId,
                                                                  List<String> sourceRefs) {
        return extractAbilitiesViaAgent(postName, postText, postText, sourceType, sourceRefId, sourceRefs);
    }

    /**
     * The Agent may receive enriched context, but its evidence is checked only
     * against the unmodified server-owned source text.
     */
    public PostAbilityExtractionResult extractAbilitiesViaAgent(String postName, String agentContextText,
                                                                  String trustedSourceText, String sourceType,
                                                                  Long sourceRefId, List<String> sourceRefs) {
        PostAbilityExtractRequest request = new PostAbilityExtractRequest();
        request.setPostName(postName);
        request.setSourceType(sourceType);
        request.setSourceRefId(sourceRefId);
        // sourceText is the one and only evidence coordinate system. Do not put
        // tag hints or generated context ahead of the JD: evidence offsets and
        // text-location validation must refer to the same server-owned text.
        request.setSourceText(trustedSourceText);
        request.setEvidenceText(trustedSourceText);
        request.setSourceRefs(sourceRefs);

        log.info("通过Agent提取岗位能力: postName={}, sourceType={}, sourceRefId={}", postName, sourceType, sourceRefId);
        return postAbilityAgentService.extractAbilities(request);
    }

    /**
     * 从Agent提取结果构建JdAbilityItemDTO列表（兼容旧接口）
     */
    public List<JdAbilityItemDTO> convertClaimsToItems(PostAbilityExtractionResult extractionResult,
                                                         List<AbilityTag> existingTags) {
        List<JdAbilityItemDTO> items = new ArrayList<>();
        if (extractionResult == null || extractionResult.getClaims() == null) {
            return items;
        }
        for (PostAbilityClaim claim : extractionResult.getClaims()) {
            JdAbilityItemDTO item = new JdAbilityItemDTO();
            item.setSuggestedName(claim.getAbilityName());
            item.setTechStack(claim.getTechStack());
            item.setTagCategory(claim.getAbilityType() == null || claim.getAbilityType().isBlank()
                    ? "TECHNICAL" : claim.getAbilityType());
            item.setMinRequiredLevel(claim.getRequiredLevel());
            item.setWeight(claim.getWeight());
            item.setIsCore(claim.getIsCore() != null && claim.getIsCore() ? 1 : 0);
            item.setIsRequired(claim.getIsRequired() != null && claim.getIsRequired() ? 1 : 0);
            item.setReasoning(claim.getExtractReason());

            // 证据字段端到端保留（Harness 载荷，不得使用 reasoning 代替）
            item.setConfidenceScore(claim.getConfidenceScore());
            item.setEvidenceText(claim.getEvidenceText());
            item.setEvidenceAnchor(claim.getEvidenceAnchor());
            item.setAbilityType(claim.getAbilityType());
            item.setEvidenceStart(claim.getEvidenceStart());
            item.setEvidenceEnd(claim.getEvidenceEnd());
            item.setSourceRefs(claim.getSourceRefs() != null
                    ? new ArrayList<>(claim.getSourceRefs()) : null);

            // 标签匹配：优先使用Agent返回的标签ID，但仅当该ID存在于启用标签列表时才判定为精确匹配
            if (claim.getAbilityTagId() != null) {
                existingTags.stream()
                        .filter(t -> t.getId().equals(claim.getAbilityTagId()))
                        .findFirst()
                        .ifPresentOrElse(matched -> {
                            item.setMatchStatus("MATCHED");
                            item.setMatchedTagId(claim.getAbilityTagId());
                            item.setMatchedTagName(matched.getTagName());
                            item.setSimilarityScore(1.0);
                        }, () -> {
                            // 防止伪造/失效的标签ID成为正式标签
                            item.setMatchStatus("NEW");
                            item.setMatchedTagId(null);
                        });
            } else if (claim.getSimilarTagId() != null) {
                item.setMatchStatus("SIMILAR");
                item.setMatchedTagId(claim.getSimilarTagId());
                existingTags.stream()
                        .filter(t -> t.getId().equals(claim.getSimilarTagId()))
                        .findFirst()
                        .ifPresent(t -> item.setMatchedTagName(t.getTagName()));
            } else {
                // Agent未返回标签ID，尝试本地匹配
                matchWithExistingTags(item, existingTags);
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 构建富化的sourceText，包含RAG上下文和已有标签参考
     */
    public String buildEnrichedSourceText(String postName, String postText,
                                            List<AbilityTag> existingTags, String ragContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位名称：").append(postName).append("\n\n");
        sb.append("岗位描述：\n").append(postText).append("\n\n");

        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("相关知识上下文：\n").append(ragContext).append("\n\n");
        }

        if (existingTags != null && !existingTags.isEmpty()) {
            sb.append("系统已有能力标签（供参考）：\n");
            for (AbilityTag tag : existingTags) {
                sb.append("- ").append(tag.getTagName())
                        .append("（").append(tag.getTagCategory()).append("）\n");
            }
        }

        return sb.toString();
    }

    public Map<String, Object> parseAiResponse(String aiResponse) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) {
                aiResponse = "{}";
            }
            String json = llmResponseParser.extractJson(aiResponse);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析AI响应JSON失败: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("jobSummary", "");
            fallback.put("abilities", Collections.emptyList());
            return fallback;
        }
    }

    public Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    public BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return null; }
    }

    public Integer toBoolInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Boolean) return (Boolean) val ? 1 : 0;
        if (val instanceof Integer) return (Integer) val;
        return Boolean.parseBoolean(val.toString()) ? 1 : 0;
    }

    public boolean isTrustedForApply(JdAbilityItemDTO item, String trustedSourceText) {
        if (item == null) {
            return false;
        }
        if (item.getEvidenceText() == null || item.getEvidenceText().isBlank()) {
            return false;
        }
        if (trustedSourceText != null && !trustedSourceText.isBlank()
                && !normalizeText(trustedSourceText).contains(normalizeText(item.getEvidenceText()))) {
            return false;
        }
        // Legacy hallucination markers are advisory only. They were produced by a
        // separate model and must not override the deterministic source-evidence check.
        // Unknown abilities remain valid claims when their source evidence is valid.
        return true;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    @SuppressWarnings("unchecked")
    public List<String> toStringList(Object val) {
        if (val == null) return Collections.emptyList();
        if (val instanceof List) {
            List<?> rawList = (List<?>) val;
            return rawList.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public void matchWithExistingTags(JdAbilityItemDTO item, List<AbilityTag> existingTags) {
        String suggestedName = item.getSuggestedName();
        if (suggestedName == null || suggestedName.isEmpty()) {
            item.setMatchStatus("NEW");
            return;
        }

        // 1. 精确名称匹配
        for (AbilityTag tag : existingTags) {
            if (suggestedName.equals(tag.getTagName())) {
                item.setMatchStatus("MATCHED");
                item.setMatchedTagId(tag.getId());
                item.setMatchedTagName(tag.getTagName());
                item.setSimilarityScore(1.0);
                return;
            }
        }

        // 2. 别名匹配
        AbilityTag aliasMatch = abilityTagService.findByAlias(suggestedName);
        if (AbilityTagHierarchy.isAssessable(aliasMatch)) {
            item.setMatchStatus("MATCHED");
            item.setMatchedTagId(aliasMatch.getId());
            item.setMatchedTagName(aliasMatch.getTagName());
            item.setSimilarityScore(1.0);
            return;
        }

        // 3. 向量相似度匹配
        try {
            List<Float> suggestedVector = vectorEmbeddingService.embed(suggestedName);
            double bestScore = 0;
            AbilityTag bestMatch = null;

            for (AbilityTag tag : existingTags) {
                if (tag.getEmbeddingVector() == null || tag.getEmbeddingVector().isEmpty()) {
                    continue;
                }
                Float score = vectorEmbeddingService.cosineSimilarity(suggestedVector, tag.getEmbeddingVector());
                if (score != null && score > bestScore) {
                    bestScore = score;
                    bestMatch = tag;
                }
            }

            if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD_HIGH) {
                item.setMatchStatus("MATCHED");
                item.setMatchedTagId(bestMatch.getId());
                item.setMatchedTagName(bestMatch.getTagName());
                item.setSimilarityScore(bestScore);
            } else if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD_LOW) {
                item.setMatchStatus("SIMILAR");
                item.setMatchedTagId(bestMatch.getId());
                item.setMatchedTagName(bestMatch.getTagName());
                item.setSimilarityScore(bestScore);
            } else {
                item.setMatchStatus("NEW");
            }
        } catch (Exception e) {
            log.warn("向量匹配失败，标记为新标签: suggestedName={}, error={}", suggestedName, e.getMessage());
            item.setMatchStatus("NEW");
        }
    }
}
