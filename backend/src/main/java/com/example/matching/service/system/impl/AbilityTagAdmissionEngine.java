package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.enums.EvidenceSourceEnum;
import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.event.GraphChangeRequestedEvent;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagNormalizer;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.service.system.AbilityTagVectorOperations;
import com.example.matching.service.system.TaxonomyClassifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 能力标签准入引擎：从 AbilityTagServiceImpl 中拆分的独立领域组件。
 * <p>
 * 承载统一准入管线（admitNewTag）、语义相似查找（findSimilarTags）、
 * 标签创建（createFormalTag/createAiTag）以及共享的查找/别名叶子方法。
 * 拆分目的：将 1100+ 行的上帝类收敛为职责聚焦的组件，行为保持不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagAdmissionEngine extends ServiceImpl<AbilityTagMapper, AbilityTag> {

    @Autowired(required = false)
    private CacheManager cacheManager;

    // 相似度分层阈值
    private static final double SIMILARITY_THRESHOLD_HIGH = 0.92;
    private static final double SIMILARITY_THRESHOLD_MEDIUM_HIGH = 0.82;
    private static final double SIMILARITY_THRESHOLD_MEDIUM = 0.70;
    private static final double SIMILARITY_THRESHOLD_LOW = 0.55;

    private static final BigDecimal HARNESS_PASS_SCORE_THRESHOLD = new BigDecimal("80");

    private final AbilityTagAliasMapper tagAliasMapper;
    private final AbilityTagRelationMapper tagRelationMapper;
    private final AbilityTagCandidateMapper tagCandidateMapper;
    private final AbilityTagVectorOperations vectorOperations;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final AbilityTagNormalizer abilityTagNormalizer;
    private final AbilityTagTaxonomyClassifier taxonomyClassifier;
    private final ObjectProvider<KnowledgeDocumentService> knowledgeDocumentServiceProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<AbilityTagAdmissionPipeline> admissionPipelineProvider;

    @Override
    @Cacheable(cacheNames = RedisCacheNames.ABILITY_TAG_INFO, key = "#id", unless = "#result == null")
    public AbilityTag getById(java.io.Serializable id) {
        return super.getById(id);
    }

    public AbilityTag findByName(String tagName) {
        if (!StringUtils.hasText(tagName)) {
            return null;
        }
        return getOne(Wrappers.<AbilityTag>lambdaQuery()
                .eq(AbilityTag::getTagName, tagName)
                .eq(AbilityTag::getStatus, 1)
                .last("LIMIT 1"));
    }

    public AbilityTag findByAlias(String aliasName) {
        if (!StringUtils.hasText(aliasName)) {
            return null;
        }
        AbilityTagAlias alias = tagAliasMapper.selectOne(
                Wrappers.<AbilityTagAlias>lambdaQuery()
                        .eq(AbilityTagAlias::getAliasName, aliasName)
                        .last("LIMIT 1")
        );
        if (alias == null) {
            return null;
        }
        AbilityTag tag = getById(alias.getTagId());
        if (tag != null && tag.getStatus() == 1) {
            return tag;
        }
        return null;
    }

    public List<AbilityTag> findSimilarTags(String tagName, double threshold) {
        if (!StringUtils.hasText(tagName)) {
            return new ArrayList<>();
        }

        // 1. 精确匹配
        AbilityTag exactMatch = findByName(tagName);
        if (exactMatch != null) {
            return List.of(exactMatch);
        }

        // 2. 别名匹配
        AbilityTagAlias alias = tagAliasMapper.selectOne(
                Wrappers.<AbilityTagAlias>lambdaQuery()
                        .eq(AbilityTagAlias::getAliasName, tagName)
                        .last("LIMIT 1")
        );
        if (alias != null) {
            AbilityTag tag = getById(alias.getTagId());
            if (tag != null && tag.getStatus() == 1) {
                return List.of(tag);
            }
        }

        // 3. 向量相似度匹配
        try {
            List<Float> queryVector = vectorEmbeddingService.embed(tagName);
            if (queryVector == null || queryVector.isEmpty()) {
                return new ArrayList<>();
            }

            List<AbilityTag> allTags = list(Wrappers.<AbilityTag>lambdaQuery()
                    .eq(AbilityTag::getStatus, 1));

            return allTags.stream()
                    .filter(tag -> tag.getEmbeddingVector() != null && !tag.getEmbeddingVector().isEmpty())
                    .filter(tag -> {
                        Float similarity = vectorEmbeddingService.cosineSimilarity(
                                queryVector, tag.getEmbeddingVector());
                        return similarity != null && similarity >= threshold;
                    })
                    .sorted((a, b) -> {
                        Float simA = vectorEmbeddingService.cosineSimilarity(queryVector, a.getEmbeddingVector());
                        Float simB = vectorEmbeddingService.cosineSimilarity(queryVector, b.getEmbeddingVector());
                        return Float.compare(simB != null ? simB : 0, simA != null ? simA : 0);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("向量相似度查找失败: tagName={}, error={}", tagName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Transactional
    public AbilityTag findOrCreateByName(String tagName, String tagCategory, String sourceType) {
        AbilityTag existing = findByName(tagName);
        if (existing != null) {
            return existing;
        }
        return createAiTag(tagName, tagCategory, sourceType != null ? sourceType : "AI_JD");
    }

    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType) {
        return findSimilarTagOrCreate(tagName, tagCategory, sourceType, null, null);
    }

    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType, String jdText) {
        return findSimilarTagOrCreate(tagName, tagCategory, sourceType, jdText, null);
    }

    /**
     * 查找语义相似标签或创建新标签（带幻觉防护+Harness来源追踪）。
     * 桥接到统一准入流程。
     */
    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType,
                                             String jdText, Long sourceRefId) {
        TagAdmissionContext context = TagAdmissionContext.builder()
                .tagName(tagName)
                .tagCategory(tagCategory != null ? tagCategory : "TECHNICAL")
                .sourceType(sourceType != null ? sourceType : "AI_RESUME")
                .contextText(jdText)
                .sourceRefId(sourceRefId)
                .confidenceScore(0.8f)
                .evidenceText(jdText)
                .build();

        TagAdmissionResult result = admitNewTag(context);

        switch (result.getDecision()) {
            case EXISTING_TAG_REUSED:
            case FORMAL_TAG_CREATED:
                log.info("标签准入成功: tagName={}, decision={}, tagId={}",
                        tagName, result.getDecision(), result.getResolvedTagId());
                return result.getFormalTag();

            case CANDIDATE_CREATED:
                log.info("标签进入候选池: tagName={}, decision={}, candidateId={}",
                        tagName, result.getDecision(), result.getCandidateId());
                return null;

            case REJECTED:
                log.info("标签被拒绝: tagName={}, decision={}, reason={}",
                        tagName, result.getDecision(), result.getReason());
                return null;

            default:
                log.warn("未知的准入决策: tagName={}, decision={}", tagName, result.getDecision());
                return null;
        }
    }

    /**
     * 统一标签准入流程：规范化 -> 精确/别名/向量匹配 -> 质量/证据/来源/置信度检查 -> Harness 验证。
     */

    /**
     * 统一准入管线（委托给 {@link AbilityTagAdmissionPipeline}）。
     */
    @Transactional
    public TagAdmissionResult admitNewTag(TagAdmissionContext context) {
        return admissionPipelineProvider.getObject().admitNewTag(context);
    }

    void saveAlias(Long tagId, String aliasName) {
        admissionPipelineProvider.getObject().saveAlias(tagId, aliasName);
    }

    void saveSimilarRelationIfAbsent(Long matchedTagId, String queryTagName) {
        AbilityTag queryTag = findByName(queryTagName);
        if (queryTag == null) {
            log.info("向量匹配发现相似标签，但查询标签尚不存在，跳过关系创建: query={}, matchedTagId={}",
                    queryTagName, matchedTagId);
            return;
        }

        Long sourceId = Math.min(queryTag.getId(), matchedTagId);
        Long targetId = Math.max(queryTag.getId(), matchedTagId);
        if (sourceId.equals(targetId)) return;

        AbilityTagRelation existing = tagRelationMapper.selectOne(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getSourceTagId, sourceId)
                        .eq(AbilityTagRelation::getTargetTagId, targetId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            log.debug("标签关系已存在: source={}, target={}", sourceId, targetId);
            return;
        }

        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSourceTagId(sourceId);
        relation.setTargetTagId(targetId);
        relation.setRelationType(RelationTypeEnum.SIMILAR.getCode());
        relation.setStatus(RelationStatusEnum.PENDING.getCode());
        relation.setEvidenceSource(EvidenceSourceEnum.VECTOR_DISCOVERY.getCode());
        relation.setRemark("向量自动发现相似标签: " + queryTagName);
        relation.setCreatedTime(java.time.LocalDateTime.now());
        tagRelationMapper.insert(relation);

        log.info("创建向量发现的相似标签关系: source={}, target={}, query={}", sourceId, targetId, queryTagName);
    }

    AbilityTag createAiTag(String tagName, String tagCategory, String sourceType) {
        AbilityTag newTag = new AbilityTag();
        newTag.setTagCode("AI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        newTag.setTagName(tagName);
        newTag.setTagCategory(tagCategory != null ? tagCategory : "TECHNICAL");
        TaxonomyClassifyResult taxonomy = applyTaxonomy(newTag, tagName);
        if (taxonomy == null) {
            log.info("AI标签无法归属到L2能力，跳过正式入库: tagName={}", tagName);
            return null;
        }
        if ("RULE".equals(taxonomy.source())) {
            return taxonomy.abilityTag();
        }
        newTag.setSortOrder(999);
        newTag.setIsSystem(0);
        newTag.setSourceType(sourceType != null ? sourceType : "AI_RESUME");
        newTag.setStatus(1);

        try {
            List<Float> vector = vectorEmbeddingService.embed(tagName);
            newTag.setEmbeddingVector(vector);
        } catch (Exception e) {
            log.warn("生成标签向量失败: tagName={}, error={}", tagName, e.getMessage());
        }

        save(newTag);
        newTag.setCanonicalTagId(newTag.getId());
        updateById(newTag);
        evictAbilityTagCaches();
        eventPublisher.publishEvent(new GraphChangeRequestedEvent("ABILITY_TAG", "ABILITY_TAG", newTag.getId(), "UPSERT",
                Map.of("trigger", "AbilityTagService.createFormalTag"), null));

        try {
            addToKnowledgeBase(newTag);
        } catch (Exception e) {
            log.warn("新标签写入知识库失败: tagName={}, error={}", tagName, e.getMessage());
        }

        log.info("AI自动创建能力标签（含向量）: name={}, category={}, source={}", tagName, tagCategory, sourceType);
        return newTag;
    }

    @Transactional
    public AbilityTag createFormalTag(String tagName, String tagCategory, String domain, String description, String sourceType) {
        AbilityTag newTag = new AbilityTag();
        newTag.setTagCode("TAG_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        newTag.setTagName(tagName);
        newTag.setTagCategory(tagCategory != null ? tagCategory : "TECHNICAL");
        newTag.setDomain(domain != null ? domain : "GENERAL");
        newTag.setDescription(description);
        TaxonomyClassifyResult taxonomy = applyTaxonomy(newTag, tagName);
        if (taxonomy == null) {
            throw new IllegalStateException("无法确定新能力的有效父节点，拒绝创建正式标签: " + tagName);
        }
        newTag.setSortOrder(999);
        newTag.setIsSystem(0);
        newTag.setSourceType(sourceType != null ? sourceType : "MANUAL");
        newTag.setStatus(1);

        try {
            List<Float> vector = vectorEmbeddingService.embed(tagName);
            newTag.setEmbeddingVector(vector);
        } catch (Exception e) {
            log.warn("生成标签向量失败: tagName={}, error={}", tagName, e.getMessage());
        }

        save(newTag);
        newTag.setCanonicalTagId(newTag.getId());
        updateById(newTag);
        evictAbilityTagCaches();

        try {
            addToKnowledgeBase(newTag);
        } catch (Exception e) {
            log.warn("新标签写入知识库失败: tagName={}, error={}", tagName, e.getMessage());
        }

        log.info("创建正式能力标签: id={}, name={}, category={}, source={}", newTag.getId(), tagName, tagCategory, sourceType);
        return newTag;
    }

    /**
     * 技能归层：判定标签归属的能力层（L1）。
     * 归类成功 → 挂 parent_id + tag_level=2 + 继承父能力 domain；
     * 归类失败/无归属 → 保持顶层能力层（parent_id=0, tag_level=1）。
     */
    private TaxonomyClassifyResult applyTaxonomy(AbilityTag newTag, String tagName) {
        try {
            TaxonomyClassifyResult classify = taxonomyClassifier.classify(tagName);
            AbilityTag matchedCapability = classify != null ? classify.abilityTag() : null;
            if (matchedCapability != null && matchedCapability.getId() != null) {
                Long domainId = matchedCapability.getParentId();
                if (!AbilityTagHierarchy.isAssessable(matchedCapability)
                        || domainId == null || domainId == 0L
                        || !StringUtils.hasText(matchedCapability.getTagCategory())) {
                    return null;
                }
                newTag.setParentId(domainId);
                newTag.setTagLevel(2);
                // The classifier selects a sibling capability; inherit its
                // canonical category/domain instead of trusting caller text.
                newTag.setTagCategory(matchedCapability.getTagCategory());
                if (matchedCapability.getDomain() != null) {
                    newTag.setDomain(matchedCapability.getDomain());
                }
                log.info("技能标签归层: tagName={}, ability={}, source={}, confidence={}",
                        tagName, matchedCapability.getTagName(), classify.source(), classify.confidence());
                return classify;
            }
        } catch (Exception e) {
            log.warn("技能归层失败，保持能力层: tagName={}, error={}", tagName, e.getMessage());
        }
        return null;
    }

    private void addToKnowledgeBase(AbilityTag tag) {
        KnowledgeDocumentService docService = knowledgeDocumentServiceProvider.getIfAvailable();
        if (docService == null) {
            log.debug("KnowledgeDocumentService不可用，跳过知识库写入");
            return;
        }

        String content = String.format(
                "能力标签：%s\n分类：%s\n来源：%s\n描述：%s领域的能力标签，由AI自动发现并创建。",
                tag.getTagName(),
                tag.getTagCategory(),
                tag.getSourceType(),
                tag.getTagCategory()
        );

        KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
        dto.setSourceType("ABILITY_TAG");
        dto.setSourceRefId(tag.getId());
        dto.setTitle("能力标签: " + tag.getTagName());
        dto.setContent(content);

        docService.saveDocument(dto);
        log.info("新标签已写入知识库: tagName={}, tagId={}", tag.getTagName(), tag.getId());
    }

    private void evictAbilityTagCaches() {
        if (cacheManager == null) {
            return;
        }
        for (String cacheName : List.of(RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
                RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS,
                RedisCacheNames.ABILITY_TAG_INFO, RedisCacheNames.TAG_CANONICAL)) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
