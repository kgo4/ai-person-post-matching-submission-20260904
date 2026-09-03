package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选标签服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagCandidateServiceImpl extends ServiceImpl<AbilityTagCandidateMapper, AbilityTagCandidate>
        implements AbilityTagCandidateService {

    private final AbilityTagMapper abilityTagMapper;
    private final ObjectProvider<AbilityTagService> abilityTagServiceProvider;

    @Override
    public IPage<AbilityTagCandidate> pageCandidates(IPage<AbilityTagCandidate> page,
                                                      String status,
                                                      String sourceType,
                                                      String keyword) {
        var wrapper = Wrappers.<AbilityTagCandidate>lambdaQuery();

        if (StringUtils.hasText(status)) {
            wrapper.eq(AbilityTagCandidate::getStatus, status);
        }
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(AbilityTagCandidate::getSourceType, sourceType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AbilityTagCandidate::getCandidateName, keyword)
                    .or().like(AbilityTagCandidate::getReason, keyword));
        }

        wrapper.orderByDesc(AbilityTagCandidate::getOccurrenceCount)
                .orderByDesc(AbilityTagCandidate::getCreatedTime);

        return page(page, wrapper);
    }

    @Override
    @Transactional
    public Long addCandidate(AbilityTagCandidate candidate) {
        // 使用 candidateName + tagCategory + status=PENDING 进行去重
        AbilityTagCandidate existing = getOne(Wrappers.<AbilityTagCandidate>lambdaQuery()
                .eq(AbilityTagCandidate::getCandidateName, candidate.getCandidateName())
                .eq(AbilityTagCandidate::getTagCategory, candidate.getTagCategory() != null ? candidate.getTagCategory() : "TECHNICAL")
                .eq(AbilityTagCandidate::getStatus, "PENDING"));

        if (existing != null) {
            // 累计出现次数
            existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);

            // 只有当 sourcePostId 不同时才增加 relatedPostCount
            if (candidate.getSourcePostId() != null
                    && !candidate.getSourcePostId().equals(existing.getSourcePostId())) {
                existing.setRelatedPostCount(existing.getRelatedPostCount() + 1);
                existing.setSourcePostId(candidate.getSourcePostId()); // 更新为最新
            }

            // 只有当 sourceEmpId 不同时才增加 relatedEmpCount
            if (candidate.getSourceEmpId() != null
                    && !candidate.getSourceEmpId().equals(existing.getSourceEmpId())) {
                existing.setRelatedEmpCount(existing.getRelatedEmpCount() + 1);
                existing.setSourceEmpId(candidate.getSourceEmpId()); // 更新为最新
            }

            // 保留最新的 evidenceText
            if (StringUtils.hasText(candidate.getEvidenceText())) {
                existing.setEvidenceText(candidate.getEvidenceText());
            }

            // 保留更高的 similarityScore
            if (candidate.getSimilarityScore() != null
                    && (existing.getSimilarityScore() == null
                    || candidate.getSimilarityScore().compareTo(existing.getSimilarityScore()) > 0)) {
                existing.setSimilarityScore(candidate.getSimilarityScore());
                existing.setSimilarTagId(candidate.getSimilarTagId());
                existing.setSimilarTagName(candidate.getSimilarTagName());
            }

            // 保留更详细的 reasoning
            if (StringUtils.hasText(candidate.getReasoning())
                    && (existing.getReasoning() == null || candidate.getReasoning().length() > existing.getReasoning().length())) {
                existing.setReasoning(candidate.getReasoning());
            }

            // 更新来源信息
            if (StringUtils.hasText(candidate.getSourceType())) {
                existing.setSourceType(candidate.getSourceType());
            }
            if (candidate.getSourceRefId() != null) {
                existing.setSourceRefId(candidate.getSourceRefId());
            }

            existing.setUpdatedTime(LocalDateTime.now());
            updateById(existing);

            log.debug("候选标签已存在，累计出现次数: name={}, count={}, relatedEmpCount={}, relatedPostCount={}",
                    candidate.getCandidateName(), existing.getOccurrenceCount(),
                    existing.getRelatedEmpCount(), existing.getRelatedPostCount());
            return existing.getId();
        }

        // 设置默认值
        if (candidate.getOccurrenceCount() == null) {
            candidate.setOccurrenceCount(1);
        }
        if (candidate.getRelatedPostCount() == null) {
            candidate.setRelatedPostCount(candidate.getSourcePostId() != null ? 1 : 0);
        }
        if (candidate.getRelatedEmpCount() == null) {
            candidate.setRelatedEmpCount(candidate.getSourceEmpId() != null ? 1 : 0);
        }
        candidate.setStatus("PENDING");

        // 如果没有设置相似标签信息，尝试查找
        if (candidate.getSimilarTagId() == null) {
            findSimilarTag(candidate);
        }

        save(candidate);
        log.info("新增候选标签: id={}, name={}, source={}, similarity={}",
                candidate.getId(), candidate.getCandidateName(), candidate.getSourceType(),
                candidate.getSimilarityScore());
        return candidate.getId();
    }

    @Override
    @Transactional
    public void addCandidates(List<AbilityTagCandidate> candidates) {
        for (AbilityTagCandidate candidate : candidates) {
            addCandidate(candidate);
        }
    }

    @Override
    @Transactional
    public Long approve(Long candidateId, Long reviewerId, String comment) {
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "候选审批必须指定目标L1能力域");
    }

    @Override
    @Transactional
    public Long approve(Long candidateId, Long parentDomainId, Long reviewerId, String comment) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId)
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("approve").build();
        }
        if (!"PENDING".equals(candidate.getStatus())) {
            throw BusinessException.of(ErrorCodeEnum.STATE_CONFLICT, "候选标签状态不正确，无法审核: " + candidate.getStatus())
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("approve").build();
        }

        // 使用统一的正式标签创建链路（包含向量嵌入、canonicalTagId、知识库写入）
        AbilityTag newTag = abilityTagServiceProvider.getObject().createAssessableCapability(
                candidate.getCandidateName(),
                parentDomainId,
                candidate.getTagCategory(),
                candidate.getDomain(),
                candidate.getDescription() != null ? candidate.getDescription() : candidate.getReason(),
                "AI_CANDIDATE"
        );

        // 更新候选标签状态
        candidate.setStatus("APPROVED");
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewerId);
        candidate.setReviewedTime(LocalDateTime.now());
        candidate.setMergedTagId(newTag.getId());
        updateById(candidate);

        log.info("候选标签审核通过: candidateId={}, newTagId={}, name={}", candidateId, newTag.getId(), candidate.getCandidateName());
        return newTag.getId();
    }

    @Override
    @Transactional
    public void reject(Long candidateId, Long reviewerId, String comment) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId)
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("reject").build();
        }
        if (!"PENDING".equals(candidate.getStatus())) {
            throw BusinessException.of(ErrorCodeEnum.STATE_CONFLICT, "候选标签状态不正确，无法拒绝: " + candidate.getStatus())
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("reject").build();
        }

        candidate.setStatus("REJECTED");
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewerId);
        candidate.setReviewedTime(LocalDateTime.now());
        updateById(candidate);

        log.info("候选标签已拒绝: candidateId={}, name={}", candidateId, candidate.getCandidateName());
    }

    @Override
    @Transactional
    public void merge(Long candidateId, Long targetTagId, Long reviewerId, String comment) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId)
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("merge").build();
        }
        if (!"PENDING".equals(candidate.getStatus())) {
            throw BusinessException.of(ErrorCodeEnum.STATE_CONFLICT, "候选标签状态不正确，无法合并: " + candidate.getStatus())
                    .entity("ABILITY_TAG_CANDIDATE", candidateId).operation("merge").build();
        }

        AbilityTag targetTag = abilityTagMapper.selectById(targetTagId);
        if (targetTag == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "目标标签不存在: " + targetTagId)
                    .entity("ABILITY_TAG", targetTagId).operation("merge").build();
        }

        // 更新候选标签状态
        candidate.setStatus("MERGED");
        candidate.setMergedTagId(targetTagId);
        candidate.setReviewComment(comment != null ? comment : "合并到标签: " + targetTag.getTagName());
        candidate.setReviewedBy(reviewerId);
        candidate.setReviewedTime(LocalDateTime.now());
        updateById(candidate);

        // 修复：合并时把候选名补写为目标标签别名，防止下次同名声明重新走准入产生新候选
        // （原实现只改候选状态，防标签爆炸链断裂）
        try {
            AbilityTagService tagService = abilityTagServiceProvider.getIfAvailable();
            if (tagService != null && candidate.getCandidateName() != null && !candidate.getCandidateName().isBlank()) {
                tagService.addAlias(targetTagId, candidate.getCandidateName(), "CANDIDATE_MERGE");
            }
        } catch (Exception e) {
            log.warn("合并候选时保存别名失败（不影响合并结果）: candidateId={}, targetTagId={}, error={}",
                    candidateId, targetTagId, e.getMessage());
        }

        log.info("候选标签已合并: candidateId={}, targetTagId={}, name={}", candidateId, targetTagId, candidate.getCandidateName());
    }

    @Override
    public List<AbilityTagCandidate> getHighFrequencyCandidates(int threshold) {
        return list(Wrappers.<AbilityTagCandidate>lambdaQuery()
                .eq(AbilityTagCandidate::getStatus, "PENDING")
                .ge(AbilityTagCandidate::getOccurrenceCount, threshold)
                .orderByDesc(AbilityTagCandidate::getOccurrenceCount));
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> result = new HashMap<>();
        result.put("PENDING", count(Wrappers.<AbilityTagCandidate>lambdaQuery().eq(AbilityTagCandidate::getStatus, "PENDING")));
        result.put("APPROVED", count(Wrappers.<AbilityTagCandidate>lambdaQuery().eq(AbilityTagCandidate::getStatus, "APPROVED")));
        result.put("REJECTED", count(Wrappers.<AbilityTagCandidate>lambdaQuery().eq(AbilityTagCandidate::getStatus, "REJECTED")));
        result.put("MERGED", count(Wrappers.<AbilityTagCandidate>lambdaQuery().eq(AbilityTagCandidate::getStatus, "MERGED")));
        return result;
    }

    /**
     * 查找相似的正式标签
     */
    private void findSimilarTag(AbilityTagCandidate candidate) {
        // 简单实现：按名称模糊匹配
        // 后续可以接入向量相似度
        AbilityTag similar = abilityTagMapper.selectOne(Wrappers.<AbilityTag>lambdaQuery()
                .like(AbilityTag::getTagName, candidate.getCandidateName())
                .eq(AbilityTag::getStatus, 1)
                .last("LIMIT 1"));

        if (similar != null) {
            candidate.setSimilarTagId(similar.getId());
            candidate.setSimilarTagName(similar.getTagName());
        }
    }
}
