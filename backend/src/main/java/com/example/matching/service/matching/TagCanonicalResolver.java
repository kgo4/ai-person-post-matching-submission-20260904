package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.system.TagCanonicalCacheInvalidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签归一解析组件
 * <p>
 * 职责：
 * - 输入标签ID，返回标准标签ID
 * - 输入两个标签ID，判断是否属于同一标准标签
 * - 查询已确认相近关系
 * <p>
 * 数据访问统一经 {@link TagQueryPort}，不再直接注入他域 Mapper。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagCanonicalResolver implements TagCanonicalCacheInvalidator {

    private final TagQueryPort tagQueryPort;

    /** 已确认的SIMILAR关系默认系数 */
    private static final BigDecimal CONFIRMED_SIMILAR_DEFAULT_COEFFICIENT = new BigDecimal("0.92");

    /**
     * 清除指定标签的 canonical 缓存（标签合并或关系变更时调用）
     */
    @Override
    @CacheEvict(cacheNames = RedisCacheNames.TAG_CANONICAL, key = "#tagId")
    public void evictCanonicalCache(Long tagId) {
        log.debug("Evicted canonical cache for tagId={}", tagId);
    }

    /**
     * 获取标签的标准标签ID
     * <p>
     * 如果标签有canonical_tag_id则返回该值，否则返回标签自身的ID。
     *
     * @param tagId 标签ID
     * @return 标准标签ID
     */
    @Cacheable(cacheNames = RedisCacheNames.TAG_CANONICAL, key = "#tagId", sync = true)
    public Long getCanonicalTagId(Long tagId) {
        if (tagId == null) return null;
        TagQueryPort.TagDTO tag = tagQueryPort.getTagById(tagId);
        if (tag == null) return tagId;
        return tag.canonicalTagId() != null ? tag.canonicalTagId() : tag.id();
    }

    /**
     * 批量获取标签的标准标签ID
     *
     * @param tagIds 标签ID列表
     * @return tagId -> canonicalTagId 的映射
     */
    public Map<Long, Long> batchGetCanonicalTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> result = new HashMap<>(tagQueryPort.batchGetCanonicalIds(
                new ArrayList<>(tagIds)).tagToCanonical());

        // 对于查询不到的标签，使用自身ID作为标准ID
        for (Long tagId : tagIds) {
            result.putIfAbsent(tagId, tagId);
        }

        return result;
    }

    /**
     * 判断两个标签是否属于同一标准标签
     *
     * @param tagId1 标签1的ID
     * @param tagId2 标签2的ID
     * @return true如果属于同一标准标签
     */
    public boolean isSameCanonical(Long tagId1, Long tagId2) {
        if (tagId1 == null || tagId2 == null) return false;
        if (tagId1.equals(tagId2)) return true;

        Long canonical1 = getCanonicalTagId(tagId1);
        Long canonical2 = getCanonicalTagId(tagId2);
        return canonical1 != null && canonical1.equals(canonical2);
    }

    /**
     * 查询两个标签之间的已确认SIMILAR关系
     * <p>
     * 会同时检查 (source, target) 和 (target, source) 两个方向。
     *
     * @param tagId1 标签1
     * @param tagId2 标签2
     * @return 已确认的SIMILAR关系，不存在返回null
     */
    public AbilityTagRelation findConfirmedSimilarRelation(Long tagId1, Long tagId2) {
        if (tagId1 == null || tagId2 == null) return null;
        return toRelation(tagQueryPort.batchFindConfirmedSimilarRelations(tagId1, List.of(tagId2)).get(tagId2));
    }

    /**
     * 获取已确认SIMILAR关系的相似度分数
     * <p>
     * 如果关系中记录了similarity_score则使用该值，否则使用默认系数。
     *
     * @param relation 已确认的SIMILAR关系
     * @return 命中系数
     */
    public BigDecimal getSimilarCoefficient(AbilityTagRelation relation) {
        if (relation == null) return CONFIRMED_SIMILAR_DEFAULT_COEFFICIENT;
        if (relation.getSimilarityScore() != null && relation.getSimilarityScore().compareTo(BigDecimal.ZERO) > 0) {
            return relation.getSimilarityScore();
        }
        return CONFIRMED_SIMILAR_DEFAULT_COEFFICIENT;
    }

    /**
     * 批量查询指定标签与其他标签之间的已确认SIMILAR关系
     * <p>
     * 返回一个映射：targetTagId -> relation
     *
     * @param sourceTagId 源标签ID
     * @param targetTagIds 目标标签ID列表
     * @return targetTagId -> relation 的映射
     */
    public Map<Long, AbilityTagRelation> batchFindConfirmedSimilarRelations(Long sourceTagId, Collection<Long> targetTagIds) {
        if (sourceTagId == null || targetTagIds == null || targetTagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, AbilityTagRelation> result = new HashMap<>();
        tagQueryPort.batchFindConfirmedSimilarRelations(sourceTagId, targetTagIds)
                .forEach((targetTagId, dto) -> result.put(targetTagId, toRelation(dto)));
        return result;
    }

    /**
     * 批量读取多个岗位能力与候选能力之间已确认的相近关系。
     */
    public Map<Long, Map<Long, AbilityTagRelation>> batchFindConfirmedSimilarRelationsForSources(
            Collection<Long> sourceTagIds, Collection<Long> targetTagIds) {
        if (sourceTagIds == null || sourceTagIds.isEmpty()
                || targetTagIds == null || targetTagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Long, AbilityTagRelation>> result = new HashMap<>();
        Map<Long, Map<Long, TagQueryPort.TagRelationDTO>> relations =
                tagQueryPort.batchFindConfirmedSimilarRelationsForSources(sourceTagIds, targetTagIds);
        if (relations == null) {
            return null;
        }
        relations.forEach((sourceTagId, targets) -> {
            if (targets == null || targets.isEmpty()) {
                return;
            }
            Map<Long, AbilityTagRelation> converted = new HashMap<>();
            targets.forEach((targetTagId, relation) -> converted.put(targetTagId, toRelation(relation)));
            result.put(sourceTagId, converted);
        });
        return result;
    }

    private static AbilityTagRelation toRelation(TagQueryPort.TagRelationDTO dto) {
        if (dto == null) {
            return null;
        }
        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSourceTagId(dto.sourceTagId());
        relation.setTargetTagId(dto.targetTagId());
        relation.setRelationType(RelationTypeEnum.SIMILAR.getCode());
        relation.setStatus(RelationStatusEnum.CONFIRMED.getCode());
        relation.setSimilarityScore(dto.similarityScore());
        return relation;
    }
}
