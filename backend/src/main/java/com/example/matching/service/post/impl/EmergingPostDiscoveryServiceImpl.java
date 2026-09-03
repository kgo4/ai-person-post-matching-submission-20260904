package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.post.EmergingPostDiscoveryDTO;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers market skill communities from governed MarketJdData. The detector deliberately
 * degrades to observation mode on small data rather than presenting weak samples as a new role.
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
    private static final Pattern TECH_TOKEN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:Java|Python|Go|C\\+\\+|C#|Rust|JavaScript|TypeScript|Spring Boot|Spring Cloud|"
                    + "Kubernetes|Docker|Kafka|RabbitMQ|Redis|MySQL|PostgreSQL|MongoDB|Elasticsearch|"
                    + "TensorFlow|PyTorch|LangChain|向量数据库|机器学习|深度学习|数据仓库|数据治理|物联网|边缘计算)(?![A-Za-z0-9])");

    private final MarketJdDataMapper marketJdDataMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final ObjectMapper objectMapper;
    private final PmiCommunityDetector pmiCommunityDetector;

    @Override
    public List<EmergingPostDiscoveryDTO> discoverEmergingPosts(int limit) {
        List<MarketJdData> marketJds = marketJdDataMapper.selectList(Wrappers.<MarketJdData>lambdaQuery()
                .eq(MarketJdData::getAnalysisStatus, 1)
                .eq(MarketJdData::getIsDuplicate, 0)
                .orderByDesc(MarketJdData::getPublishedTime)
                .last("LIMIT 2000"));
        List<SkillDocument> documents = toSkillDocuments(marketJds);
        if (documents.size() < OBSERVATION_MIN_JD) {
            return List.of();
        }

        DiscoveryMode mode = DiscoveryMode.of(documents.size());
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
        return candidates.stream()
                .sorted(Comparator.comparing(EmergingPostDiscoveryDTO::getEmergenceScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 50)))
                .toList();
    }

    @Override
    public EmergingPostDiscoveryDTO.MarketInsight getMarketInsight() {
        // 洞察统计必须基于完整候选集，不能复用页面的 10 条展示上限。
        List<EmergingPostDiscoveryDTO> candidates = discoverEmergingPosts(50);
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

    private EmergingPostDiscoveryDTO buildCandidate(Set<Long> community, List<SkillDocument> documents,
                                                    PmiCommunityDetector.Result graph, Map<Long, AbilityTag> tags,
                                                    Map<Long, Set<Long>> existingPostSkills, DiscoveryMode mode) {
        List<Long> orderedIds = community.stream()
                .sorted(Comparator.comparingInt((Long id) -> graph.occurrences().getOrDefault(id, 0)).reversed())
                .toList();
        List<String> skills = orderedIds.stream().map(tags::get).filter(java.util.Objects::nonNull)
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
        dto.setCandidateName(skills.get(0) + "复合能力方向");
        dto.setDescription("基于已治理市场 JD 的技能共现社区发现结果，需经人工审核后进入岗位定义流程。");
        dto.setCoreAbilities(skills);
        dto.setFrequency(matched.size());
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
        dto.setSourceRefs(matched.stream().limit(50).map(document -> "source:MARKET_JD:" + document.id()).toList());
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

    private List<SkillDocument> toSkillDocuments(List<MarketJdData> data) {
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
                names.putAll(extractTechnologyNames(jd));
                names.forEach((id, name) -> tagIds.add(id));
            }
            if (tagIds.size() < 2) continue;
            LocalDateTime effectiveTime = jd.getPublishedTime() != null ? jd.getPublishedTime() : jd.getCreatedTime();
            documents.add(new SkillDocument(jd.getId(), tagIds, effectiveTime, jd.getQualityScore(),
                    jd.getSourcePlatform(), jd.getCompanyDiversityKey(), names));
        }
        return documents;
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

    private LinkedHashMap<Long, String> extractTechnologyNames(MarketJdData jd) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        String text = String.join(" ", safe(jd.getPostName()), safe(jd.getJobDescription()), safe(jd.getRequirements()));
        Matcher matcher = TECH_TOKEN.matcher(text);
        while (matcher.find()) {
            String name = matcher.group().trim();
            result.putIfAbsent(syntheticId(name), name);
        }
        return result;
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
        return documents.stream().map(SkillDocument::qualityScore).filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0d) / 100d;
    }

    private double sourceDiversity(List<SkillDocument> documents) {
        return documents.stream().map(SkillDocument::sourcePlatform).filter(java.util.Objects::nonNull).distinct().count()
                / (double) Math.max(1, Math.min(3, documents.size()));
    }

    private double jaccard(Set<Long> left, Set<Long> right) {
        Set<Long> intersection = new HashSet<>(left); intersection.retainAll(right);
        Set<Long> union = new HashSet<>(left); union.addAll(right);
        return union.isEmpty() ? 0d : intersection.size() / (double) union.size();
    }

    private int percentage(double value) { return (int) Math.round(Math.max(0d, Math.min(1d, value)) * 100d); }

    private record SkillDocument(Long id, Set<Long> tagIds, LocalDateTime publishedTime, BigDecimal qualityScore,
                                 String sourcePlatform, String companyDiversityKey, Map<Long, String> skillNames) {
        long syntheticId(String name) {
            return -1L * (Integer.toUnsignedLong(name.trim().toLowerCase().hashCode()) + 1L);
        }
    }
    private enum DiscoveryMode { OBSERVATION, CANDIDATE, DISCOVERY;
        static DiscoveryMode of(int count) { return count >= DISCOVERY_MIN_JD ? DISCOVERY : count >= COMMUNITY_MIN_JD ? CANDIDATE : OBSERVATION; }
    }
}
