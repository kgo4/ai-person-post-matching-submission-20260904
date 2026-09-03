package com.example.matching.port.tag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 标签查询端口 — 公开只读接口。
 * <p>
 * 其他域只能通过此接口查询标签信息，禁止直接注入 AbilityTagMapper。
 * 返回 DTO，不暴露 Entity。
 */
public interface TagQueryPort {

    /** 批量查询标签基本信息 */
    List<TagDTO> batchGetTags(List<Long> tagIds);

    /** 按 ID 查询单个标签，未找到返回 null */
    default TagDTO getTagById(Long tagId) {
        if (tagId == null) return null;
        List<TagDTO> list = batchGetTags(List.of(tagId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 按标签名查询单个标签，未找到返回 null */
    TagDTO getTagByName(String tagName);

    /** 按分类查询标签列表 */
    List<TagDTO> listByCategory(String category);

    /** 查询子标签 */
    List<TagDTO> listChildren(Long parentId);

    /** 查询标签的标准化ID映射 */
    TagCanonicalMap batchGetCanonicalIds(List<Long> tagIds);

    /** 分页列出活跃标签（用于批量回填），limit <= 0 则不限制 */
    List<TagDTO> listActiveTags(int limit);

    /** 全量标签（报表统计用） */
    List<TagDTO> listAllTags();

    /**
     * 批量查询已确认 SIMILAR 关系（双向：source->target 与 target->source）。
     * 返回 targetTagId -> 关系 的映射，未找到的关系不出现。
     */
    Map<Long, TagRelationDTO> batchFindConfirmedSimilarRelations(Long sourceTagId, Collection<Long> targetTagIds);

    /**
     * 批量查询多个源标签到多个目标标签之间已确认的 SIMILAR 关系。
     * <p>
     * 默认实现保留旧端口语义，供非持久化适配器兼容；数据库适配器应覆盖为批量查询，
     * 避免候选预评分按"岗位能力数 x 候选人数"发起 N+1 查询。
     */
    default Map<Long, Map<Long, TagRelationDTO>> batchFindConfirmedSimilarRelationsForSources(
            Collection<Long> sourceTagIds, Collection<Long> targetTagIds) {
        if (sourceTagIds == null || sourceTagIds.isEmpty()
                || targetTagIds == null || targetTagIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, TagRelationDTO>> result = new java.util.HashMap<>();
        for (Long sourceTagId : sourceTagIds) {
            if (sourceTagId == null) {
                continue;
            }
            Map<Long, TagRelationDTO> relations = batchFindConfirmedSimilarRelations(sourceTagId, targetTagIds);
            if (relations != null && !relations.isEmpty()) {
                result.put(sourceTagId, relations);
            }
        }
        return result;
    }

    /** 标签标准化ID映射结果 */
    record TagCanonicalMap(java.util.Map<Long, Long> tagToCanonical) {}

    /** 已确认 SIMILAR 关系只读 DTO */
    record TagRelationDTO(Long sourceTagId, Long targetTagId, BigDecimal similarityScore) {}

    /** 标签只读 DTO */
    record TagDTO(
            Long id,
            String tagName,
            String tagCode,
            String tagCategory,
            String domain,
            Integer tagLevel,
            Long parentId,
            Long canonicalTagId,
            String sourceType,
            String description,
            List<Float> embeddingVector,
            LocalDateTime createdTime
    ) {
        public static TagDTO from(com.example.matching.entity.system.AbilityTag tag) {
            return new TagDTO(
                    tag.getId(), tag.getTagName(), tag.getTagCode(),
                    tag.getTagCategory(), tag.getDomain(), tag.getTagLevel(),
                    tag.getParentId(), tag.getCanonicalTagId(), tag.getSourceType(),
                    tag.getDescription(), tag.getEmbeddingVector(), tag.getCreatedTime()
            );
        }
    }

    /**
     * 查询标签的受控别名列表（ability_tag_alias），未找到返回空列表。
     */
    List<String> listAliases(Long tagId);
}
