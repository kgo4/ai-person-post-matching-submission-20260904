package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagCanonicalMap;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.tag.TagQueryPort.TagRelationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagQueryPortAdapter implements TagQueryPort {

    private final AbilityTagMapper abilityTagMapper;
    private final AbilityTagRelationMapper tagRelationMapper;
    private final com.example.matching.mapper.system.AbilityTagAliasMapper abilityTagAliasMapper;

    @Override
    public Map<Long, TagRelationDTO> batchFindConfirmedSimilarRelations(Long sourceTagId, Collection<Long> targetTagIds) {
        if (sourceTagId == null || targetTagIds == null || targetTagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, TagRelationDTO> result = new HashMap<>();

        // 查询 source -> target 方向
        List<AbilityTagRelation> forwardRelations = tagRelationMapper.selectList(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getSourceTagId, sourceTagId)
                        .in(AbilityTagRelation::getTargetTagId, targetTagIds)
                        .eq(AbilityTagRelation::getRelationType, RelationTypeEnum.SIMILAR.getCode())
                        .eq(AbilityTagRelation::getStatus, RelationStatusEnum.CONFIRMED.getCode()));
        for (AbilityTagRelation rel : forwardRelations) {
            result.put(rel.getTargetTagId(), new TagRelationDTO(
                    rel.getSourceTagId(), rel.getTargetTagId(), rel.getSimilarityScore()));
        }

        // 查询 target -> source 方向（对于没有正向关系的target）
        Set<Long> remainingTargets = targetTagIds.stream()
                .filter(id -> !result.containsKey(id))
                .collect(Collectors.toSet());
        if (!remainingTargets.isEmpty()) {
            List<AbilityTagRelation> reverseRelations = tagRelationMapper.selectList(
                    Wrappers.<AbilityTagRelation>lambdaQuery()
                            .in(AbilityTagRelation::getSourceTagId, remainingTargets)
                            .eq(AbilityTagRelation::getTargetTagId, sourceTagId)
                            .eq(AbilityTagRelation::getRelationType, RelationTypeEnum.SIMILAR.getCode())
                            .eq(AbilityTagRelation::getStatus, RelationStatusEnum.CONFIRMED.getCode()));
            for (AbilityTagRelation rel : reverseRelations) {
                result.put(rel.getSourceTagId(), new TagRelationDTO(
                        rel.getSourceTagId(), rel.getTargetTagId(), rel.getSimilarityScore()));
            }
        }

        return result;
    }

    @Override
    public Map<Long, Map<Long, TagRelationDTO>> batchFindConfirmedSimilarRelationsForSources(
            Collection<Long> sourceTagIds, Collection<Long> targetTagIds) {
        if (sourceTagIds == null || sourceTagIds.isEmpty()
                || targetTagIds == null || targetTagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> sources = sourceTagIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> targets = targetTagIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (sources.isEmpty() || targets.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Map<Long, TagRelationDTO>> result = new HashMap<>();
        List<AbilityTagRelation> forwardRelations = tagRelationMapper.selectList(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .in(AbilityTagRelation::getSourceTagId, sources)
                        .in(AbilityTagRelation::getTargetTagId, targets)
                        .eq(AbilityTagRelation::getRelationType, RelationTypeEnum.SIMILAR.getCode())
                        .eq(AbilityTagRelation::getStatus, RelationStatusEnum.CONFIRMED.getCode()));
        for (AbilityTagRelation relation : forwardRelations) {
            result.computeIfAbsent(relation.getSourceTagId(), key -> new HashMap<>())
                    .put(relation.getTargetTagId(), new TagRelationDTO(
                            relation.getSourceTagId(), relation.getTargetTagId(), relation.getSimilarityScore()));
        }

        List<AbilityTagRelation> reverseRelations = tagRelationMapper.selectList(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .in(AbilityTagRelation::getSourceTagId, targets)
                        .in(AbilityTagRelation::getTargetTagId, sources)
                        .eq(AbilityTagRelation::getRelationType, RelationTypeEnum.SIMILAR.getCode())
                        .eq(AbilityTagRelation::getStatus, RelationStatusEnum.CONFIRMED.getCode()));
        for (AbilityTagRelation relation : reverseRelations) {
            Long sourceTagId = relation.getTargetTagId();
            Long targetTagId = relation.getSourceTagId();
            // 与单源查询保持一致：同一对标签优先保留正向关系。
            result.computeIfAbsent(sourceTagId, key -> new HashMap<>())
                    .putIfAbsent(targetTagId, new TagRelationDTO(
                            sourceTagId, targetTagId, relation.getSimilarityScore()));
        }
        return result;
    }

    @Override
    public List<TagDTO> batchGetTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();
        return abilityTagMapper.selectBatchIds(tagIds).stream()
                .map(TagDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<TagDTO> listByCategory(String category) {
        if (category == null || category.isBlank()) return List.of();
        return abilityTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AbilityTag>()
                        .eq(AbilityTag::getTagCategory, category)
                        .eq(AbilityTag::getStatus, 1)
        ).stream().map(TagDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<TagDTO> listChildren(Long parentId) {
        if (parentId == null) return List.of();
        return abilityTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AbilityTag>()
                        .eq(AbilityTag::getParentId, parentId)
                        .eq(AbilityTag::getStatus, 1)
        ).stream().map(TagDTO::from).collect(Collectors.toList());
    }

    @Override
    public TagCanonicalMap batchGetCanonicalIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return new TagCanonicalMap(Map.of());

        Map<Long, Long> result = new HashMap<>();
        List<AbilityTag> tags = abilityTagMapper.selectBatchIds(tagIds);
        for (AbilityTag tag : tags) {
            Long canonicalId = tag.getCanonicalTagId() != null ? tag.getCanonicalTagId() : tag.getId();
            result.put(tag.getId(), canonicalId);
        }
        return new TagCanonicalMap(result);
    }

    @Override
    public TagDTO getTagByName(String tagName) {
        if (tagName == null || tagName.isBlank()) return null;
        AbilityTag tag = abilityTagMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AbilityTag>()
                        .eq(AbilityTag::getTagName, tagName)
                        .eq(AbilityTag::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return tag != null ? TagDTO.from(tag) : null;
    }

    @Override
    public List<TagDTO> listActiveTags(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AbilityTag>()
                .eq(AbilityTag::getStatus, 1)
                .eq(AbilityTag::getIsDeleted, 0);
        if (limit > 0) w.last("LIMIT " + limit);
        return abilityTagMapper.selectList(w).stream().map(TagDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<TagDTO> listAllTags() {
        return abilityTagMapper.selectList(Wrappers.<AbilityTag>lambdaQuery()).stream()
                .map(TagDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<String> listAliases(Long tagId) {
        if (tagId == null) {
            return Collections.emptyList();
        }
        return abilityTagAliasMapper.selectList(
                        Wrappers.<com.example.matching.entity.system.AbilityTagAlias>lambdaQuery()
                                .eq(com.example.matching.entity.system.AbilityTagAlias::getTagId, tagId))
                .stream()
                .map(com.example.matching.entity.system.AbilityTagAlias::getAliasName)
                .filter(alias -> alias != null && !alias.isBlank())
                .collect(Collectors.toList());
    }
}
