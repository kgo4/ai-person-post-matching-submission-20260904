package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.post.EmergingPostDiscoveryDTO;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.rag.RagKnowledgeLayer;
import com.example.matching.service.post.EmergingPostDiscoveryService;
import com.example.matching.service.post.support.PmiCommunityDetector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers market skill communities from governed MarketJdData. The detector deliberately
 * degrades to observation mode on small data rather than presenting weak samples as a new role.
 *
 * <p>能力识别词表来自已治理的岗位能力模型（{@code post_ability_model.ability_name}）与能力标签库
 * （{@code ability_tag.tag_name}），不再使用硬编码技术词白名单 —— 硬编码词表会把绝大多数资料判定为
 * 「无可用能力」，导致上传资料后始终拿不到候选。词表为空时才回退到内置种子词，并在诊断信息中明确标注。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergingPostDiscoveryServiceImpl implements EmergingPostDiscoveryService {

    private static final int OBSERVATION_MIN_JD = 10;
    private static final int COMMUNITY_MIN_JD = 50;
    private static final int DISCOVERY_MIN_JD = 500;
    private static final int MIN_COMMUNITY_SIZE = 3;
    private static final double PMI_THRESHOLD = 0.10d;
    /** 词表上限，避免超长正则带来编译与匹配开销。 */
    private static final int VOCABULARY_LIMIT = 4000;
    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_TERM_LENGTH = 40;
    private static final Pattern TERM_SEPARATOR = Pattern.compile("[,，、;；/|]+");
    private static final Pattern ROLE_TITLE = Pattern.compile(
            "(?i)([\\u4e00-\\u9fa5A-Za-z]{2,20}(?:工程师|分析师|架构师|运营师|开发师|技术员))");

    /**
     * 仅当岗位能力模型与标签库都为空时使用的引导词表。它只是冷启动兜底，
     * 绝不能冒充「能力词表」——诊断信息会标记 {@code vocabularySource=SEED}。
     */
    private static final List<String> SEED_VOCABULARY = List.of(
            "Java", "Python", "Go", "C++", "C#", "Rust", "JavaScript", "TypeScript", "Spring Boot", "Spring Cloud",
            "Kubernetes", "Docker", "Kafka", "RabbitMQ", "Redis", "MySQL", "PostgreSQL", "MongoDB", "Elasticsearch",
            "TensorFlow", "PyTorch", "LangChain", "向量数据库", "机器学习", "深度学习", "数据仓库", "数据治理",
            "物联网", "边缘计算");

    private final MarketJdDataMapper marketJdDataMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final ObjectMapper objectMapper;
    private final PmiCommunityDetector pmiCommunityDetector;
    private final KnowledgeDocumentService knowledgeDocumentService;

    @Override
    public List<EmergingPostDiscoveryDTO> discoverEmergingPosts(int limit) {
        return runDiscovery(limit).candidates();
    }

    private DiscoveryOutcome runDiscovery(int limit) {
        Vocabulary vocabulary = buildVocabulary();
        List<MarketJdData> marketJds = marketJdDataMapper.selectList(Wrappers.<MarketJdData>lambdaQuery()
                .eq(MarketJdData::getAnalysisStatus, 1)
                .eq(MarketJdData::getIsDuplicate, 0)
                .orderByDesc(MarketJdData::getPublishedTime)
                .last("LIMIT 2000"));
        List<RagKnowledgeDocument> knowledgeDocuments = knowledgeDocumentService
                .listActiveDocumentsByLayers(Set.of(RagKnowledgeLayer.TREND, RagKnowledgeLayer.DOMAIN), 500);

        List<SkillDocument> jdDocuments = toSkillDocuments(marketJds, vocabulary);
        KnowledgeParse knowledgeParse = toKnowledgeSkillDocuments(knowledgeDocuments, vocabulary);
        List<SkillDocument> documents = new ArrayList<>();
        documents.addAll(jdDocuments);
        documents.addAll(knowledgeParse.documents());

        LinkedHashSet<String> recognized = new LinkedHashSet<>();
        documents.forEach(document -> recognized.addAll(document.skillNames().values()));
        DiscoveryDiagnostics diagnostics = new DiscoveryDiagnostics(
                vocabulary.size(), vocabulary.source(), knowledgeDocuments.size(), marketJds.size(),
                documents.size(), recognized.size(), knowledgeParse.matchedDocumentCount(), "");

        boolean hasKnowledgeSource = documents.stream().anyMatch(document -> !"MARKET_JD".equals(document.sourceType()));
        if (documents.isEmpty()) {
            return new DiscoveryOutcome(List.of(), diagnostics.withMessage(
                    diagnosticsMessage(diagnostics, true, List.of())));
        }
        if (!hasKnowledgeSource && documents.size() < OBSERVATION_MIN_JD) {
            return new DiscoveryOutcome(List.of(), diagnostics.withMessage(diagnosticsMessage(diagnostics, true, List.of())));
        }

        DiscoveryMode mode = hasKnowledgeSource && documents.size() < OBSERVATION_MIN_JD
                ? DiscoveryMode.OBSERVATION : DiscoveryMode.of(documents.size());
        Map<Long, AbilityTag> tags = abilityTagMapper.selectList(
                        Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1))
                .stream().collect(Collectors.toMap(AbilityTag::getId, tag -> tag, (left, right) -> left));
        // skillTags 可能为空或只包含原始名称。为保证标签库为空时仍可发现岗位，
        // toSkillDocuments 会生成稳定的临时能力 ID，并在此补充其展示名称。
        documents.forEach(document -> document.skillNames().forEach((id, name) ->
                tags.putIfAbsent(id, syntheticTag(id, name))));
        PmiCommunityDetector.Result graph = pmiCommunityDetector.detect(
                documents.stream().map(SkillDocument::tagIds).toList(), PMI_THRESHOLD,
                mode == DiscoveryMode.OBSERVATION ? 2 : MIN_COMMUNITY_SIZE);
        if (mode == DiscoveryMode.OBSERVATION && graph.communities().isEmpty()) {
            // 单份官方资料没有足够样本计算 PMI，但仍必须形成可审核的观察候选。
            List<Set<Long>> fallbackCommunities = documents.stream()
                    .map(SkillDocument::tagIds)
                    .filter(ids -> ids.size() >= 2)
                    .map(ids -> (Set<Long>) new LinkedHashSet<>(ids))
                    .toList();
            Map<Long, Integer> occurrences = new HashMap<>();
            fallbackCommunities.forEach(ids -> ids.forEach(id -> occurrences.merge(id, 1, Integer::sum)));
            graph = new PmiCommunityDetector.Result(fallbackCommunities, occurrences, Map.of());
        }
        Map<Long, Set<Long>> existingPostSkills = existingPostSkills();

        List<EmergingPostDiscoveryDTO> candidates = new ArrayList<>();
        for (Set<Long> community : graph.communities()) {
            if (community.size() < (mode == DiscoveryMode.OBSERVATION ? 2 : MIN_COMMUNITY_SIZE)) {
                continue;
            }
            EmergingPostDiscoveryDTO candidate = buildCandidate(community, documents, graph, tags, existingPostSkills, mode);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        List<EmergingPostDiscoveryDTO> result = candidates.stream()
                .sorted(Comparator.comparing(EmergingPostDiscoveryDTO::getEmergenceScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
        return new DiscoveryOutcome(result, diagnostics.withMessage(diagnosticsMessage(diagnostics, false, result)));
    }

    @Override
    public EmergingPostDiscoveryDTO.MarketInsight getMarketInsight() {
        // 洞察统计必须基于完整候选集，不能复用页面的 10 条展示上限。
        DiscoveryOutcome outcome = runDiscovery(50);
        List<EmergingPostDiscoveryDTO> candidates = outcome.candidates();
        EmergingPostDiscoveryDTO.MarketInsight insight = new EmergingPostDiscoveryDTO.MarketInsight();
        List<MarketJdData> analyzedJds = marketJdDataMapper.selectList(Wrappers.<MarketJdData>lambdaQuery()
                .eq(MarketJdData::getAnalysisStatus, 1).eq(MarketJdData::getIsDuplicate, 0));
        insight.setAnalyzedJdCount(analyzedJds.size());
        insight.setCandidateCount(candidates.size());
        insight.setLastUpdated(LocalDateTime.now().toString());
        int sourcePlatformCount = (int) analyzedJds.stream().map(MarketJdData::getSourcePlatform)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        int independentEmployerCount = (int) analyzedJds.stream().map(MarketJdData::getCompanyDiversityKey)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        insight.setSourcePlatformCount(sourcePlatformCount);
        insight.setIndependentEmployerCount(independentEmployerCount);
        insight.setSourceDiversityScore(Math.min(100, sourcePlatformCount * 25));
        insight.setCompanyDiversityScore(Math.min(100, independentEmployerCount * 10));
        Long deduplicated = marketJdDataMapper.selectCount(Wrappers.<MarketJdData>lambdaQuery()
                .eq(MarketJdData::getIsDuplicate, 1));
        Long noiseFiltered = marketJdDataMapper.selectCount(Wrappers.<MarketJdData>lambdaQuery()
                .eq(MarketJdData::getAnalysisStatus, 2));
        insight.setDeduplicatedCount(deduplicated == null ? 0 : deduplicated.intValue());
        insight.setNoiseFilteredCount(noiseFiltered == null ? 0 : noiseFiltered.intValue());

        DiscoveryDiagnostics diagnostics = outcome.diagnostics();
        insight.setIndexedDocumentCount(diagnostics.indexedDocumentCount());
        insight.setMatchedDocumentCount(diagnostics.matchedDocumentCount());
        insight.setVocabularySize(diagnostics.vocabularySize());
        insight.setVocabularySource(diagnostics.vocabularySource());
        insight.setRecognizedAbilityCount(diagnostics.recognizedAbilityCount());
        insight.setDiagnosticMessage(diagnostics.message());

        List<EmergingPostDiscoveryDTO.HotAbility> hot = new ArrayList<>();
        for (EmergingPostDiscoveryDTO candidate : candidates) {
            if (candidate.getCoreAbilities() == null || candidate.getCoreAbilities().isEmpty()) continue;
            EmergingPostDiscoveryDTO.HotAbility item = new EmergingPostDiscoveryDTO.HotAbility();
            item.setAbilityName(candidate.getCoreAbilities().get(0));
            item.setMentionCount(candidate.getFrequency());
            item.setGrowthRate(candidate.getTrendGrowthScore());
            item.setRelatedPostCount(candidate.getRelatedExistingPostIds() == null ? 0 : candidate.getRelatedExistingPostIds().size());
            hot.add(item);
        }
        insight.setHotAbilities(hot);
        insight.setTechTrends(List.of());
        return insight;
    }

    /**
     * 面向使用者的空结果原因说明。宁可给出可执行的下一步，也不要静默返回 200 + 空数组。
     */
    private String diagnosticsMessage(DiscoveryDiagnostics diagnostics, boolean empty, List<EmergingPostDiscoveryDTO> candidates) {
        if (diagnostics.indexedDocumentCount() == 0 && diagnostics.marketJdCount() == 0) {
            return "暂无可用数据源：请先上传行业资料（白皮书/政策文件），或采集并分析市场 JD。";
        }
        if (diagnostics.vocabularySize() == 0) {
            return "能力词表为空：岗位能力模型与能力标签库中都没有可用能力名称，无法从资料中识别能力，请先在岗位定义中补充能力项。";
        }
        if (diagnostics.indexedDocumentCount() > 0 && diagnostics.knowledgeMatchedDocumentCount() == 0) {
            return "已索引 " + diagnostics.indexedDocumentCount() + " 份资料，但未在正文中匹配到任何岗位能力词（词表 "
                    + diagnostics.vocabularySize() + " 项，来源 " + diagnostics.vocabularySource()
                    + "）。请确认资料正文确实包含能力描述，或补充对应的岗位能力项。";
        }
        if (diagnostics.recognizedAbilityCount() < 2 && empty) {
            return "仅识别出 " + diagnostics.recognizedAbilityCount()
                    + " 个能力词，不足以形成能力共现社区（至少需要 2 个）。";
        }
        if (empty) {
            return "已索引 " + diagnostics.indexedDocumentCount() + " 份资料并识别出 "
                    + diagnostics.recognizedAbilityCount() + " 个能力词，但未形成满足阈值的能力社区，可继续补充样本或放宽发现阈值。";
        }
        return "已基于 " + diagnostics.indexedDocumentCount() + " 份索引资料与 " + diagnostics.marketJdCount()
                + " 条市场 JD 形成 " + candidates.size() + " 个候选岗位方向。";
    }

    private EmergingPostDiscoveryDTO buildCandidate(Set<Long> community, List<SkillDocument> documents,
                                                    PmiCommunityDetector.Result graph, Map<Long, AbilityTag> tags,
                                                    Map<Long, Set<Long>> existingPostSkills, DiscoveryMode mode) {
        List<Long> orderedIds = community.stream()
                .sorted(Comparator.comparingInt((Long id) -> graph.occurrences().getOrDefault(id, 0)).reversed())
                .toList();
        List<String> skills = orderedIds.stream().map(tags::get).filter(Objects::nonNull)
                .map(AbilityTag::getTagName).toList();
        if (skills.size() < 2) return null;

        Set<Long> communityIds = new LinkedHashSet<>(orderedIds);
        List<SkillDocument> matched = documents.stream()
                .filter(document -> !java.util.Collections.disjoint(document.tagIds(), communityIds)).toList();
        double maxJaccard = 0d;
        List<Long> relatedPosts = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : existingPostSkills.entrySet()) {
            double similarity = jaccard(communityIds, entry.getValue());
            maxJaccard = Math.max(maxJaccard, similarity);
            if (similarity >= 0.45d) relatedPosts.add(entry.getKey());
        }
        double growth = growthScore(matched);
        double novelty = 1d - maxJaccard;
        double cohesion = graph.cohesion(communityIds);
        double credibility = credibility(matched);
        int emergence = percentage((growth + novelty + cohesion + credibility) / 4d);

        EmergingPostDiscoveryDTO dto = new EmergingPostDiscoveryDTO();
        /*
         * 【明确口径】候选方向名必须由能力社区自己生成，**绝不取任何文档标题**。
         *
         * 原先取 matched 里第一条文档的 rawTitle，而资料的 rawTitle 就是它的标题；
         * 资料标题在上传时又可能被前端用「文件名」兜底填上，于是
         * `2026年AI人才需求白皮书.docx` 会直接变成页面上的「候选方向」，
         * 用户看到的就是一堆以文件名命名的、无法审核的假岗位。
         * 另外 findFirst() 没有排序，取到哪一条文档取决于列表顺序，重跑还会换名字。
         */
        dto.setCandidateName(buildCandidateName(skills));
        dto.setDescription("基于已治理市场 JD 与索引资料的技能共现社区发现结果，需经人工审核后进入岗位定义流程。");
        dto.setCoreAbilities(skills);
        // 来源必须分列：matched 是「市场 JD + 知识资料」的混合集，直接当 JD 条数会虚高，
        // 也会把用户上传的资料算成市场 JD。前端按 marketJdCount / knowledgeDocumentCount 分别展示。
        List<SkillDocument> marketJdMatched = matched.stream()
                .filter(document -> "MARKET_JD".equals(document.sourceType()))
                .toList();
        dto.setFrequency(matched.size());
        dto.setMarketJdCount(marketJdMatched.size());
        dto.setKnowledgeDocumentCount(matched.size() - marketJdMatched.size());
        // 真实岗位名只能来自市场 JD 的岗位字段；资料标题不是岗位名，走 sourceTitles。
        dto.setEvidencePostNames(marketJdMatched.stream().map(SkillDocument::rawTitle)
                .filter(name -> name != null && !name.isBlank()).distinct().limit(10).toList());
        dto.setEvidenceBreakdown(matched.stream().map(SkillDocument::sourceType)
                .filter(Objects::nonNull).distinct().toList());
        dto.setNoveltyScore(percentage(novelty));
        dto.setSemanticNoveltyScore(percentage(novelty));
        dto.setMarketHeatScore(percentage((double) matched.size() / Math.max(1, documents.size())));
        dto.setTrendGrowthScore(percentage(growth));
        int sourcePlatformCount = (int) matched.stream().map(SkillDocument::sourcePlatform)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        int independentEmployerCount = (int) matched.stream().map(SkillDocument::companyDiversityKey)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        dto.setSourcePlatformCount(sourcePlatformCount);
        dto.setIndependentEmployerCount(independentEmployerCount);
        dto.setSourceDiversityScore(Math.min(100, sourcePlatformCount * 25));
        dto.setCompanyDiversityScore(Math.min(100, independentEmployerCount * 10));
        dto.setEvidenceCredibilityScore(percentage(credibility));
        dto.setEmergenceScore(emergence);
        dto.setSourceRefs(matched.stream().limit(50).map(document -> "source:" + document.sourceType() + ":" + document.id()).toList());
        dto.setSourceTypes(matched.stream().map(SkillDocument::sourceType).distinct().toList());
        // 「原始称谓」按字面只能是真实岗位称谓 —— 只取市场 JD 的岗位字段。
        // 资料文档的 rawTitle 其实是它的标题（可能来自上传文件名），放到「来源材料」里才不会被误读成岗位名。
        dto.setRawTitles(marketJdMatched.stream().map(SkillDocument::rawTitle)
                .filter(v -> v != null && !v.isBlank()).distinct().limit(10).toList());
        dto.setSourceTitles(matched.stream().map(SkillDocument::sourceTitle).filter(v -> v != null && !v.isBlank()).distinct().limit(10).toList());
        dto.setPolicyValidated(matched.stream().anyMatch(d -> isAuthoritativeSource(d.sourceType())));
        List<String> riskFlags = new ArrayList<>();
        if (!dto.getPolicyValidated()) riskFlags.add("POLICY_VALIDATION_MISSING");
        if (matched.stream().noneMatch(d -> "MARKET_JD".equals(d.sourceType()))) riskFlags.add("MARKET_VALIDATION_MISSING");
        dto.setRiskFlags(riskFlags);
        dto.setEvidenceSummary("来源 " + String.join("、", dto.getSourceTypes()) + "，覆盖 " + matched.size() + " 条资料/岗位样本");
        dto.setRelatedExistingPostIds(relatedPosts);
        dto.setDifferentiationReason(maxJaccard >= 0.60d
                ? "与既有岗位能力模型相近，建议进入岗位演化审核。"
                : "与既有岗位能力模型差异较大，建议作为新兴岗位候选审核。");
        dto.setReviewStatus(mode == DiscoveryMode.OBSERVATION ? "OBSERVATION" : "PENDING");
        dto.setHarnessDecision(mode == DiscoveryMode.OBSERVATION ? "REVIEW" : "PASS");
        dto.setDiscoveryMode(mode.name());
        dto.setCohesionScore(percentage(cohesion));
        dto.setRecommendedAction(maxJaccard >= 0.60d ? "POST_EVOLUTION" : "EMERGING_POST_REVIEW");
        return dto;
    }

    private List<SkillDocument> toSkillDocuments(List<MarketJdData> data, Vocabulary vocabulary) {
        List<SkillDocument> documents = new ArrayList<>();
        for (MarketJdData jd : data) {
            Set<Long> tagIds = parseTagIds(jd.getSkillTags());
            LinkedHashMap<Long, String> names = parseSkillNames(jd.getSkillTags());
            if (tagIds.isEmpty() && jd.getMatchedPostId() != null) {
                postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()
                                .eq(PostAbilityModel::getPostId, jd.getMatchedPostId())
                                .eq(PostAbilityModel::getIsDeleted, 0))
                        .forEach(model -> {
                            if (model.getTagId() != null) tagIds.add(model.getTagId());
                            if (model.getAbilityName() != null && !model.getAbilityName().isBlank()) {
                                long id = model.getTagId() != null ? model.getTagId() : syntheticId(model.getAbilityName());
                                names.putIfAbsent(id, model.getAbilityName());
                                tagIds.add(id);
                            }
                        });
            }
            if (tagIds.isEmpty()) {
                names.putAll(extractTechnologyNames(jd, vocabulary));
                names.forEach((id, name) -> tagIds.add(id));
            }
            if (tagIds.size() < 2) continue;
            LocalDateTime effectiveTime = jd.getPublishedTime() != null ? jd.getPublishedTime() : jd.getCreatedTime();
            documents.add(new SkillDocument(jd.getId(), tagIds, effectiveTime, jd.getQualityScore(),
                    jd.getSourcePlatform(), jd.getCompanyDiversityKey(), names, "MARKET_JD",
                    jd.getPostName(), jd.getPostName()));
        }
        return documents;
    }

    private KnowledgeParse toKnowledgeSkillDocuments(List<RagKnowledgeDocument> data, Vocabulary vocabulary) {
        List<SkillDocument> documents = new ArrayList<>();
        int matchedDocumentCount = 0;
        for (RagKnowledgeDocument doc : data) {
            String text = String.join(" ", safe(doc.getTitle()), safe(doc.getContent()));
            LinkedHashMap<Long, String> names = matchVocabulary(text, vocabulary);
            if (names.isEmpty()) continue;
            matchedDocumentCount++;
            if (names.size() < 2) continue;
            Set<Long> ids = new LinkedHashSet<>(names.keySet());
            String sourceType = normalizeKnowledgeSource(doc.getSourceType());
            documents.add(new SkillDocument(doc.getId(), ids, doc.getUpdatedTime(), BigDecimal.valueOf(sourceCredibility(sourceType)),
                    sourceType, sourceType + ":" + doc.getId(), names, sourceType, extractRoleTitle(doc.getTitle(), text), doc.getTitle()));
        }
        return new KnowledgeParse(documents, matchedDocumentCount);
    }

    /**
     * 构建能力识别词表：以岗位能力模型为权威来源，能力标签库作为补充；两者皆空时回退内置种子词。
     */
    private Vocabulary buildVocabulary() {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (PostAbilityModel model : postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getIsDeleted, 0))) {
            addTerm(terms, model.getAbilityName());
            addTerm(terms, model.getTechStack());
        }
        int abilityModelTermCount = terms.size();
        for (AbilityTag tag : abilityTagMapper.selectList(
                Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1))) {
            addTerm(terms, tag.getTagName());
        }
        if (terms.isEmpty()) {
            Pattern seed = buildPattern(SEED_VOCABULARY);
            return new Vocabulary(seed, SEED_VOCABULARY.size(), "SEED");
        }
        String source = abilityModelTermCount > 0 ? "POST_ABILITY_MODEL" : "ABILITY_TAG";
        Pattern pattern = buildPattern(terms);
        int size = pattern == null ? 0 : Math.min(terms.size(), VOCABULARY_LIMIT);
        return new Vocabulary(pattern, size, source);
    }

    private void addTerm(Collection<String> terms, String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : TERM_SEPARATOR.split(raw)) {
            String term = part.trim();
            if (term.length() < MIN_TERM_LENGTH || term.length() > MAX_TERM_LENGTH) continue;
            if (term.chars().allMatch(Character::isDigit)) continue;
            terms.add(term);
        }
    }

    private Pattern buildPattern(Collection<String> terms) {
        List<String> ordered = terms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(VOCABULARY_LIMIT)
                .toList();
        if (ordered.isEmpty()) return null;
        StringBuilder builder = new StringBuilder();
        for (String term : ordered) {
            if (builder.length() > 0) builder.append('|');
            boolean ascii = term.codePoints().allMatch(codePoint -> codePoint < 128);
            if (ascii) {
                builder.append("(?<![A-Za-z0-9])").append(Pattern.quote(term)).append("(?![A-Za-z0-9])");
            } else {
                builder.append(Pattern.quote(term));
            }
        }
        return Pattern.compile(builder.toString(), Pattern.CASE_INSENSITIVE);
    }

    private LinkedHashMap<Long, String> matchVocabulary(String text, Vocabulary vocabulary) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        if (vocabulary == null || vocabulary.pattern() == null || text == null || text.isBlank()) return result;
        Matcher matcher = vocabulary.pattern().matcher(text);
        while (matcher.find()) {
            String name = matcher.group().trim();
            if (name.isEmpty()) continue;
            result.putIfAbsent(syntheticId(name), name);
        }
        return result;
    }

    private String normalizeKnowledgeSource(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return "OFFICIAL_DOCUMENT";
        String normalized = sourceType.toUpperCase(Locale.ROOT);
        if (normalized.contains("WHITEPAPER") || normalized.contains("REPORT")) return "INDUSTRY_REPORT";
        if (normalized.contains("POLICY") || normalized.contains("OFFICIAL") || normalized.contains("CONTEST")) return "OFFICIAL_POLICY";
        return normalized;
    }

    private int sourceCredibility(String sourceType) {
        return isAuthoritativeSource(sourceType) ? 95 : "INDUSTRY_REPORT".equals(sourceType) ? 85 : 70;
    }

    private boolean isAuthoritativeSource(String sourceType) {
        return "OFFICIAL_POLICY".equals(sourceType) || "OFFICIAL_DOCUMENT".equals(sourceType);
    }

    /**
     * 由能力社区生成候选方向名。
     * <p>
     * 取社区内提及次数最高的前两个能力词拼成「X+Y 复合能力方向」。{@code skills} 来自
     * {@link #buildCandidate} 里按 {@code graph.occurrences()} 降序排列的社区技能，因此前两个
     * 就是该社区最具代表性的能力组合。
     * <p>
     * 为什么不用文档标题：见调用处的注释 —— 资料标题可能来自上传文件名，用它命名会产出
     * 「以文件名命名的岗位」。名称必须能从证据本身推导出来，才经得起人工审核。
     * 单个能力词时退化为「{能力} 相关能力方向」，避免出现「X+ 复合」这种半截名字。
     */
    private String buildCandidateName(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "未识别能力方向";
        }
        if (skills.size() == 1) {
            return skills.get(0) + " 相关能力方向";
        }
        return skills.get(0) + "+" + skills.get(1) + " 复合能力方向";
    }

    private String extractRoleTitle(String title, String text) {
        if (title != null && !title.isBlank()) return title.trim();
        Matcher matcher = ROLE_TITLE.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Set<Long> parseTagIds(String json) {
        try {
            List<Long> ids = objectMapper.readValue(json, new TypeReference<List<Long>>() { });
            return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
        } catch (Exception exception) {
            return new LinkedHashSet<>();
        }
    }

    private LinkedHashMap<Long, String> parseSkillNames(String json) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        try {
            List<?> values = objectMapper.readValue(json, new TypeReference<List<?>>() { });
            for (Object value : values) {
                if (value instanceof Number number) continue;
                String name = String.valueOf(value).trim();
                if (!name.isBlank()) result.put(syntheticId(name), name);
            }
        } catch (Exception ignored) {
            // 非法或历史格式由文本回退处理，不阻断市场分析。
        }
        return result;
    }

    private LinkedHashMap<Long, String> extractTechnologyNames(MarketJdData jd, Vocabulary vocabulary) {
        String text = String.join(" ", safe(jd.getPostName()), safe(jd.getJobDescription()), safe(jd.getRequirements()));
        return matchVocabulary(text, vocabulary);
    }

    private AbilityTag syntheticTag(Long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setStatus(1);
        tag.setIsDeleted(0);
        return tag;
    }

    private long syntheticId(String name) {
        return -1L * (Integer.toUnsignedLong(name.trim().toLowerCase().hashCode()) + 1L);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<Long, Set<Long>> existingPostSkills() {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (PostAbilityModel model : postAbilityModelMapper.selectList(Wrappers.<PostAbilityModel>lambdaQuery()
                .eq(PostAbilityModel::getIsDeleted, 0))) {
            if (model.getTagId() != null) {
                result.computeIfAbsent(model.getPostId(), ignored -> new LinkedHashSet<>()).add(model.getTagId());
            }
        }
        return result;
    }

    private double growthScore(List<SkillDocument> documents) {
        LocalDateTime cut = LocalDateTime.now().minusMonths(3);
        long recent = documents.stream().filter(document -> document.publishedTime() != null && document.publishedTime().isAfter(cut)).count();
        long previous = documents.size() - recent;
        double rate = (recent - previous) / (double) Math.max(1, previous);
        return 1d / (1d + Math.exp(-rate));
    }

    private double credibility(List<SkillDocument> documents) {
        if (documents.isEmpty()) return 0d;
        return documents.stream().map(SkillDocument::qualityScore).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0d) / 100d;
    }

    private double jaccard(Set<Long> left, Set<Long> right) {
        Set<Long> intersection = new HashSet<>(left); intersection.retainAll(right);
        Set<Long> union = new HashSet<>(left); union.addAll(right);
        return union.isEmpty() ? 0d : intersection.size() / (double) union.size();
    }

    private int percentage(double value) { return (int) Math.round(Math.max(0d, Math.min(1d, value)) * 100d); }

    private record SkillDocument(Long id, Set<Long> tagIds, LocalDateTime publishedTime, BigDecimal qualityScore,
                                 String sourcePlatform, String companyDiversityKey, Map<Long, String> skillNames,
                                 String sourceType, String rawTitle, String sourceTitle) {
    }

    /** 能力识别词表：编译后的多分支正则 + 词表规模 + 来源标记。 */
    private record Vocabulary(Pattern pattern, int size, String source) { }

    /** 资料解析结果：形成技能文档的资料 + 正文中至少命中一个能力词的资料数。 */
    private record KnowledgeParse(List<SkillDocument> documents, int matchedDocumentCount) { }

    private record DiscoveryOutcome(List<EmergingPostDiscoveryDTO> candidates, DiscoveryDiagnostics diagnostics) { }

    private record DiscoveryDiagnostics(int vocabularySize, String vocabularySource, int indexedDocumentCount,
                                        int marketJdCount, int matchedDocumentCount, int recognizedAbilityCount,
                                        int knowledgeMatchedDocumentCount, String message) {
        DiscoveryDiagnostics withMessage(String newMessage) {
            return new DiscoveryDiagnostics(vocabularySize, vocabularySource, indexedDocumentCount, marketJdCount,
                    matchedDocumentCount, recognizedAbilityCount, knowledgeMatchedDocumentCount, newMessage);
        }
    }

    private enum DiscoveryMode { OBSERVATION, CANDIDATE, DISCOVERY;
        static DiscoveryMode of(int count) { return count >= DISCOVERY_MIN_JD ? DISCOVERY : count >= COMMUNITY_MIN_JD ? CANDIDATE : OBSERVATION; }
    }
}


