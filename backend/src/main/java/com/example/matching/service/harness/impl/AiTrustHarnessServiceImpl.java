package com.example.matching.service.harness.impl;

import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.source.SourceRefValidationResult;
import com.example.matching.common.trace.TraceContext;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.evolution.MarketJdQueryPort;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.utils.ScoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AiTrustHarnessServiceImpl implements AiTrustHarnessService {

    private static final String REVIEW_PENDING = "PENDING";
    private static final String REVIEW_AUTO_PASSED = "AUTO_PASSED";
    private static final String TYPE_ABILITY_TAG = "ABILITY_TAG";

    // 市场JD能力自动准入场景（仅该场景允许分组市场新能力在满足多源/多公司条件后走向 PASS）
    private static final String SCENARIO_MARKET_JD_ABILITY_ADMISSION = "MARKET_JD_ABILITY_ADMISSION";

    private static final String MARKET_JD_REF_PREFIX = "source:MARKET_JD:";

    // ==================== 支持分权重（决定原始支持分的组成） ====================
    /** 命中正式能力标签的加分：声明匹配已有正式标签，是最强的锚定信号 */
    private static final int SCORE_MATCHED_TAG = 45;
    /** 原始证据文本存在的加分（仅凭文本不足以自动 PASS，需配合 sourceRef 真实性校验） */
    private static final int SCORE_EVIDENCE_PRESENT = 30;
    /** 至少一条可解析 sourceRef 的加分 */
    private static final int SCORE_ACCEPTED_SOURCE_REFS = 20;
    /** RAG 分块存在的加分 */
    private static final int SCORE_RAG_CHUNKS = 10;
    /** 命中相似标签的加分 */
    private static final int SCORE_SIMILAR_TAG = 10;

    // ==================== 决策 ↔ 分数带（保证支持分忠实反映决策，而非装饰） ====================
    /** BLOCK / RETRY 的分数上限：拒绝类决策的支持分不得超过该值 */
    private static final int REJECT_MAX_SCORE = 49;
    /** REVIEW 的分数下限 */
    private static final int REVIEW_MIN_SCORE = 50;
    /** REVIEW 的分数上限（恒低于 PASS 阈值，消除"分数与决策脱钩"） */
    private static final int REVIEW_MAX_SCORE = 69;
    /** PASS 的分数下限 */
    private static final int PASS_MIN_SCORE = 70;
    /** PASS 的分数上限 */
    private static final int PASS_MAX_SCORE = 100;
    /** 强 PASS 最低分：对齐 AbilityTagAdmissionPipeline.HARNESS_PASS_SCORE_THRESHOLD，可被自动建正式标签 */
    private static final int STRONG_PASS_MIN_SCORE = 80;

    // 新增场景常量
    private static final String SCENARIO_POST_DYNAMIC_EVOLUTION = "POST_DYNAMIC_EVOLUTION";
    private static final String SCENARIO_POST_EVOLUTION = "POST_EVOLUTION";
    private static final String SCENARIO_EMERGING_POST_DISCOVERY = "EMERGING_POST_DISCOVERY";
    private static final String SCENARIO_POST_ABILITY_CHANGE = "POST_ABILITY_CHANGE";
    private static final String SCENARIO_CLOUD_KNOWLEDGE_EVOLUTION = "CLOUD_KNOWLEDGE_EVOLUTION";
    private static final String SCENARIO_INDUSTRY_TREND_ANALYSIS = "INDUSTRY_TREND_ANALYSIS";
    private static final String SCENARIO_PERSON_ABILITY_EXTRACTION = "PERSON_ABILITY_EXTRACTION";
    private static final String SCENARIO_PERSON_ABILITY = "PERSON_ABILITY";
    private static final String SCENARIO_POST_ABILITY = "POST_ABILITY";

    // 新增声明类型常量
    private static final String CLAIM_TYPE_EMERGING_POST = "EMERGING_POST";
    private static final String CLAIM_TYPE_POST_ABILITY_CHANGE = "POST_ABILITY_CHANGE";
    private static final String CLAIM_TYPE_POST_TASK_CHANGE = "POST_TASK_CHANGE";
    private static final String CLAIM_TYPE_POST_TOOL_CHANGE = "POST_TOOL_CHANGE";
    private static final String CLAIM_TYPE_POST_HARD_CONDITION_CHANGE = "POST_HARD_CONDITION_CHANGE";

    // 高影响变更类型（必须 REVIEW）
    private static final List<String> HIGH_IMPACT_CHANGE_TYPES = List.of(
            "REMOVE_ABILITY", "REMOVE_HARD_CONDITION", "ADD_HARD_CONDITION",
            "UPGRADE_LEVEL", "DOWNGRADE_LEVEL");

    private static final List<String> AI_DERIVED_SOURCE_TYPES = List.of(
            "ABILITY_TAG", "AI_CANDIDATE", "AI_GENERATED", "RAG_SUMMARY");

    private ObjectProvider<AiHarnessCheckLogMapper> logMapperProvider;
    private ObjectProvider<ObjectMapper> objectMapperProvider;
    private ObjectProvider<AiContextSourceRefService> sourceRefServiceProvider;
    private ObjectProvider<MarketJdQueryPort> marketJdQueryPortProvider;

    /** 市场 JD 能力自动准入配置（可选，用于对齐 PASS 阈值而非硬编码） */
    @Autowired(required = false)
    private MarketJdCapabilityAdmissionProperties marketJdAdmissionProperties;

    @Autowired(required = false)
    private ObjectProvider<AbilityTagMapper> abilityTagMapperProvider;

    @Autowired
    public AiTrustHarnessServiceImpl(ObjectProvider<AiHarnessCheckLogMapper> logMapperProvider,
                                     ObjectProvider<ObjectMapper> objectMapperProvider,
                                     ObjectProvider<AiContextSourceRefService> sourceRefServiceProvider,
                                     ObjectProvider<MarketJdQueryPort> marketJdQueryPortProvider) {
        this.logMapperProvider = logMapperProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.sourceRefServiceProvider = sourceRefServiceProvider;
        this.marketJdQueryPortProvider = marketJdQueryPortProvider;
    }

    @Override
    public AiHarnessDecisionDTO verify(AiHarnessClaimDTO claim) {
        AiHarnessDecisionDTO decision = decide(claim);
        decision.setClaimGroupId(claim != null ? claim.getClaimGroupId() : null);
        persist(claim, decision);
        return decision;
    }

    @Override
    public List<AiHarnessDecisionDTO> verifyBatch(List<AiHarnessClaimDTO> claims) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        List<AiHarnessDecisionDTO> decisions = new ArrayList<>(claims.size());
        for (AiHarnessClaimDTO claim : claims) {
            AiHarnessDecisionDTO decision;
            try {
                decision = decide(claim);
            } catch (Exception e) {
                // 单条校验异常隔离：降级为 RETRY，不让异常冒泡中断整批
                log.warn("Harness 单条校验异常，降级为 RETRY: claimGroupId={}, error={}",
                        claim != null ? claim.getClaimGroupId() : null, e.getMessage());
                AiHarnessDecisionDTO retry = new AiHarnessDecisionDTO();
                retry.setCheckCode("HNS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
                retry.setTraceId(TraceContext.getOrNull());
                retry.setMatchedTagId(claim != null ? claim.getMatchedTagId() : null);
                retry.setSimilarTagId(claim != null ? claim.getSimilarTagId() : null);
                decision = finish(retry, AiHarnessDecisionDTO.RETRY, "HIGH", 0, false,
                        List.of("harness verification failed unexpectedly; retry"));
            }
            decision.setClaimGroupId(claim != null ? claim.getClaimGroupId() : null);
            decisions.add(decision);
        }
        for (int index = 0; index < claims.size(); index++) {
            persist(claims.get(index), decisions.get(index));
        }
        return decisions;
    }

    private AiHarnessDecisionDTO decide(AiHarnessClaimDTO claim) {
        AiHarnessDecisionDTO decision = new AiHarnessDecisionDTO();
        decision.setCheckCode("HNS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        decision.setMatchedTagId(claim != null ? claim.getMatchedTagId() : null);
        decision.setSimilarTagId(claim != null ? claim.getSimilarTagId() : null);
        decision.setTraceId(TraceContext.getOrNull());

        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (claim == null || isBlank(claim.getClaimText())) {
            reasons.add("claimText is empty");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", 0, false, reasons);
        }

        boolean hasRefs = claim.hasSourceRefs();
        boolean hasEvidence = claim.hasEvidence();
        boolean hasRagChunks = claim.hasRagChunks();
        boolean selfEvidence = isSelfEvidence(claim);

        if (selfEvidence) {
            reasons.add("self evidence or AI-derived source cannot prove the claim");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, true, reasons);
        }

        // 校验sourceRefs格式
        List<String> acceptedSourceRefs = new ArrayList<>();
        List<String> invalidSourceRefs = new ArrayList<>();
        List<String> unverifiableSourceRefs = new ArrayList<>();
        if (hasRefs) {
            validateSourceRefs(claim.getSourceRefs(), acceptedSourceRefs, invalidSourceRefs, unverifiableSourceRefs);
            decision.setAcceptedSourceRefs(acceptedSourceRefs);
            decision.setInvalidSourceRefs(invalidSourceRefs);
            decision.setUnverifiableSourceRefs(unverifiableSourceRefs);
        }

        // 检查缺失的证据
        List<String> missingEvidence = new ArrayList<>();
        if (!hasEvidence) {
            missingEvidence.add("evidenceText");
        }
        if (!hasRefs) {
            missingEvidence.add("sourceRefs");
        }
        decision.setMissingEvidence(missingEvidence);

        if (claim.getMatchedTagId() != null) {
            score += SCORE_MATCHED_TAG;
            reasons.add("matched formal tag");
        }
        if (hasEvidence) {
            score += SCORE_EVIDENCE_PRESENT;
            reasons.add("original evidence text present");
        }

        // 综合差距诊断场景（MATCH_GAP_DIAGNOSIS）：不再做前缀放行特判。
        // 事实包引用的 fact:/evidence: 前缀由 AiContextSourceRefService 统一解析（EMP_ABILITY/
        // POST_ABILITY_MODEL/MATCHING_RECORD 等实体均受支持），一律走下方标准 fail-closed 流程：
        // 引用可解析 -> accepted；引用不存在 -> invalid/BLOCK；resolver 故障 -> unverifiable/RETRY。
        // 删除原因：原实现仅按字符串前缀即 PASS(80)，不校验引用真实性，可被伪造声明绕过。

        if (hasRefs && acceptedSourceRefs.isEmpty()) {
            if (!unverifiableSourceRefs.isEmpty()) {
                // 依赖故障（resolver 异常/服务不可用）fail-closed：不得以 REVIEW 放行
                reasons.add("all sourceRefs are unverifiable; fail closed with RETRY");
                decision.setUnverifiableSourceRefs(unverifiableSourceRefs);
                return finish(decision, AiHarnessDecisionDTO.RETRY, "HIGH", score, false, reasons);
            }
            // 所有sourceRefs都无效（NOT_FOUND/UNAUTHORIZED/UNSUPPORTED 永久BLOCK，仅人工复核后可重新提交）
            reasons.add("all sourceRefs are invalid");
            decision.setInvalidSourceRefs(invalidSourceRefs);
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        } else if (hasRefs) {
            score += SCORE_ACCEPTED_SOURCE_REFS;
            reasons.add("sourceRefs present");
        }
        if (hasRagChunks) {
            score += SCORE_RAG_CHUNKS;
            reasons.add("RAG chunks present");
        }
        if (claim.getSimilarTagId() != null) {
            score += SCORE_SIMILAR_TAG;
            reasons.add("similar formal tag found");
        }

        if (isPersonnelAbilityClaim(claim)) {
            return decidePersonnelEvidence(claim, decision, score, reasons, hasEvidence,
                    hasRefs, acceptedSourceRefs, invalidSourceRefs, unverifiableSourceRefs);
        }

        // 岗位演化场景特殊处理
        if (isPostEvolutionScenario(claim.getScenario())) {
            return handlePostEvolutionScenario(claim, decision, score, reasons, hasEvidence, hasRefs, acceptedSourceRefs);
        }

        // AI面试能力观察场景特殊处理
        if (SourceRefConstants.SCENARIO_AI_INTERVIEW_OBSERVATION.equals(claim.getScenario())) {
            return handleAIInterviewObservationScenario(claim, decision, score, reasons, hasEvidence, hasRefs, acceptedSourceRefs);
        }

        // 市场JD新能力分组准入场景（仅 MARKET_JD_ABILITY_ADMISSION）：
        // 在通用 ABILITY_TAG 无 matchedTagId 强制 REVIEW 之前，先检查多JD/多公司分组证据是否充分。
        // 其他场景（JD_ABILITY_EXTRACT、简历、面试等）的无标签行为不受影响。
        if (SCENARIO_MARKET_JD_ABILITY_ADMISSION.equals(claim.getScenario())
                && TYPE_ABILITY_TAG.equals(claim.getClaimType())
                && claim.getMatchedTagId() == null) {
            return handleMarketJdNewAbility(claim, decision, score, reasons,
                    hasEvidence, hasRefs, acceptedSourceRefs, invalidSourceRefs, unverifiableSourceRefs);
        }

        if (isAbilityExtractionScenario(claim.getScenario()) && claim.getMatchedTagId() == null) {
            if (hasEvidence && hasRefs) {
                reasons.add("ability extraction must match a formal ability tag before automatic admission");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
            }
            reasons.add("ability extraction lacks a formal ability tag or original evidence");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        if (TYPE_ABILITY_TAG.equals(claim.getClaimType()) && claim.getMatchedTagId() == null) {
            if (hasEvidence && hasRefs) {
                reasons.add("new ability has evidence and should enter candidate governance");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
            }
            reasons.add("new ability lacks original evidence");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        if (score >= PASS_MIN_SCORE) {
            if (!unverifiableSourceRefs.isEmpty()) {
                reasons.add("unverifiable sourceRefs block automatic pass");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "HIGH", score, false, reasons);
            }
            if (acceptedSourceRefs.isEmpty()) {
                // 修复：自动 PASS 必须至少有 1 条可验证的 sourceRef 锚定，防止 matchedTag + evidenceText
                // （自由文本，未经真实性校验）绕过引用校验直通 PASS。
                reasons.add("no verifiable sourceRef anchors the claim; cannot auto-pass");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
            }
            return finish(decision, AiHarnessDecisionDTO.PASS, "LOW", score, false, reasons);
        }
        if (score >= REVIEW_MIN_SCORE) {
            reasons.add("support is partial and requires review");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        reasons.add("insufficient support");
        return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
    }

    /**
     * 市场JD新能力分组准入：仅当非空证据、至少 2 个不同的有效 source:MARKET_JD 引用、
     * 解析出的引用覆盖至少 2 个不同 companyDiversityKey、且不存在无效/不可验证引用时，
     * 才允许走向 PASS（分数对齐准入阈值 80，供 AbilityTagAdmissionPipeline 直接建正式标签）。
     * 任一条件不满足则回退到标准新能力行为（REVIEW/BLOCK），绝不无条件放行。
     */
    private AiHarnessDecisionDTO handleMarketJdNewAbility(AiHarnessClaimDTO claim,
                                                           AiHarnessDecisionDTO decision,
                                                           int score,
                                                           List<String> reasons,
                                                           boolean hasEvidence,
                                                           boolean hasRefs,
                                                           List<String> acceptedSourceRefs,
                                                           List<String> invalidSourceRefs,
                                                           List<String> unverifiableSourceRefs) {
        // 前置条件一：非空证据 + 至少 2 个不同的有效 MARKET_JD 引用
        if (!hasEvidence || !hasRefs || acceptedSourceRefs.size() < 2) {
            return reviewOrBlockNewAbility(claim, decision, score, reasons, hasEvidence, hasRefs);
        }
        // 前置条件二：不允许存在无效或不可验证的引用（fail-closed）
        if (!invalidSourceRefs.isEmpty() || !unverifiableSourceRefs.isEmpty()) {
            reasons.add("market JD new ability has invalid or unverifiable source refs; needs review");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }
        // 前置条件三：解析出的引用至少覆盖 2 个不同的 companyDiversityKey
        Set<String> companyKeys = resolveCompanyDiversityKeys(acceptedSourceRefs);
        if (companyKeys.size() < 2) {
            reasons.add("market JD new ability requires at least two distinct company diversity keys");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }
        // 全部前置条件满足：多JD/多公司分组证据充分，PASS（支持分对齐可配置准入阈值，而非硬编码 80）
        reasons.add("grouped market JD new ability grounded by multi-company evidence");
        decision.setDecisionRule("MARKET_JD_GROUPED_MULTI_COMPANY");
        int passScore = marketJdAdmissionProperties != null
                ? marketJdAdmissionProperties.getNewAbilityPassMinScore()
                : STRONG_PASS_MIN_SCORE;
        return finish(decision, AiHarnessDecisionDTO.PASS, "LOW", passScore, false, reasons);
    }

    /** 与通用 ABILITY_TAG 无标签分支一致的 REVIEW/BLOCK 行为 */
    private AiHarnessDecisionDTO reviewOrBlockNewAbility(AiHarnessClaimDTO claim,
                                                          AiHarnessDecisionDTO decision,
                                                          int score,
                                                          List<String> reasons,
                                                          boolean hasEvidence,
                                                          boolean hasRefs) {
        if (hasEvidence && hasRefs) {
            reasons.add("new ability has evidence and should enter candidate governance");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }
        reasons.add("new ability lacks original evidence");
        return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
    }

    /** 从已接受的 source:MARKET_JD 引用中解析不同 companyDiversityKey 的数量 */
    private Set<String> resolveCompanyDiversityKeys(List<String> acceptedSourceRefs) {
        Set<String> keys = new HashSet<>();
        if (acceptedSourceRefs == null) {
            return keys;
        }
        MarketJdQueryPort port = marketJdQueryPortProvider != null
                ? marketJdQueryPortProvider.getIfAvailable() : null;
        if (port == null) {
            return keys;
        }
        for (String ref : acceptedSourceRefs) {
            if (ref == null || !ref.startsWith(MARKET_JD_REF_PREFIX)) {
                continue;
            }
            Long jdId = parseMarketJdId(ref);
            if (jdId == null) {
                continue;
            }
            try {
                String companyKey = port.getCompanyDiversityKey(jdId);
                if (companyKey != null && !companyKey.isBlank()) {
                    keys.add(companyKey);
                }
            } catch (Exception e) {
                // fail-closed：依赖故障时不把该引用计入多公司证据，避免异常冒泡中断整批
                log.warn("解析市场JD公司归属失败，跳过该引用: jdId={}, error={}", jdId, e.getMessage());
            }
        }
        return keys;
    }

    private Long parseMarketJdId(String ref) {
        String[] parts = ref.split(":");
        if (parts.length < 3) {
            return null;
        }
        try {
            return Long.valueOf(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAbilityExtractionScenario(String scenario) {
        return SCENARIO_PERSON_ABILITY.equals(scenario)
                || SCENARIO_POST_ABILITY.equals(scenario)
                || SCENARIO_PERSON_ABILITY_EXTRACTION.equals(scenario);
    }

    private boolean isPersonnelAbilityClaim(AiHarnessClaimDTO claim) {
        return claim != null && ("EMP_ABILITY".equals(claim.getClaimType())
                || "PERSON_ABILITY".equals(claim.getClaimType())
                || SCENARIO_PERSON_ABILITY.equals(claim.getScenario())
                || SCENARIO_PERSON_ABILITY_EXTRACTION.equals(claim.getScenario())
                || "PERSON_ABILITY_AGGREGATE".equals(claim.getScenario()));
    }

    /**
     * 人员能力证据包判定：只依据真实引用、原文片段和来源类型，不使用 Harness 分数阈值。
     */
    private AiHarnessDecisionDTO decidePersonnelEvidence(AiHarnessClaimDTO claim,
                                                          AiHarnessDecisionDTO decision,
                                                          int score,
                                                          List<String> reasons,
                                                          boolean hasEvidence,
                                                          boolean hasRefs,
                                                          List<String> acceptedSourceRefs,
                                                          List<String> invalidSourceRefs,
                                                          List<String> unverifiableSourceRefs) {
        if (!hasEvidence) {
            reasons.add("person ability claim has no original evidence");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }
        if (!hasRefs) {
            reasons.add("person ability claim has evidence but no traceable sourceRef; manual review required");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }
        if (acceptedSourceRefs.isEmpty()) {
            if (!invalidSourceRefs.isEmpty() || !unverifiableSourceRefs.isEmpty()) {
                reasons.add("person ability evidence exists but sourceRefs cannot be fully verified; manual review required");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "HIGH", score, false, reasons);
            }
            reasons.add("person ability claim has no usable evidence reference");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        int groundedRefs = 0;
        int trustedRefs = 0;
        int boundRefs = 0;
        AiContextSourceRefService sourceRefService = sourceRefServiceProvider.getIfAvailable();
        if (sourceRefService != null) {
            for (String ref : acceptedSourceRefs) {
                try {
                    AiContextSourceRefService.ResolveOutcome outcome = sourceRefService.resolveWithStatus(ref);
                    AiContextSourceRefDTO resolved = outcome != null ? outcome.resolved() : null;
                    if (resolved != null && resolved.getSnippet() != null && !resolved.getSnippet().isBlank()) {
                        groundedRefs++;
                        if (supportsPersonnelAbility(claim, resolved.getSnippet())) {
                            boundRefs++;
                        }
                    }
                    if (resolved != null && isTrustedPersonnelSource(resolved.getSourceType())) {
                        trustedRefs++;
                    }
                } catch (Exception ignored) {
                    // 初次校验已接受的引用不会因二次详情读取异常而伪造 PASS，落入 REVIEW。
                }
            }
        }

        boolean allAcceptedRefsGrounded = groundedRefs == acceptedSourceRefs.size();
        boolean allAcceptedRefsTrusted = trustedRefs == acceptedSourceRefs.size();
        boolean hasExplicitAbilityBinding = boundRefs > 0;
        if (allAcceptedRefsGrounded && allAcceptedRefsTrusted && hasExplicitAbilityBinding && invalidSourceRefs.isEmpty()
                && unverifiableSourceRefs.isEmpty()) {
            reasons.add("person ability evidence is traceable, source-grounded, and from trusted business sources");
            return finish(decision, AiHarnessDecisionDTO.PASS, "LOW", score, false, reasons);
        }

        reasons.add(hasExplicitAbilityBinding
                ? "person ability evidence is available but requires human evidence review"
                : "person ability evidence is traceable but the ability-to-evidence binding requires human review");
        return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
    }

    private boolean isTrustedPersonnelSource(String sourceType) {
        return sourceType != null && Set.of("RESUME_PARSE", "RESUME", "AI_TEST", "VIDEO_INTERVIEW",
                "AI_INTERVIEW", "PMS_ANALYSIS", "PROJECT", "MANUAL").contains(sourceType);
    }

    private boolean supportsPersonnelAbility(AiHarnessClaimDTO claim, String sourceSnippet) {
        String evidence = normalizeForEvidenceBinding(claim.getEvidenceText());
        String snippet = normalizeForEvidenceBinding(sourceSnippet);
        for (String term : personnelAbilityTerms(claim)) {
            String normalized = normalizeForEvidenceBinding(term);
            if (!normalized.isBlank() && (evidence.contains(normalized) || snippet.contains(normalized))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> personnelAbilityTerms(AiHarnessClaimDTO claim) {
        Set<String> terms = new HashSet<>();
        terms.add(claim.getClaimText());
        if (claim.getMatchedTagId() == null || abilityTagMapperProvider == null) {
            return terms;
        }
        AbilityTagMapper mapper = abilityTagMapperProvider.getIfAvailable();
        if (mapper == null) {
            return terms;
        }
        try {
            AbilityTag matchedTag = mapper.selectById(claim.getMatchedTagId());
            if (matchedTag == null) {
                return terms;
            }
            terms.add(matchedTag.getTagName());
            if (matchedTag.getCanonicalTagId() != null
                    && !matchedTag.getCanonicalTagId().equals(matchedTag.getId())) {
                AbilityTag canonicalTag = mapper.selectById(matchedTag.getCanonicalTagId());
                if (canonicalTag != null) {
                    terms.add(canonicalTag.getTagName());
                }
            }
        } catch (Exception e) {
            log.warn("加载人员能力规范标签失败，按声明能力名核验: tagId={}, err={}",
                    claim.getMatchedTagId(), e.getMessage());
        }
        return terms;
    }

    private String normalizeForEvidenceBinding(String text) {
        return AbilityNameNormalizer.normalize(text);
    }

    /**
     * 判断是否为岗位演化相关场景
     */
    private boolean isPostEvolutionScenario(String scenario) {
        return SCENARIO_POST_EVOLUTION.equals(scenario)
                || SCENARIO_POST_DYNAMIC_EVOLUTION.equals(scenario)
                || SCENARIO_EMERGING_POST_DISCOVERY.equals(scenario)
                || SCENARIO_POST_ABILITY_CHANGE.equals(scenario)
                || SCENARIO_CLOUD_KNOWLEDGE_EVOLUTION.equals(scenario)
                || SCENARIO_INDUSTRY_TREND_ANALYSIS.equals(scenario);
    }

    /**
     * 处理岗位演化场景的校验逻辑
     */
    private AiHarnessDecisionDTO handlePostEvolutionScenario(AiHarnessClaimDTO claim,
                                                               AiHarnessDecisionDTO decision,
                                                               int score,
                                                               List<String> reasons,
                                                               boolean hasEvidence,
                                                               boolean hasRefs,
                                                               List<String> acceptedSourceRefs) {
        // 1. 新兴岗位声明必须有证据和来源
        if (CLAIM_TYPE_EMERGING_POST.equals(claim.getClaimType())) {
            if (!hasEvidence || !hasRefs) {
                reasons.add("emerging post claim must have evidence and sourceRefs");
                return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
            }
            if (acceptedSourceRefs.size() < 2) {
                reasons.add("emerging post claim requires multiple source support");
                return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
            }
            reasons.add("emerging post claim has sufficient evidence");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        // 2. 高影响变更必须 REVIEW
        if (isHighImpactChange(claim.getChangeType(), claim.getClaimType())) {
            if (!hasEvidence || !hasRefs) {
                reasons.add("high impact change must have evidence and sourceRefs");
                return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
            }
            reasons.add("high impact change requires manual review");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "HIGH", score, false, reasons);
        }

        // 3. 单一来源支持重大变更需要 REVIEW
        if (!hasEvidence || !hasRefs) {
            reasons.add("evolution claim must have evidence and sourceRefs");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        if (hasEvidence && hasRefs && acceptedSourceRefs.size() == 1) {
            reasons.add("single source support requires review for evolution changes");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        // 4. 多来源支持且有证据可以 PASS
        if (hasEvidence && hasRefs && acceptedSourceRefs.size() >= 2) {
            reasons.add("multiple source support with evidence");
            return finish(decision, AiHarnessDecisionDTO.PASS, "LOW", score, false, reasons);
        }

        // 5. 默认需要审核
        reasons.add("evolution claim requires review");
        return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
    }

    /**
     * 判断是否为高影响变更
     */
    private boolean isHighImpactChange(String changeType, String claimType) {
        return (changeType != null && HIGH_IMPACT_CHANGE_TYPES.contains(changeType))
                || CLAIM_TYPE_POST_HARD_CONDITION_CHANGE.equals(changeType)
                || CLAIM_TYPE_POST_HARD_CONDITION_CHANGE.equals(claimType);
    }

    /**
     * 处理AI面试能力观察场景的校验逻辑
     * <p>
     * AI面试观察必须满足：
     * 1. 有 matchedTagId
     * 2. 有 evidenceText
     * 3. 有标准 sourceRefs
     * 4. sourceRefs 至少包含面试 session 或 question
     * 5. 如果没有回答证据，不能 PASS
     * 6. 如果只有 AI 自证，没有事实引用，BLOCK 或 REVIEW
     */
    private AiHarnessDecisionDTO handleAIInterviewObservationScenario(AiHarnessClaimDTO claim,
                                                                        AiHarnessDecisionDTO decision,
                                                                        int score,
                                                                        List<String> reasons,
                                                                        boolean hasEvidence,
                                                                        boolean hasRefs,
                                                                        List<String> acceptedSourceRefs) {
        // 1. 必须匹配到正式标签
        if (claim.getMatchedTagId() == null) {
            reasons.add("AI interview observation must match a formal ability tag");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        // 2. 必须有证据文本
        if (!hasEvidence) {
            reasons.add("AI interview observation lacks answer evidence");
            return finish(decision, AiHarnessDecisionDTO.BLOCK, "HIGH", score, false, reasons);
        }

        // 3. 必须有有效的来源引用
        if (!hasRefs || acceptedSourceRefs.isEmpty()) {
            reasons.add("AI interview observation lacks valid sourceRefs");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        // 4. 来源引用必须包含面试会话的事实引用
        boolean hasInterviewSession = acceptedSourceRefs.stream()
                .anyMatch(ref -> ref.contains(SourceRefConstants.ENTITY_INTERVIEW_SESSION));
        if (!hasInterviewSession) {
            reasons.add("AI interview observation must reference interview session");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        // 5. 来源引用必须包含问题或追问的事实引用（session只能证明有面试，不能证明某个能力判断）
        boolean hasQuestionOrFollowUp = acceptedSourceRefs.stream()
                .anyMatch(ref -> ref.contains(SourceRefConstants.ENTITY_INTERVIEW_QUESTION)
                        || ref.contains(SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP));
        if (!hasQuestionOrFollowUp) {
            reasons.add("AI interview observation must reference interview question or follow-up to prove ability judgment");
            return finish(decision, AiHarnessDecisionDTO.REVIEW, "MEDIUM", score, false, reasons);
        }

        // 6. 满足所有条件，PASS
        reasons.add("AI interview observation is grounded by interview question/follow-up evidence");
        return finish(decision, AiHarnessDecisionDTO.PASS, "LOW", score, false, reasons);
    }

    /**
     * 校验sourceRefs格式和存在性
     * <p>
     * 1. 检查格式是否有效
     * 2. 检查是否为标准格式（非废弃格式）
     * 3. 调用 AiContextSourceRefService.resolve() 验证引用是否真实存在
     * <p>
     * 安全默认：无法验证的引用不加入 accepted，进入 unverifiable 列表供决策层降级。
     */
    private void validateSourceRefs(List<String> sourceRefs, List<String> accepted, List<String> invalid,
                                     List<String> unverifiable) {
        if (sourceRefs == null) {
            return;
        }

        AiContextSourceRefService sourceRefService = sourceRefServiceProvider.getIfAvailable();

        for (String ref : sourceRefs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }

            // 1. 格式校验
            if (!SourceRefConstants.isValidFormat(ref)) {
                invalid.add(ref);
                log.warn("Invalid sourceRef format: {}", ref);
                continue;
            }

            // 2. 检查是否为废弃格式
            if (SourceRefConstants.isDeprecatedFormat(ref)) {
                invalid.add(ref);
                log.warn("Deprecated sourceRef format: {}", ref);
                continue;
            }

            // 3. 验证引用是否真实存在（结构化状态，fail-closed 语义）
            if (sourceRefService == null) {
                unverifiable.add(ref);
                log.warn("AiContextSourceRefService is not available, cannot verify sourceRef: {}", ref);
                continue;
            }

            try {
                AiContextSourceRefService.ResolveOutcome outcome = sourceRefService.resolveWithStatus(ref);
                switch (outcome.status()) {
                    case VALID -> {
                        accepted.add(ref);
                    }
                    case NOT_FOUND, UNAUTHORIZED, UNSUPPORTED -> {
                        invalid.add(ref);
                        log.warn("SourceRef not admissible ({}): {}", outcome.status(), ref);
                    }
                    case DEPENDENCY_ERROR -> {
                        unverifiable.add(ref);
                        log.warn("SourceRef resolve dependency error for ref {}: {}", ref, outcome.status());
                    }
                }
            } catch (Exception e) {
                unverifiable.add(ref);
                log.warn("SourceRef resolve error for ref {}: {}", ref, e.getMessage());
            }
        }
    }

    private AiHarnessDecisionDTO finish(AiHarnessDecisionDTO decision, String result, String riskLevel,
                                        int rawScore, boolean selfEvidence, List<String> reasons) {
        int clampedScore = normalizeScore(result, rawScore);
        decision.setDecision(result);
        decision.setRiskLevel(riskLevel);
        decision.setSupportScore(BigDecimal.valueOf(clampedScore));
        decision.setSelfEvidence(selfEvidence);
        decision.setReasons(reasons);
        return decision;
    }

    /**
     * 把原始支持分规范到决策对应的分数带，使"分数 ↔ 决策"严格一致：
     * PASS → [70, 100]、REVIEW → [50, 69]、BLOCK/RETRY → [0, 49]。
     * 强 PASS（≥ {@value #STRONG_PASS_MIN_SCORE}）由调用方显式传入表达，
     * 供下游 AbilityTagAdmissionPipeline 直接创建正式标签。
     */
    private int normalizeScore(String decision, int rawScore) {
        return switch (decision) {
            case AiHarnessDecisionDTO.PASS -> ScoreUtils.clamp(rawScore, PASS_MIN_SCORE, PASS_MAX_SCORE);
            case AiHarnessDecisionDTO.REVIEW -> ScoreUtils.clamp(rawScore, REVIEW_MIN_SCORE, REVIEW_MAX_SCORE);
            case AiHarnessDecisionDTO.BLOCK, AiHarnessDecisionDTO.RETRY ->
                    ScoreUtils.clamp(rawScore, 0, REJECT_MAX_SCORE);
            default -> ScoreUtils.clamp(rawScore, 0, 100);
        };
    }

    private boolean isSelfEvidence(AiHarnessClaimDTO claim) {
        if (claim == null) {
            return false;
        }
        if (claim.getSourceType() != null && AI_DERIVED_SOURCE_TYPES.contains(claim.getSourceType())) {
            return true;
        }
        if (claim.getSourceRefs() == null) {
            return false;
        }
        // 修复：仅按精确前缀识别 AI 自证来源，避免把 fact:ABILITY_TAG:{id} 这类指向
        // 正式标签表的标准事实引用误判为自证而 BLOCK。
        return claim.getSourceRefs().stream()
                .filter(ref -> ref != null)
                .anyMatch(ref -> ref.startsWith("ai:")
                        || ref.startsWith("generated:")
                        || ref.startsWith("rag:ABILITY_TAG"));
    }

    private void persist(AiHarnessClaimDTO claim, AiHarnessDecisionDTO decision) {
        if (logMapperProvider == null || decision == null) {
            return;
        }
        AiHarnessCheckLogMapper mapper = logMapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        try {
            AiHarnessCheckLog logRecord = new AiHarnessCheckLog();
            logRecord.setCheckCode(decision.getCheckCode());
            logRecord.setScenario(claim != null ? claim.getScenario() : null);
            logRecord.setClaimType(claim != null ? claim.getClaimType() : null);
            logRecord.setClaimText(claim != null ? claim.getClaimText() : null);
            logRecord.setSourceType(claim != null ? claim.getSourceType() : null);
            logRecord.setSourceRefId(claim != null ? claim.getSourceRefId() : null);
            logRecord.setEvidenceText(claim != null ? claim.getEvidenceText() : null);
            logRecord.setRagChunkIds(toJson(claim != null ? claim.getRagChunkIds() : null));
            logRecord.setSourceRefs(toJson(claim != null ? claim.getSourceRefs() : null));
            logRecord.setMatchedTagId(decision.getMatchedTagId());
            logRecord.setSimilarTagId(decision.getSimilarTagId());
            logRecord.setSupportScore(decision.getSupportScore());
            logRecord.setRiskLevel(decision.getRiskLevel());
            logRecord.setDecision(decision.getDecision());
            logRecord.setIsSelfEvidence(decision.isSelfEvidence() ? 1 : 0);
            logRecord.setReasonJson(toJson(decision.getReasons()));
            logRecord.setReviewStatus(resolveReviewStatus(decision.getDecision()));

            // 新增字段
            logRecord.setContextHash(claim != null ? claim.getContextHash() : null);
            logRecord.setContextSnapshotId(claim != null ? claim.getContextSnapshotId() : null);
            logRecord.setClaimPayloadJson(claim != null ? claim.getClaimPayloadJson() : null);
            logRecord.setAcceptedSourceRefs(toJson(decision.getAcceptedSourceRefs()));
            logRecord.setInvalidSourceRefs(toJson(decision.getInvalidSourceRefs()));
            logRecord.setMissingEvidenceJson(toJson(decision.getMissingEvidence()));
            logRecord.setBusinessTargetType(claim != null ? claim.getBusinessTargetType() : null);
            logRecord.setBusinessTargetId(claim != null ? claim.getBusinessTargetId() : null);
            logRecord.setBusinessApplyStatus("PENDING");
            logRecord.setLegacySupportScore(decision.getLegacySupportScore());
            logRecord.setLegacyDecision(decision.getLegacyDecision());
            logRecord.setDecisionRule(decision.getDecisionRule());
            logRecord.setTraceId(decision.getTraceId() != null
                    ? decision.getTraceId() : TraceContext.getOrNull());

            mapper.insert(logRecord);
        } catch (Exception e) {
            // 修复：审计日志落库失败改为 ERROR 级别（原为 warn 静默，审计链缺失不可感知）
            log.error("Failed to persist AI harness decision log (audit chain broken): checkCode={}, error={}",
                    decision != null ? decision.getCheckCode() : "unknown", e.getMessage(), e);
        }
    }

    private String resolveReviewStatus(String decision) {
        if (AiHarnessDecisionDTO.PASS.equals(decision)) {
            return REVIEW_AUTO_PASSED;
        }
        return REVIEW_PENDING;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        ObjectMapper mapper = objectMapperProvider != null ? objectMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
