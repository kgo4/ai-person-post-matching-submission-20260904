package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.enums.EvidenceSourceEnum;
import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.service.system.AbilityTagRelationService;
import com.example.matching.service.system.TagCanonicalCacheInvalidator;
import com.example.matching.service.system.PostAbilityTagGovernanceService;
import com.example.matching.port.post.PostQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.matching.ai.service.VectorEmbeddingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 能力标签关系 服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagRelationServiceImpl extends ServiceImpl<AbilityTagRelationMapper, AbilityTagRelation>
        implements AbilityTagRelationService {

    private final AbilityTagMapper abilityTagMapper;
    private final AbilityTagAliasMapper tagAliasMapper;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final TagCanonicalCacheInvalidator tagCanonicalCacheInvalidator;
    private final PostQueryPort postQueryPort;
    private final PostAbilityTagGovernanceService postAbilityTagGovernanceService;
    private final AtomicBoolean discoveryRunning = new AtomicBoolean(false);

    @Override
    public IPage<AbilityTagRelation> pageRelations(IPage<AbilityTagRelation> page,
                                                    Long sourceTagId, Long targetTagId,
                                                    String relationType, String status) {
        LambdaQueryWrapper<AbilityTagRelation> wrapper = Wrappers.<AbilityTagRelation>lambdaQuery();
        if (sourceTagId != null) {
            wrapper.eq(AbilityTagRelation::getSourceTagId, sourceTagId);
        }
        if (targetTagId != null) {
            wrapper.eq(AbilityTagRelation::getTargetTagId, targetTagId);
        }
        if (relationType != null && !relationType.isBlank()) {
            wrapper.eq(AbilityTagRelation::getRelationType, relationType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AbilityTagRelation::getStatus, status);
        }
        wrapper.orderByDesc(AbilityTagRelation::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public AbilityTagRelation createRelation(Long sourceTagId, Long targetTagId,
                                              String relationType, Double similarityScore,
                                              String evidenceSource, String remark, Long createdBy) {
        // 参数校验
        if (sourceTagId == null || targetTagId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }
        if (sourceTagId.equals(targetTagId)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }

        // 检查标签是否存在
        AbilityTag sourceTag = abilityTagMapper.selectById(sourceTagId);
        AbilityTag targetTag = abilityTagMapper.selectById(targetTagId);
        if (sourceTag == null || targetTag == null) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
        }

        // 检查关系是否已存在
        AbilityTagRelation existing = getOne(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getSourceTagId, sourceTagId)
                        .eq(AbilityTagRelation::getTargetTagId, targetTagId)
                        .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }

        // 验证关系类型
        RelationTypeEnum typeEnum = RelationTypeEnum.fromCode(relationType);
        if (typeEnum == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }

        AbilityTagRelation relation = new AbilityTagRelation();
        relation.setSourceTagId(sourceTagId);
        relation.setTargetTagId(targetTagId);
        relation.setRelationType(typeEnum.getCode());
        if (similarityScore != null) {
            relation.setSimilarityScore(BigDecimal.valueOf(similarityScore));
        }
        relation.setStatus(RelationStatusEnum.PENDING.getCode());
        if (evidenceSource != null) {
            EvidenceSourceEnum sourceEnum = EvidenceSourceEnum.fromCode(evidenceSource);
            relation.setEvidenceSource(sourceEnum != null ? sourceEnum.getCode() : evidenceSource);
        }
        relation.setRemark(remark);
        relation.setCreatedBy(createdBy);
        save(relation);

        log.info("创建标签关系: source={}, target={}, type={}", sourceTagId, targetTagId, relationType);
        return relation;
    }

    @Override
    @Transactional
    public void approveRelation(Long id, Long updatedBy) {
        AbilityTagRelation relation = getById(id);
        if (relation == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }
        if (!RelationStatusEnum.PENDING.getCode().equals(relation.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }

        relation.setStatus(RelationStatusEnum.CONFIRMED.getCode());
        relation.setUpdatedBy(updatedBy);
        updateById(relation);

        // 如果是SAME_AS关系，将两个标签归一到同一标准标签
        if (RelationTypeEnum.SAME_AS.getCode().equals(relation.getRelationType())) {
            mergeToSameCanonical(relation.getSourceTagId(), relation.getTargetTagId());
        }

        log.info("审核通过标签关系: id={}, type={}", id, relation.getRelationType());
    }

    @Override
    @Transactional
    public void rejectRelation(Long id, Long updatedBy) {
        AbilityTagRelation relation = getById(id);
        if (relation == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }
        if (!RelationStatusEnum.PENDING.getCode().equals(relation.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR);
        }

        relation.setStatus(RelationStatusEnum.REJECTED.getCode());
        relation.setUpdatedBy(updatedBy);
        updateById(relation);

        log.info("审核拒绝标签关系: id={}", id);
    }

    @Override
    public List<AbilityTagRelation> findRelationsBetween(Long tagId1, Long tagId2) {
        if (tagId1 == null || tagId2 == null) {
            return List.of();
        }

        List<AbilityTagRelation> forward = list(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getSourceTagId, tagId1)
                        .eq(AbilityTagRelation::getTargetTagId, tagId2));

        List<AbilityTagRelation> reverse = list(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getSourceTagId, tagId2)
                        .eq(AbilityTagRelation::getTargetTagId, tagId1));

        List<AbilityTagRelation> result = new ArrayList<>(forward);
        result.addAll(reverse);
        return result;
    }

    @Override
    @Transactional
    public int batchCreateCandidateRelations(Long sourceTagId, List<Long> similarTagIds,
                                               List<Double> similarityScores) {
        if (sourceTagId == null || similarTagIds == null || similarTagIds.isEmpty()) {
            return 0;
        }

        int created = 0;
        for (int i = 0; i < similarTagIds.size(); i++) {
            Long targetTagId = similarTagIds.get(i);
            Double score = (similarityScores != null && i < similarityScores.size()) ? similarityScores.get(i) : null;

            try {
                createRelation(sourceTagId, targetTagId,
                        RelationTypeEnum.SIMILAR.getCode(), score,
                        EvidenceSourceEnum.VECTOR_DISCOVERY.getCode(),
                        "向量自动发现", null);
                created++;
            } catch (BusinessException e) {
                // 关系已存在，跳过
                log.debug("跳过已存在的标签关系: source={}, target={}", sourceTagId, targetTagId);
            }
        }

        log.info("批量创建候选关系: source={}, created={}", sourceTagId, created);
        return created;
    }

    @Override
    public int discoverRelations(double threshold) {
        if (!discoveryRunning.compareAndSet(false, true)) {
            log.warn("标签关系自动发现已在执行，忽略重复请求");
            return 0;
        }
        try {
            return discoverRelationsInternal(threshold);
        } finally {
            discoveryRunning.set(false);
        }
    }

    private int discoverRelationsInternal(double threshold) {
        if (threshold < 0 || threshold > 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "相似度阈值必须在 0 到 1 之间");
        }
        // 自动发现前先把岗位能力表中的能力旁路同步为标签，保证标签库为空时也能正常开始发现。
        List<PostQueryPort.PostAbilityDTO> postAbilities = postQueryPort.listAllPostAbilityModels();
        if (postAbilities != null) {
            postAbilities.stream()
                    .filter(a -> a != null && a.abilityName() != null && !a.abilityName().isBlank())
                    .forEach(a -> postAbilityTagGovernanceService.govern(
                            new com.example.matching.event.PostAbilityTagGovernanceRequestedEvent(
                                    a.postId(), a.abilityName(), "TECHNICAL", "JD_IMPORT", a.id(),
                                    a.remark(), "自动发现前岗位能力旁路同步")));
        }

        // 获取所有启用且有向量的标签
        List<AbilityTag> allTags = abilityTagMapper.selectList(
                Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1));

        // 自动发现的完整语义是“生成向量 -> 相似度计算 -> 形成候选关系”。
        // 这里仅调用向量嵌入服务，不调用聊天模型，也不影响任何岗位/人员主流程。
        int generated = 0;
        for (AbilityTag tag : allTags) {
            if (tag.getEmbeddingVector() != null && !tag.getEmbeddingVector().isEmpty()) continue;
            try {
                List<Float> vector = vectorEmbeddingService.embed(tag.getTagName());
                if (vector != null && !vector.isEmpty()) {
                    tag.setEmbeddingVector(vector);
                    abilityTagMapper.updateById(tag);
                    generated++;
                }
            } catch (Exception ex) {
                log.warn("自动发现生成标签向量失败: tagId={}, tagName={}, error={}",
                        tag.getId(), tag.getTagName(), ex.getMessage());
            }
        }
        if (generated > 0) {
            allTags = abilityTagMapper.selectList(
                    Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1));
        }

        List<AbilityTag> tagsWithVector = allTags.stream()
                .filter(tag -> tag.getEmbeddingVector() != null && !tag.getEmbeddingVector().isEmpty())
                .toList();

        if (tagsWithVector.size() < 2) {
            log.info("有向量的标签数量不足，跳过关系发现: totalTags={}, vectorTags={}, generated={}",
                    allTags.size(), tagsWithVector.size(), generated);
            return 0;
        }

        log.info("开始标签关系发现: tagCount={}, threshold={}", tagsWithVector.size(), threshold);

        // 清除之前的向量自动发现结果（PENDING 状态），避免旧结果残留
        int deleted = getBaseMapper().delete(
                Wrappers.<AbilityTagRelation>lambdaQuery()
                        .eq(AbilityTagRelation::getEvidenceSource, EvidenceSourceEnum.VECTOR_DISCOVERY.getCode())
                        .eq(AbilityTagRelation::getStatus, RelationStatusEnum.PENDING.getCode()));
        log.info("清除旧的向量自动发现结果: deleted={}", deleted);

        int createdCount = 0;
        int comparedCount = 0;
        float maxSimilarity = 0;
        String maxPair = "";

        // 两两比较，避免重复（i < j）
        for (int i = 0; i < tagsWithVector.size(); i++) {
            for (int j = i + 1; j < tagsWithVector.size(); j++) {
                AbilityTag tag1 = tagsWithVector.get(i);
                AbilityTag tag2 = tagsWithVector.get(j);

                Float similarity = vectorEmbeddingService.cosineSimilarity(
                        tag1.getEmbeddingVector(), tag2.getEmbeddingVector());
                comparedCount++;

                if (similarity != null && similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    maxPair = tag1.getTagName() + " <-> " + tag2.getTagName();
                }

                if (similarity != null && similarity >= threshold) {
                    // 统一用较小ID作为source，避免重复
                    Long sourceId = Math.min(tag1.getId(), tag2.getId());
                    Long targetId = Math.max(tag1.getId(), tag2.getId());

                    try {
                        createRelation(sourceId, targetId,
                                RelationTypeEnum.SIMILAR.getCode(),
                                (double) similarity,
                                EvidenceSourceEnum.VECTOR_DISCOVERY.getCode(),
                                "向量自动发现 (similarity=" + String.format("%.4f", similarity) + ")",
                                null);
                        createdCount++;
                        log.debug("发现相似标签: {} <-> {} (similarity={})", tag1.getTagName(), tag2.getTagName(), similarity);
                    } catch (BusinessException e) {
                        // 关系已存在，跳过
                    }
                }
            }
        }

        log.info("标签关系发现完成: compared={}, created={}, maxSimilarity={} (pair: {})",
                comparedCount, createdCount, String.format("%.4f", maxSimilarity), maxPair);
        return createdCount;
    }


    /**
     * 将两个标签归一到同一标准标签
     * <p>
     * 将source标签的canonical_tag_id设置为target标签的canonical_tag_id。
     * 同时将source标签名称保存为target标签的别名（避免别名丢失）。
     */
    private void mergeToSameCanonical(Long sourceTagId, Long targetTagId) {
        AbilityTag sourceTag = abilityTagMapper.selectById(sourceTagId);
        AbilityTag targetTag = abilityTagMapper.selectById(targetTagId);
        if (sourceTag == null || targetTag == null) return;

        // 获取target的标准标签ID
        Long targetCanonicalId = targetTag.getCanonicalTagId() != null ? targetTag.getCanonicalTagId() : targetTag.getId();

        // 更新source标签及其同标准标签的canonical_tag_id
        Long sourceCanonicalId = sourceTag.getCanonicalTagId() != null ? sourceTag.getCanonicalTagId() : sourceTag.getId();

        // 将source标准标签下的所有标签归并到target标准标签下，并补充别名记录
        List<AbilityTag> sourceGroupTags = abilityTagMapper.selectList(
                Wrappers.<AbilityTag>lambdaQuery()
                        .eq(AbilityTag::getCanonicalTagId, sourceCanonicalId));

        for (AbilityTag tag : sourceGroupTags) {
            // 更新canonical_tag_id
            tag.setCanonicalTagId(targetCanonicalId);
            abilityTagMapper.updateById(tag);

            // 失效 TAG_CANONICAL 缓存：getCanonicalTagId 带 30min TTL，不失效会导致
            // 审批通过后匹配/推荐最长 30 分钟仍使用旧 canonical id（结果错误）
            try {
                tagCanonicalCacheInvalidator.evictCanonicalCache(tag.getId());
            } catch (Exception evictEx) {
                log.warn("Failed to evict canonical cache for tagId={}, will self-heal after TTL: {}",
                        tag.getId(), evictEx.getMessage());
            }

            // 补充别名记录（跳过target标签自身，避免冗余）
            if (!tag.getId().equals(targetTagId)) {
                saveAliasIfAbsent(targetTagId, tag.getTagName());
            }
        }

        log.info("标签归一完成: sourceCanonical={}, targetCanonical={}, affectedTags={}",
                sourceCanonicalId, targetCanonicalId, sourceGroupTags.size());
    }

    /**
     * 保存标签别名（如果不存在）
     */
    private void saveAliasIfAbsent(Long tagId, String aliasName) {
        AbilityTagAlias existing = tagAliasMapper.selectOne(
                Wrappers.<AbilityTagAlias>lambdaQuery()
                        .eq(AbilityTagAlias::getTagId, tagId)
                        .eq(AbilityTagAlias::getAliasName, aliasName)
                        .last("LIMIT 1"));
        if (existing == null) {
            AbilityTagAlias alias = new AbilityTagAlias();
            alias.setTagId(tagId);
            alias.setAliasName(aliasName);
            alias.setCreatedTime(java.time.LocalDateTime.now());
            tagAliasMapper.insert(alias);
            log.debug("补充标签别名: tagId={}, alias={}", tagId, aliasName);
        }
    }
}
