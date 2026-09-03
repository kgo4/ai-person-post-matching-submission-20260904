package com.example.matching.service.system.impl;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagNormalizer;
import com.example.matching.service.system.AbilityTagVectorOperations;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 标签统一准入管线：规范化 -> 精确/别名/向量匹配 -> 质量/证据/来源/置信度检查 -> 标签治理候选。
 * <p>
 * 从 AbilityTagAdmissionEngine（700+ 行）中拆分的准入决策组件。
 * 查找/别名/相似度/创建等共享能力委托给 {@link AbilityTagAdmissionEngine}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbilityTagAdmissionPipeline {

    // 相似度分层阈值
    private static final double SIMILARITY_THRESHOLD_HIGH = 0.92;
    private static final double SIMILARITY_THRESHOLD_MEDIUM_HIGH = 0.82;
    private static final double SIMILARITY_THRESHOLD_MEDIUM = 0.70;
    private static final double SIMILARITY_THRESHOLD_LOW = 0.55;

    private static final BigDecimal HARNESS_PASS_SCORE_THRESHOLD = new BigDecimal("80");

    private final AbilityTagAdmissionEngine engine;
    private final AbilityTagVectorOperations vectorOperations;
    private final AbilityTagNormalizer abilityTagNormalizer;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final AbilityTagCandidateMapper tagCandidateMapper;
    private final AbilityTagAliasMapper tagAliasMapper;

    /** Task10：可选注入的提取指标（测试环境无 MeterRegistry 时为空） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.agent.config.ExtractionMetrics extractionMetrics;

    @Transactional
    public TagAdmissionResult admitNewTag(TagAdmissionContext context) {
        String rawTagName = context.getTagName();
        String tagCategory = context.getTagCategory();
        String sourceType = context.getSourceType();

        log.info("标签准入决策开始: rawTagName={}, sourceType={}, confidence={}",
                rawTagName, sourceType, context.getConfidenceScore());

        if (!isTaxonomySource(sourceType)) {
            return TagAdmissionResult.rejected("人员能力来源不参与系统标签库治理: " + sourceType);
        }

        String normalizedTagName = abilityTagNormalizer.normalize(rawTagName);
        context.setNormalizedTagName(normalizedTagName);
        log.info("标签规范化: '{}' -> '{}'", rawTagName, normalizedTagName);
        String tagName = normalizedTagName;

        // 阶段1: 精确匹配
        AbilityTag exactMatch = engine.findByName(tagName);
        if (exactMatch != null) {
            log.debug("精确匹配已有标签: tagName={}, tagId={}", tagName, exactMatch.getId());
            recordTagOutcome(() -> extractionMetrics.tagReused());
            return TagAdmissionResult.reused(exactMatch, "精确匹配已有标签", null, null);
        }
        if (!rawTagName.equals(tagName)) {
            AbilityTag rawExactMatch = engine.findByName(rawTagName);
            if (rawExactMatch != null) {
                log.debug("原始名称精确匹配已有标签: rawTagName={}, tagId={}", rawTagName, rawExactMatch.getId());
                engine.saveAlias(rawExactMatch.getId(), tagName);
                return TagAdmissionResult.reused(rawExactMatch, "精确匹配已有标签（原始名称）", null, null);
            }
        }

        // 阶段2: 别名匹配
        AbilityTag aliasMatch = engine.findByAlias(tagName);
        if (aliasMatch != null) {
            log.debug("别名匹配已有标签: tagName={}, matchedTag={}, tagId={}",
                    tagName, aliasMatch.getTagName(), aliasMatch.getId());
            return TagAdmissionResult.reused(aliasMatch,
                    "通过别名匹配到已有标签: " + aliasMatch.getTagName(), null, null);
        }
        if (!rawTagName.equals(tagName)) {
            AbilityTag rawAliasMatch = engine.findByAlias(rawTagName);
            if (rawAliasMatch != null) {
                log.debug("原始名称别名匹配: rawTagName={}, matchedTag={}", rawTagName, rawAliasMatch.getTagName());
                return TagAdmissionResult.reused(rawAliasMatch,
                        "通过别名匹配到已有标签: " + rawAliasMatch.getTagName(), null, null);
            }
        }

        // 阶段3: 向量相似度匹配（分层阈值）
        List<AbilityTag> similarTags = engine.findSimilarTags(tagName, SIMILARITY_THRESHOLD_LOW);
        if (!similarTags.isEmpty()) {
            AbilityTag matched = similarTags.get(0);
            Float similarity = vectorOperations.calculateSimilarity(tagName, matched);
            if (similarity != null) {
                log.debug("向量相似度匹配: tagName={}, matched={}, similarity={}",
                        tagName, matched.getTagName(), similarity);
                if (similarity >= SIMILARITY_THRESHOLD_HIGH) {
                    log.info("高相似标签直接复用: tagName={}, matched={}, similarity={}",
                            tagName, matched.getTagName(), similarity);
                    engine.saveAlias(matched.getId(), tagName);
                    if (!rawTagName.equals(tagName)) {
                        engine.saveAlias(matched.getId(), rawTagName);
                    }
                    return TagAdmissionResult.reused(matched,
                            String.format("高相似标签直接复用（相似度: %.2f）: %s", similarity, matched.getTagName()),
                            similarTags, similarity);
                }
                if (similarity >= SIMILARITY_THRESHOLD_MEDIUM_HIGH) {
                    log.info("中高相似标签进入合并审核: tagName={}, matched={}, similarity={}",
                            tagName, matched.getTagName(), similarity);
                    AbilityTagCandidate candidate = createCandidateForSimilarTag(
                            context, matched, similarity, "中高相似，建议合并到已有标签");
                    return TagAdmissionResult.candidate(candidate, "SIMILAR_MERGE_REVIEW", null);
                }
                if (similarity >= SIMILARITY_THRESHOLD_MEDIUM) {
                    log.info("中相似标签进入候选池: tagName={}, matched={}, similarity={}",
                            tagName, matched.getTagName(), similarity);
                    engine.saveSimilarRelationIfAbsent(matched.getId(), tagName);
                    return processWithHarnessForSimilar(context, matched, similarity);
                }
                log.debug("低相似标签，视为新能力: tagName={}, matched={}, similarity={}",
                        tagName, matched.getTagName(), similarity);
                engine.saveSimilarRelationIfAbsent(matched.getId(), tagName);
            }
        }

        // 阶段4: 新标签准入判断
        if (abilityTagNormalizer.isLowQualityName(tagName)) {
            log.info("标签质量不合格（低质量模式），直接拒绝: tagName={}", tagName);
            return TagAdmissionResult.rejected("标签质量不合格: 低质量模式黑名单");
        }
        if (abilityTagNormalizer.isSentenceLike(tagName)) {
            log.info("标签是句子型，直接拒绝: tagName={}", tagName);
            return TagAdmissionResult.rejected("标签质量不合格: 句子型标签");
        }
        int qualityScore = abilityTagNormalizer.getQualityScore(tagName);
        if (qualityScore < 40) {
            log.info("标签质量评分过低，直接拒绝: tagName={}, score={}", tagName, qualityScore);
            return TagAdmissionResult.rejected("标签质量不合格: 质量评分 " + qualityScore);
        }

        // 4.2 证据检查
        if (!StringUtils.hasText(context.getEvidenceText())) {
            log.info("缺少证据文本，进入候选池: tagName={}", tagName);
            AbilityTagCandidate candidate = createCandidateFromContext(context, "NO_EVIDENCE", "缺少证据文本");
            return TagAdmissionResult.candidate(candidate, "NO_EVIDENCE", null);
        }

        // 4.3 来源可信度检查
        if (!isTrustedSource(sourceType)) {
            log.info("来源不可信，进入候选池: tagName={}, sourceType={}", tagName, sourceType);
            AbilityTagCandidate candidate = createCandidateFromContext(context, "UNTRUSTED_SOURCE", "来源不可信: " + sourceType);
            return TagAdmissionResult.candidate(candidate, "UNTRUSTED_SOURCE", null);
        }

        // 4.4 置信度检查
        Float confidence = context.getConfidenceScore();
        if (confidence == null || confidence < 0.55f) {
            log.info("置信度不足，进入候选池: tagName={}, confidence={}", tagName, confidence);
            AbilityTagCandidate candidate = createCandidateFromContext(context, "LOW_CONFIDENCE",
                    String.format("置信度不足: %.2f", confidence != null ? confidence : 0.0f));
            return TagAdmissionResult.candidate(candidate, "LOW_CONFIDENCE", null);
        }

        // 原文证据已经在提取边界定位校验。标签库新增还需要人工确认层级和同义关系，
        // 因此只创建标签治理候选，不再调用独立的“幻觉 Harness”生成第三个审核队列。
        AbilityTagCandidate candidate = createCandidateFromContext(
                context, "SOURCE_EVIDENCE_VALIDATED", "原文证据已校验，等待标签层级与同义关系治理");
        recordTagOutcome(() -> extractionMetrics.tagCandidateCreated());
        return TagAdmissionResult.candidate(candidate, "TAG_GOVERNANCE", null);
    }

    /**
     * 已验证标签准入：使用调用方（市场JD自动准入）已完成的 AiTrustHarnessService 决策，
     * <strong>不再重复调用可信度判定服务</strong>。
     * <p>
     * 规则：
     * <ul>
     *   <li>PASS 且支持分 >= {@value #HARNESS_PASS_SCORE_THRESHOLD}：创建/复用正式标签</li>
     *   <li>PASS 但分数不足：仅创建候选（PASS_LOW_SCORE）</li>
     *   <li>REVIEW：仅创建候选</li>
     *   <li>BLOCK / RETRY / 未知决策 / 证据缺失 / 来源不可信：拒绝</li>
     * </ul>
     * 复用规范化、精确/别名查找、质量检查、证据/来源检查与候选创建，但跳过 Harness 阶段。
     *
     * @param context  标签准入上下文（tagName/sourceType/evidenceText 等）
     * @param decision 已验证的 Harness 决策
     * @return 准入结果
     */
    @Transactional
    public TagAdmissionResult admitVerifiedNewTag(TagAdmissionContext context, AiHarnessDecisionDTO decision) {
        String rawTagName = context.getTagName();
        String tagCategory = context.getTagCategory();
        String sourceType = context.getSourceType();

        log.info("已验证标签准入开始: rawTagName={}, sourceType={}, decision={}",
                rawTagName, sourceType, decision != null ? decision.getDecision() : null);

        if (!isTaxonomySource(sourceType)) {
            return TagAdmissionResult.rejected("非岗位来源不参与系统标签库治理: " + sourceType);
        }

        String normalizedTagName = abilityTagNormalizer.normalize(rawTagName);
        context.setNormalizedTagName(normalizedTagName);
        String tagName = normalizedTagName;

        // 阶段1: 精确/别名匹配（防御性复用现有标签，避免重复创建）
        AbilityTag exactMatch = engine.findByName(tagName);
        if (exactMatch != null) {
            log.debug("已验证准入精确匹配已有标签: tagName={}, tagId={}", tagName, exactMatch.getId());
            recordTagOutcome(() -> extractionMetrics.tagReused());
            return TagAdmissionResult.reused(exactMatch, "精确匹配已有标签", null, null);
        }
        if (!rawTagName.equals(tagName)) {
            AbilityTag rawExactMatch = engine.findByName(rawTagName);
            if (rawExactMatch != null) {
                engine.saveAlias(rawExactMatch.getId(), tagName);
                return TagAdmissionResult.reused(rawExactMatch, "精确匹配已有标签（原始名称）", null, null);
            }
        }
        AbilityTag aliasMatch = engine.findByAlias(tagName);
        if (aliasMatch != null) {
            return TagAdmissionResult.reused(aliasMatch,
                    "通过别名匹配到已有标签: " + aliasMatch.getTagName(), null, null);
        }
        if (!rawTagName.equals(tagName)) {
            AbilityTag rawAliasMatch = engine.findByAlias(rawTagName);
            if (rawAliasMatch != null) {
                return TagAdmissionResult.reused(rawAliasMatch,
                        "通过别名匹配到已有标签: " + rawAliasMatch.getTagName(), null, null);
            }
        }

        // 阶段2: 质量检查
        if (abilityTagNormalizer.isLowQualityName(tagName)) {
            return TagAdmissionResult.rejected("标签质量不合格: 低质量模式黑名单");
        }
        if (abilityTagNormalizer.isSentenceLike(tagName)) {
            return TagAdmissionResult.rejected("标签质量不合格: 句子型标签");
        }
        int qualityScore = abilityTagNormalizer.getQualityScore(tagName);
        if (qualityScore < 40) {
            return TagAdmissionResult.rejected("标签质量不合格: 质量评分 " + qualityScore);
        }

        // 阶段3: 证据/来源检查（已验证入口：证据/来源不全直接拒绝，不进入候选）
        if (!StringUtils.hasText(context.getEvidenceText())) {
            return TagAdmissionResult.rejected("缺少证据文本");
        }
        if (!isTrustedSource(sourceType)) {
            return TagAdmissionResult.rejected("来源不可信: " + sourceType);
        }

        // 阶段4: 按调用方已完成的可信度决策分流，不重复验证。
        String harnessDecision = decision != null ? decision.getDecision() : null;
        BigDecimal harnessScore = decision != null ? decision.getSupportScore() : null;
        String reason = decision != null && decision.getReasons() != null && !decision.getReasons().isEmpty()
                ? String.join("; ", decision.getReasons()) : null;
        String harnessLogId = decision != null ? decision.getCheckCode() : null;

        if ("PASS".equals(harnessDecision)) {
            if (harnessScore != null && harnessScore.compareTo(HARNESS_PASS_SCORE_THRESHOLD) >= 0) {
                log.info("已验证 Harness PASS 且分数足够，新标签自动入库: tagName={}, score={}", tagName, harnessScore);
                AbilityTag newTag = engine.createAiTag(tagName, tagCategory, sourceType);
                if (newTag == null) {
                    AbilityTagCandidate candidate = createCandidateFromContext(context,
                            "NO_L2_CLASSIFICATION", "无法唯一归属到现有L1能力域");
                    recordTagOutcome(() -> extractionMetrics.tagCandidateCreated());
                    return TagAdmissionResult.candidate(candidate, "NO_L2_CLASSIFICATION", harnessScore);
                }
                if (!rawTagName.equals(tagName)) {
                    engine.saveAlias(newTag.getId(), rawTagName);
                }
                log.info("[tag.formal_created] 市场准入新标签溯源: tagId={}, tagName={}, rawTagName={}, sourceType={}, sourceRefId={}, harnessLogId={}",
                        newTag.getId(), tagName, rawTagName, sourceType,
                        context.getSourceRefId(), harnessLogId);
                TagAdmissionResult created = TagAdmissionResult.created(newTag, harnessDecision, harnessScore);
                created.setHarnessLogId(harnessLogId);
                recordTagOutcome(() -> extractionMetrics.tagFormalCreated());
                return created;
            }
            log.info("已验证 Harness PASS 但分数不足，进入候选池: tagName={}, score={}", tagName, harnessScore);
            AbilityTagCandidate lowScoreCandidate = createCandidateFromContext(context, "PASS_LOW_SCORE",
                    String.format("已验证 Harness PASS 但分数不足: %s", harnessScore));
            return TagAdmissionResult.candidate(lowScoreCandidate, harnessDecision, harnessScore);
        }

        if ("REVIEW".equals(harnessDecision)) {
            log.info("已验证 Harness REVIEW，新标签进入候选池: tagName={}, score={}", tagName, harnessScore);
            AbilityTagCandidate reviewCandidate = createCandidateFromContext(context, "REVIEW",
                    "已验证 Harness REVIEW，等待人工审核");
            TagAdmissionResult reviewResult = TagAdmissionResult.candidate(reviewCandidate, harnessDecision, harnessScore);
            reviewResult.setHarnessLogId(harnessLogId);
            recordTagOutcome(() -> extractionMetrics.tagCandidateCreated());
            return reviewResult;
        }

        // BLOCK / RETRY / 未知决策：拒绝
        log.warn("已验证 Harness 拒绝: decision={}, reason={}, tagName={}", harnessDecision, reason, tagName);
        recordTagOutcome(() -> extractionMetrics.tagRejected());
        return TagAdmissionResult.rejected("已验证 Harness 拒绝: " + (reason != null ? reason : String.valueOf(harnessDecision)));
    }

    /**
     * Human Harness approval is already the final governance decision. It must not
     * be downgraded to the AI score threshold used by market/JD discovery.
     */
    @Transactional
    public TagAdmissionResult admitHumanApprovedNewTag(TagAdmissionContext context) {
        if (!isTaxonomySource(context.getSourceType())) {
            return TagAdmissionResult.rejected("非岗位来源不参与系统标签库治理: " + context.getSourceType());
        }
        String rawName = context.getTagName();
        String normalized = abilityTagNormalizer.normalize(rawName);
        AbilityTag existing = engine.findByName(normalized);
        if (existing == null) existing = engine.findByAlias(normalized);
        if (existing != null) {
            return TagAdmissionResult.reused(existing, "Harness 人工审核后复用现有标签", null, null);
        }
        if (abilityTagNormalizer.isLowQualityName(normalized)
                || abilityTagNormalizer.isSentenceLike(normalized)
                || abilityTagNormalizer.getQualityScore(normalized) < 40) {
            return TagAdmissionResult.rejected("人工审核标签名称质量不合格");
        }
        AbilityTag tag = engine.createFormalTag(normalized,
                context.getTagCategory(), context.getDomain(), context.getEvidenceText(),
                context.getSourceType());
        if (tag == null) {
            return TagAdmissionResult.rejected("人工审核通过但标签树归属失败");
        }
        return TagAdmissionResult.created(tag, AiHarnessDecisionDTO.PASS, new BigDecimal("100"));
    }

    // ===== 私有辅助 =====

    /** Task10：标签准入结果指标（空安全）。 */
    private void recordTagOutcome(Runnable recorder) {
        if (extractionMetrics != null) {
            recorder.run();
        }
    }

    private TagAdmissionResult processWithHarnessForSimilar(TagAdmissionContext context,
                                                            AbilityTag similarTag,
                                                            float similarity) {
        String tagName = context.getNormalizedTagName();
        String sourceType = context.getSourceType();

        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setCandidateName(tagName);
        candidate.setTagCategory(context.getTagCategory() != null ? context.getTagCategory() : "TECHNICAL");
        candidate.setDomain(context.getDomain());
        candidate.setSourceType(sourceType);
        candidate.setSourceRefId(context.getSourceRefId());
        candidate.setSourceEmpId(context.getEmpId());
        candidate.setSourcePostId(context.getPostId());
        candidate.setEvidenceText(context.getEvidenceText());
        candidate.setOccurrenceCount(1);
        candidate.setStatus("PENDING");
        candidate.setSimilarTagId(similarTag.getId());
        candidate.setSimilarTagName(similarTag.getTagName());
        candidate.setSimilarityScore(BigDecimal.valueOf(similarity));

        candidate.setReasoning(String.format("中相似（相似度: %.2f），等待标签治理确认复用或拆分", similarity));

        Long candidateId = abilityTagCandidateService.addCandidate(candidate);

        log.info("中相似标签进入候选池: tagName={}, similarTag={}, similarity={}, candidateId={}",
                tagName, similarTag.getTagName(), similarity, candidateId);

        return TagAdmissionResult.candidate(candidate, "SIMILAR_REVIEW", null);
    }

    private AbilityTagCandidate createCandidateForSimilarTag(TagAdmissionContext context,
                                                             AbilityTag similarTag,
                                                             float similarity,
                                                             String reason) {
        String tagName = context.getNormalizedTagName();

        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setCandidateName(tagName);
        candidate.setTagCategory(context.getTagCategory() != null ? context.getTagCategory() : "TECHNICAL");
        candidate.setDomain(context.getDomain());
        candidate.setSourceType(context.getSourceType());
        candidate.setSourceRefId(context.getSourceRefId());
        candidate.setSourceEmpId(context.getEmpId());
        candidate.setSourcePostId(context.getPostId());
        candidate.setEvidenceText(context.getEvidenceText());
        candidate.setOccurrenceCount(1);
        candidate.setStatus("PENDING");
        candidate.setSimilarTagId(similarTag.getId());
        candidate.setSimilarTagName(similarTag.getTagName());
        candidate.setSimilarityScore(BigDecimal.valueOf(similarity));
        candidate.setReasoning(String.format("%s，相似度: %.2f，建议合并到: %s",
                reason, similarity, similarTag.getTagName()));

        Long candidateId = abilityTagCandidateService.addCandidate(candidate);
        candidate.setId(candidateId);

        log.info("创建相似标签候选: id={}, tagName={}, similarTag={}, similarity={}",
                candidateId, tagName, similarTag.getTagName(), similarity);

        return candidate;
    }

    private boolean isTrustedSource(String sourceType) {
        return isTaxonomySource(sourceType);
    }

    private boolean isTaxonomySource(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return false;
        }
        return switch (sourceType.toUpperCase()) {
            case "JD_IMPORT", "EXCEL_IMPORT", "MARKET_JD", "POST_EVOLUTION", "EXTERNAL_JD", "EMERGING_POST" -> true;
            default -> false;
        };
    }

    private AbilityTagCandidate createCandidateFromContext(TagAdmissionContext context,
                                                           String decisionType,
                                                           String defaultReason) {
        String candidateName = context.getNormalizedTagName() != null
                ? context.getNormalizedTagName() : context.getTagName();

        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setCandidateName(candidateName);
        candidate.setTagCategory(context.getTagCategory() != null ? context.getTagCategory() : "TECHNICAL");
        candidate.setDomain(context.getDomain());
        candidate.setSourceType(context.getSourceType());
        candidate.setSourceRefId(context.getSourceRefId());
        candidate.setSourceEmpId(context.getEmpId());
        candidate.setSourcePostId(context.getPostId());
        candidate.setEvidenceText(context.getEvidenceText());
        candidate.setOccurrenceCount(1);
        candidate.setStatus("PENDING");

        StringBuilder reasoning = new StringBuilder();
        if (decisionType != null) {
            reasoning.append("决策类型: ").append(decisionType);
        }
        if (defaultReason != null) {
            if (reasoning.length() > 0) reasoning.append("，");
            reasoning.append(defaultReason);
        }
        candidate.setReasoning(reasoning.toString());

        String searchName = candidateName;
        List<AbilityTag> similarTags = engine.findSimilarTags(searchName, 0.70);
        if (!similarTags.isEmpty()) {
            AbilityTag mostSimilar = similarTags.get(0);
            candidate.setSimilarTagId(mostSimilar.getId());
            candidate.setSimilarTagName(mostSimilar.getTagName());
            Float similarity = vectorOperations.calculateSimilarity(searchName, mostSimilar);
            if (similarity != null) {
                candidate.setSimilarityScore(BigDecimal.valueOf(similarity));
            }
        }

        Long candidateId = abilityTagCandidateService.addCandidate(candidate);
        candidate.setId(candidateId);

        log.info("创建候选标签: id={}, name={}, decisionType={}, source={}",
                candidateId, candidateName, decisionType, context.getSourceType());
        return candidate;
    }

    void saveAlias(Long tagId, String aliasName) {
        AbilityTagAlias existing = tagAliasMapper.selectOne(
                Wrappers.<AbilityTagAlias>lambdaQuery()
                        .eq(AbilityTagAlias::getTagId, tagId)
                        .eq(AbilityTagAlias::getAliasName, aliasName)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            AbilityTagAlias alias = new AbilityTagAlias();
            alias.setTagId(tagId);
            alias.setAliasName(aliasName);
            alias.setCreatedTime(java.time.LocalDateTime.now());
            tagAliasMapper.insert(alias);
            log.debug("保存标签别名: tagId={}, alias={}", tagId, aliasName);
        }
    }
}
