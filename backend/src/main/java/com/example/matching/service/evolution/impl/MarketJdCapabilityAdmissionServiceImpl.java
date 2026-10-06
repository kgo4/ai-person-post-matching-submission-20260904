package com.example.matching.service.evolution.impl;

import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.application.system.VerifiedAbilityTagAdmissionFacade;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 市场JD能力自动准入 —— 确定性门禁实现
 * <p>
 * 只做服务端可验证的过滤与路由：直接证据自动准入（AUTO_ACCEPT）、现有标签语义 defer、
 * 新能力双阈值分组。不调用 Harness、不直接更新 MarketJdData、不做持久化。
 * <p>
 * 别名/规范名比较仅做大小写与空白归一化，不使用向量相似度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketJdCapabilityAdmissionServiceImpl implements MarketJdCapabilityAdmissionService {

    private final MarketJdCapabilityAdmissionProperties properties;
    private final TagQueryPort tagQueryPort;
    private final AiTrustHarnessService harnessService;
    private final VerifiedAbilityTagAdmissionFacade verifiedAbilityTagAdmissionFacade;

    private static final String SOURCE_PREFIX = "source:MARKET_JD:";
    private static final String MARKET_JD_SCENARIO = "MARKET_JD_ABILITY_ADMISSION";
    private static final String CLAIM_TYPE_ABILITY_TAG = "ABILITY_TAG";
    private static final String SOURCE_TYPE_MARKET_JD = "MARKET_JD";
    private static final String EVIDENCE_SEPARATOR = " | ";
    private static final int MAX_GROUP_REPRESENTATIVES = 5;

    /** 共享 Micrometer 注册表（测试环境无 MeterRegistry 时为空，指标静默跳过） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Override
    public AdmissionGateResult evaluateGate(AdmissionBatchRequest request) {
        Map<Long, LinkedHashSet<Long>> autoAccepted = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<Long>> recommended = new LinkedHashMap<>();
        List<ExistingTagDeferredClaim> deferredExisting = new ArrayList<>();
        Map<String, GroupBuilder> newGroups = new LinkedHashMap<>();
        int rejected = 0;

        List<TagQueryPort.TagDTO> enabledTags = tagQueryPort.listActiveTags(0);
        Map<Long, TagQueryPort.TagDTO> enabledTagById = enabledTags == null ? Map.of()
                : enabledTags.stream().collect(Collectors.toMap(TagQueryPort.TagDTO::id, t -> t, (a, b) -> a));
        Map<Long, List<String>> aliasCache = new HashMap<>();

        if (request == null || request.jds() == null) {
            return new AdmissionGateResult(autoAccepted, recommended, deferredExisting, Map.of(), rejected);
        }

        for (JdExtraction jd : request.jds()) {
            if (jd == null || jd.jdId() == null || isBlank(jd.cleanedJdText()) || jd.items() == null) {
                if (jd != null && jd.items() != null) {
                    rejected += jd.items().size();
                }
                continue;
            }
            for (JdAbilityItemDTO item : jd.items()) {
                MarketJdAbilityClaim claim = toClaim(request.batchNo(), jd, item);
                if (claim == null) {
                    rejected++;
                    continue;
                }
                String status = claim.matchStatus();
                if ("MATCHED".equals(status) || "SIMILAR".equals(status)) {
                    TagQueryPort.TagDTO tag = enabledTagById.get(claim.matchedTagId());
                    if (tag == null) {
                        // 禁用/不存在标签：拒绝，不得进入 Harness
                        rejected++;
                        continue;
                    }
                    if (properties.isDirectEvidenceAutoAdmit()
                            && directEvidenceHit(tag, aliasCache, claim.evidenceText())) {
                        autoAccepted.computeIfAbsent(jd.jdId(), k -> new LinkedHashSet<>()).add(tag.id());
                    } else if (isHighConfidenceSemanticRecommendation(claim)) {
                        recommended.computeIfAbsent(jd.jdId(), k -> new LinkedHashSet<>()).add(tag.id());
                    } else {
                        deferredExisting.add(new ExistingTagDeferredClaim(
                                claim.jdId(), claim.matchedTagId(), claim.suggestedName(),
                                claim.matchStatus(), claim.evidenceText(), claim.sourceRefs()));
                    }
                } else if ("NEW".equals(status)) {
                    groupNewAbility(newGroups, claim);
                } else {
                    rejected++;
                }
            }
        }

        // 新能力必须同时满足独立 JD 数与匿名招聘主体数：主体信息只用于确定性门禁，
        // 不会进入 Harness、Agent 上下文或任何前端返回值。
        Iterator<Map.Entry<String, GroupBuilder>> it = newGroups.entrySet().iterator();
        while (it.hasNext()) {
            GroupBuilder g = it.next().getValue();
            if (g.getDistinctJdCount() < properties.getNewAbilityMinJdCount()
                    || g.getDistinctCompanyCount() < properties.getNewAbilityMinCompanyCount()) {
                rejected += g.members.size();
                it.remove();
            }
        }

        Map<String, NewAbilityGroup> deferredNewAbilityGroups = newGroups.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build(), (a, b) -> a, LinkedHashMap::new));

        return new AdmissionGateResult(autoAccepted, recommended, deferredExisting, deferredNewAbilityGroups, rejected);
    }

    @Override
    public AdmissionPlan admitBatch(AdmissionBatchRequest request) {
        AdmissionGateResult gate = evaluateGate(request);
        PlanBuilder plan = new PlanBuilder();

        // 1. 直接证据自动准入直接进入最终集合（无需 Harness）
        for (Map.Entry<Long, LinkedHashSet<Long>> e : gate.autoAcceptedTagIdsByJd().entrySet()) {
            plan.accepted.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            plan.autoAcceptedCount += e.getValue().size();
        }
        for (Map.Entry<Long, LinkedHashSet<Long>> e : gate.recommendedTagIdsByJd().entrySet()) {
            plan.recommended.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }

        // 2. 构建统一 Harness 单元：现有标签 defer 每个一条 + 新能力组每组一条
        List<HarnessUnit> units = new ArrayList<>();
        for (ExistingTagDeferredClaim c : gate.deferredExistingTagClaims()) {
            units.add(new HarnessUnit(buildExistingTagClaim(c), UnitKind.EXISTING_TAG, c, null));
        }
        for (NewAbilityGroup g : gate.deferredNewAbilityGroups().values()) {
            units.add(new HarnessUnit(buildGroupClaim(g), UnitKind.NEW_GROUP, null, g));
        }

        // 3. 分批 verifyBatch（事务外；绝不 per-JD 循环调 verify）
        int batchSize = Math.max(1, properties.getHarnessBatchSize());
        Map<HarnessUnit, AiHarnessDecisionDTO> decisions = new java.util.IdentityHashMap<>();
        List<HarnessUnit> retryUnits = new ArrayList<>();
        collectDecisions(units, decisions, retryUnits, batchSize, false);

        // 4. RETRY 重试一次（仍走 verifyBatch；重试后非 PASS 一律丢弃）
        if (!retryUnits.isEmpty()) {
            List<HarnessUnit> stillRetry = new ArrayList<>();
            collectDecisions(retryUnits, decisions, stillRetry, batchSize, true);
            for (HarnessUnit u : stillRetry) {
                decisions.put(u, retryDecision(u));
            }
        }

        // 5. 按决策应用（PASS 仅精确作用于 jdId + evidenceText + matchedTagId）
        for (HarnessUnit u : units) {
            AiHarnessDecisionDTO d = decisions.get(u);
            if (u.kind == UnitKind.EXISTING_TAG) {
                applyExistingTagDecision(plan, u, d);
            } else {
                applyNewGroupDecision(plan, u, d);
            }
        }

        plan.rejectedClaimCount += gate.rejectedClaimCount();

        // ===== 结构化摘要日志 + 共享指标（Task 7）=====
        int totalUnits = units.size();
        log.info("[market-jd.admission] batchNo={}, extractedClaims={}, directAutoAdmitted={}, deferredExisting={}, "
                        + "newGroupsEligible={}, harnessPass={}, harnessReview={}, harnessBlock={}, harnessRetry={}, "
                        + "candidatesCreated={}, reviewGroupsSuppressed={}, rejectedClaims={}, acceptedTagsPerJd={}, infraFailedJds={}",
                request.batchNo(), totalUnits, plan.autoAcceptedCount, gate.deferredExistingTagClaims().size(),
                gate.deferredNewAbilityGroups().size(), plan.harnessPassCount,
                plan.existingReviewDroppedCount + plan.reviewCandidateGroupCount, plan.harnessBlockedCount,
                plan.harnessRetryDroppedCount, plan.reviewCandidateGroupCount, plan.suppressedGroupCount,
                plan.rejectedClaimCount,
                plan.accepted.values().stream().mapToInt(java.util.Set::size).sum(),
                plan.infraFailedJdIds.size());

        countRoute("DIRECT", plan.autoAcceptedCount);
        countRoute("HARNESS_PASS", plan.harnessPassCount);
        countRoute("BLOCK", plan.harnessBlockedCount);
        countRoute("REVIEW", plan.existingReviewDroppedCount + plan.reviewCandidateGroupCount);
        countRoute("REJECTED", plan.rejectedClaimCount);
        if (plan.suppressedGroupCount > 0 && meterRegistry != null) {
            counter("market_jd_capability_review_suppressed_total").increment(plan.suppressedGroupCount);
        }

        return new AdmissionPlan(plan.accepted, plan.recommended, plan.formalTagCreations,
                plan.autoAcceptedCount, plan.harnessPassCount, plan.harnessBlockedCount,
                plan.harnessRetryDroppedCount, plan.existingReviewDroppedCount,
                plan.reviewCandidateGroupCount, plan.rejectedClaimCount, plan.infraFailedJdIds);
    }

    /** 共享指标：route 计次（测试环境无 MeterRegistry 时静默跳过） */
    private void countRoute(String route, int count) {
        if (meterRegistry == null || count <= 0) {
            return;
        }
        counter("market_jd_capability_admission_total", route).increment(count);
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }

    private Counter counter(String name, String route) {
        return Counter.builder(name).tag("route", route).register(meterRegistry);
    }

    /** 分批调用 verifyBatch；RETRY 单元收集到 retrySink；响应基数不符视为基础设施失败（决策为 null） */
    private void collectDecisions(List<HarnessUnit> units, Map<HarnessUnit, AiHarnessDecisionDTO> sink,
                                  List<HarnessUnit> retrySink, int batchSize, boolean isRetryPass) {
        for (int i = 0; i < units.size(); i += batchSize) {
            List<HarnessUnit> batch = units.subList(i, Math.min(i + batchSize, units.size()));
            List<AiHarnessClaimDTO> claims = batch.stream().map(HarnessUnit::claim).toList();
            List<AiHarnessDecisionDTO> result = harnessService.verifyBatch(claims);
            if (result == null || result.size() != claims.size()) {
                // 基础设施失败：该批全部不准入，决策为 null（Harness 异常永不 admit）
                for (HarnessUnit u : batch) {
                    sink.put(u, null);
                }
                continue;
            }
            for (int j = 0; j < batch.size(); j++) {
                HarnessUnit u = batch.get(j);
                AiHarnessDecisionDTO d = result.get(j);
                if (!isRetryPass && d != null && "RETRY".equals(d.getDecision())
                        && properties.getHarnessRetryCount() > 0) {
                    retrySink.add(u);
                } else {
                    sink.put(u, d);
                }
            }
        }
    }

    /** 重试仍返回 RETRY：直接视为未准入（不再次调用 Harness） */
    private AiHarnessDecisionDTO retryDecision(HarnessUnit u) {
        AiHarnessDecisionDTO d = new AiHarnessDecisionDTO();
        d.setDecision("RETRY");
        d.setReasons(List.of("重试后仍为 RETRY，丢弃"));
        return d;
    }

    private void applyExistingTagDecision(PlanBuilder plan, HarnessUnit u, AiHarnessDecisionDTO d) {
        if (d == null) {
            plan.harnessBlockedCount++; // 基础设施失败，永不 admit
            if (u.claim.getSourceRefId() != null) {
                plan.infraFailedJdIds.add(u.claim.getSourceRefId());
            }
            return;
        }
        String decision = d.getDecision();
        if ("PASS".equals(decision)) {
            Long tagId = u.claim.getMatchedTagId();
            if (tagId != null) {
                plan.accepted.computeIfAbsent(u.claim.getSourceRefId(), k -> new LinkedHashSet<>()).add(tagId);
            }
            plan.harnessPassCount++;
        } else if ("REVIEW".equals(decision)) {
            plan.existingReviewDroppedCount++; // 现有标签 REVIEW：不加、不建候选
        } else if ("RETRY".equals(decision)) {
            plan.harnessRetryDroppedCount++;
        } else {
            plan.harnessBlockedCount++; // BLOCK 或未知
        }
    }

    private void applyNewGroupDecision(PlanBuilder plan, HarnessUnit u, AiHarnessDecisionDTO d) {
        NewAbilityGroup group = u.group;
        if (d == null) {
            plan.harnessBlockedCount++;
            plan.rejectedClaimCount += group.members().size();
            for (GroupMember m : group.members()) {
                plan.infraFailedJdIds.add(m.jdId());
            }
            return;
        }
        String decision = d.getDecision();
        switch (decision) {
            case "PASS" -> {
                BigDecimal score = d.getSupportScore();
                int minScore = properties.getNewAbilityPassMinScore();
                if (score != null && score.compareTo(BigDecimal.valueOf(minScore)) >= 0) {
                    TagAdmissionResult result = verifiedAbilityTagAdmissionFacade.admitVerifiedNewTag(
                            buildTagAdmissionContext(group), d);
                    Long resolvedTagId = result.getResolvedTagId();
                    if (resolvedTagId != null) {
                        plan.formalTagCreations.add(new FormalTagPlan(
                                resolvedTagId, group.normalizedAbilityName(), SOURCE_TYPE_MARKET_JD));
                        for (GroupMember m : group.members()) {
                            plan.accepted.computeIfAbsent(m.jdId(), k -> new LinkedHashSet<>()).add(resolvedTagId);
                        }
                        plan.harnessPassCount++;
                    } else if (result.getDecision() == TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED) {
                        plan.reviewCandidateGroupCount++;
                    } else {
                        plan.rejectedClaimCount += group.members().size();
                    }
                } else {
                    // PASS 但分数不足：进入候选（PASS_LOW_SCORE），不自动建正式标签
                    TagAdmissionResult result = verifiedAbilityTagAdmissionFacade.admitVerifiedNewTag(
                            buildTagAdmissionContext(group), d);
                    if (result.getDecision() == TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED) {
                        plan.reviewCandidateGroupCount++;
                    } else {
                        plan.rejectedClaimCount += group.members().size();
                    }
                }
            }
            case "REVIEW" -> {
                // 先检查 cap：超限的 REVIEW 组直接拒绝（不建候选、不自动通过）
                if (plan.reviewCandidateGroupCount < properties.getReviewMaxGroupsPerBatch()) {
                    TagAdmissionResult result = verifiedAbilityTagAdmissionFacade.admitVerifiedNewTag(
                            buildTagAdmissionContext(group), d);
                    if (result.getDecision() == TagAdmissionResult.AdmissionDecision.CANDIDATE_CREATED) {
                        plan.reviewCandidateGroupCount++;
                    } else {
                        plan.rejectedClaimCount += group.members().size();
                    }
                } else {
                    plan.suppressedGroupCount++;
                    plan.rejectedClaimCount += group.members().size();
                }
            }
            default -> {
                // BLOCK / RETRY（重试后仍）/ 未知：拒绝，不建候选
                if ("RETRY".equals(decision)) {
                    plan.harnessRetryDroppedCount++;
                } else {
                    plan.harnessBlockedCount++;
                }
                plan.rejectedClaimCount += group.members().size();
            }
        }
    }

    private AiHarnessClaimDTO buildExistingTagClaim(ExistingTagDeferredClaim c) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario(MARKET_JD_SCENARIO);
        claim.setClaimType(CLAIM_TYPE_ABILITY_TAG);
        claim.setClaimText(c.suggestedName());
        claim.setMatchedTagId(c.matchedTagId());
        claim.setSourceType(SOURCE_TYPE_MARKET_JD);
        claim.setSourceRefId(c.jdId());
        claim.setEvidenceText(c.evidenceText());
        claim.setSourceRefs(c.sourceRefs());
        return claim;
    }

    /**
     * 新能力组 claim：至多 5 个确定性代表成员（Task4 已按 companyKey -> jdId 排序），
     * 证据固定分隔符拼接，仅保留真实 source:MARKET_JD refs，sourceRefId 取第一个真实 JD。
     */
    private AiHarnessClaimDTO buildGroupClaim(NewAbilityGroup group) {
        List<GroupMember> reps = group.members().size() > MAX_GROUP_REPRESENTATIVES
                ? group.members().subList(0, MAX_GROUP_REPRESENTATIVES)
                : group.members();
        GroupMember first = reps.get(0);
        String evidence = reps.stream().map(GroupMember::evidenceText)
                .collect(Collectors.joining(EVIDENCE_SEPARATOR));
        List<String> refs = reps.stream().flatMap(m -> m.sourceRefs().stream()).distinct().toList();
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario(MARKET_JD_SCENARIO);
        claim.setClaimType(CLAIM_TYPE_ABILITY_TAG);
        claim.setClaimText(first.suggestedName());
        claim.setMatchedTagId(null);
        claim.setSourceType(SOURCE_TYPE_MARKET_JD);
        claim.setSourceRefId(first.jdId());
        claim.setEvidenceText(evidence);
        claim.setSourceRefs(refs);
        return claim;
    }

    private TagAdmissionContext buildTagAdmissionContext(NewAbilityGroup group) {
        GroupMember first = group.members().get(0);
        String evidence = group.members().stream()
                .limit(MAX_GROUP_REPRESENTATIVES)
                .map(GroupMember::evidenceText)
                .collect(Collectors.joining(EVIDENCE_SEPARATOR));
        return TagAdmissionContext.builder()
                .tagName(first.suggestedName())
                .tagCategory("TECHNICAL")
                .sourceType(SOURCE_TYPE_MARKET_JD)
                .sourceRefId(first.jdId())
                .evidenceText(evidence)
                .contextText(evidence)
                .build();
    }

    /**
     * 构建内部不可变 claim；违反任何证据或来源规则返回 null（拒绝，不产生 review 项）
     */
    private MarketJdAbilityClaim toClaim(String batchNo, JdExtraction jd, JdAbilityItemDTO item) {
        String evidence = item.getEvidenceText();
        if (isBlank(evidence) || isBlank(item.getSuggestedName())) {
            return null;
        }
        String cleanedText = jd.cleanedJdText();
        // 证据位置校验：若同时给出 start/end，必须精确对应同一子串；否则证据必须出现在清洗文本中
        if (item.getEvidenceStart() != null && item.getEvidenceEnd() != null) {
            int start = item.getEvidenceStart();
            int end = item.getEvidenceEnd();
            if (start < 0 || end < start || end > cleanedText.length()
                    || !cleanedText.substring(start, end).equals(evidence)) {
                return null;
            }
        } else if (!cleanedText.contains(evidence)) {
            return null;
        }
        // 来源校验：必须含自身 source:MARKET_JD:<jdId>，且不得含有服务端生成列表之外的引用
        List<String> sourceRefs = item.getSourceRefs();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return null;
        }
        String selfRef = SOURCE_PREFIX + jd.jdId();
        if (!sourceRefs.contains(selfRef)) {
            return null;
        }
        Set<String> allowed = jd.serverGeneratedRefs() == null ? Set.of() : new HashSet<>(jd.serverGeneratedRefs());
        if (!allowed.containsAll(sourceRefs)) {
            return null;
        }
        return new MarketJdAbilityClaim(
                jd.jdId(), batchNo, normalize(item.getSuggestedName()),
                item.getMatchedTagId(), item.getMatchStatus(), evidence,
                item.getEvidenceStart(), item.getEvidenceEnd(),
                List.copyOf(sourceRefs), cleanedText,
                jd.companyDiversityKey(), item.getSuggestedName(), item.getConfidenceScore(), item.getSimilarityScore());
    }

    /**
     * 直接证据命中判定：归一化后的证据文本包含规范标签名或任一受控别名
     */
    private boolean directEvidenceHit(TagQueryPort.TagDTO tag, Map<Long, List<String>> aliasCache, String evidenceText) {
        String normEvidence = normalize(evidenceText);
        if (normEvidence.contains(normalize(tag.tagName()))) {
            return true;
        }
        for (String alias : aliasesOf(tag.id(), aliasCache)) {
            if (normEvidence.contains(normalize(alias))) {
                return true;
            }
        }
        return false;
    }

    private boolean isHighConfidenceSemanticRecommendation(MarketJdAbilityClaim claim) {
        return "SIMILAR".equals(claim.matchStatus())
                && claim.similarityScore() != null
                && claim.similarityScore() >= properties.getSemanticRecommendationMinScore();
    }

    private List<String> aliasesOf(Long tagId, Map<Long, List<String>> cache) {
        return cache.computeIfAbsent(tagId, id -> {
            List<String> aliases = tagQueryPort.listAliases(id);
            if (aliases == null) {
                return List.of();
            }
            return aliases.stream()
                    .filter(a -> !isBlank(a))
                    .map(String::trim)
                    .toList();
        });
    }

    private void groupNewAbility(Map<String, GroupBuilder> groups, MarketJdAbilityClaim claim) {
        String normalizedName = claim.normalizedAbilityName();
        GroupBuilder builder = groups.computeIfAbsent(normalizedName, GroupBuilder::new);
        builder.add(new GroupMember(claim.jdId(), claim.companyDiversityKey(),
                claim.suggestedName(), claim.evidenceText(), claim.sourceRefs()));
    }

    /** 仅大小写与空白归一化（比较用途），不使用向量相似度 */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 新能力分组可变构建器（成员按 companyDiversityKey -> jdId 确定性排序，供 Harness 取代表成员）
     */
    private static final class GroupBuilder {
        private final String normalizedName;
        private final List<GroupMember> members = new ArrayList<>();
        private final Set<String> seenMemberKeys = new HashSet<>();
        private final Set<Long> jdIds = new LinkedHashSet<>();
        private final Set<String> companyKeys = new LinkedHashSet<>();

        private GroupBuilder(String normalizedName) {
            this.normalizedName = normalizedName;
        }

        private void add(GroupMember member) {
            String key = String.valueOf(member.companyDiversityKey()) + '\u0000' + member.jdId();
            if (seenMemberKeys.add(key)) {
                members.add(member);
                jdIds.add(member.jdId());
                if (!isBlank(member.companyDiversityKey())) {
                    companyKeys.add(member.companyDiversityKey());
                }
            }
        }

        private int getDistinctJdCount() {
            return jdIds.size();
        }

        private int getDistinctCompanyCount() {
            return companyKeys.size();
        }

        private NewAbilityGroup build() {
            members.sort(Comparator
                    .comparing(GroupMember::companyDiversityKey, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(GroupMember::jdId));
            return new NewAbilityGroup(normalizedName, List.copyOf(members), jdIds.size(), companyKeys.size());
        }
    }

    /**
     * 市场JD能力主张（内存态，仅本特性使用，不落表）
     */
    record MarketJdAbilityClaim(
            Long jdId,
            String batchNo,
            String normalizedAbilityName,
            Long matchedTagId,
            String matchStatus,
            String evidenceText,
            Integer evidenceStart,
            Integer evidenceEnd,
            List<String> sourceRefs,
            String cleanedJdText,
            String companyDiversityKey,
            String suggestedName,
            BigDecimal confidenceScore,
            Double similarityScore) {
    }

    /** Harness 单元类型 */
    private enum UnitKind {
        /** 现有标签语义 defer（每 item 一条 claim，matchedTagId 非空） */
        EXISTING_TAG,
        /** 新能力组（每组一条聚合 claim，matchedTagId 为空） */
        NEW_GROUP
    }

    /** 统一的 Harness 决策单元（claim 与来源信息的关联） */
    private record HarnessUnit(AiHarnessClaimDTO claim, UnitKind kind,
                               ExistingTagDeferredClaim existing, NewAbilityGroup group) {
    }

    /** 批次准入计划累积器 */
    private static final class PlanBuilder {
        final Map<Long, LinkedHashSet<Long>> accepted = new HashMap<>();
        final Map<Long, LinkedHashSet<Long>> recommended = new HashMap<>();
        final List<FormalTagPlan> formalTagCreations = new ArrayList<>();
        final Set<Long> infraFailedJdIds = new HashSet<>();
        int suppressedGroupCount;
        int autoAcceptedCount;
        int harnessPassCount;
        int harnessBlockedCount;
        int harnessRetryDroppedCount;
        int existingReviewDroppedCount;
        int reviewCandidateGroupCount;
        int rejectedClaimCount;
    }
}
