package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagUsageStat;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagUsageStatMapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.system.AbilityTagGovernanceService;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 能力标签治理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagGovernanceServiceImpl extends ServiceImpl<AbilityTagCandidateMapper, AbilityTagCandidate>
        implements AbilityTagGovernanceService {

    private final AbilityTagCandidateMapper candidateMapper;
    private final AbilityTagUsageStatMapper usageStatMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final AbilityTagService abilityTagService;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;

    @Override
    public IPage<AbilityTagCandidate> pageCandidates(IPage<AbilityTagCandidate> page, String status, String sourceType) {
        var wrapper = Wrappers.<AbilityTagCandidate>lambdaQuery();
        if (StringUtils.hasText(status)) {
            wrapper.eq(AbilityTagCandidate::getStatus, status);
        }
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(AbilityTagCandidate::getSourceType, sourceType);
        }
        wrapper.orderByDesc(AbilityTagCandidate::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public Long approveCandidate(Long candidateId, String tagCategory, Long parentDomainId, Long reviewedBy) {
        return approveCandidate(candidateId, tagCategory, parentDomainId, reviewedBy, null, null);
    }

    @Override
    @Transactional
    public Long approveCandidate(Long candidateId, String tagCategory, Long parentDomainId, Long reviewedBy,
                                 String editedCandidateName, String reviewComment) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId).entity("ABILITY_TAG_CANDIDATE", candidateId).build();
        }
        requirePending(candidate, "approve");

        if (StringUtils.hasText(editedCandidateName)) {
            candidate.setCandidateName(editedCandidateName.trim());
        }
        if (!StringUtils.hasText(candidate.getCandidateName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "正式标签名称不能为空");
        }
        if (StringUtils.hasText(tagCategory)) {
            candidate.setTagCategory(tagCategory.trim());
        }

        // 使用统一的正式标签创建方法（不走 findOrCreateByName）
        AbilityTag newTag = abilityTagService.createAssessableCapability(
                candidate.getCandidateName(),
                parentDomainId,
                tagCategory != null ? tagCategory : candidate.getTagCategory(),
                candidate.getDomain() != null ? candidate.getDomain() : "GENERAL",
                candidate.getDescription() != null ? candidate.getDescription() : candidate.getReason(),
                "CANDIDATE_APPROVED"
        );

        // 更新候选状态
        candidate.setStatus("APPROVED");
        candidate.setMergedTagId(newTag.getId());
        candidate.setReviewedBy(reviewedBy);
        candidate.setReviewedTime(LocalDateTime.now());
        candidate.setReviewComment(StringUtils.hasText(reviewComment)
                ? reviewComment.trim() : "审核通过，创建正式标签: " + newTag.getTagName());
        updateById(candidate);

        log.info("候选标签已批准: candidateId={}, newTagId={}, name={}", candidateId, newTag.getId(), candidate.getCandidateName());
        return newTag.getId();
    }

    @Override
    public void rejectCandidate(Long candidateId, Long reviewedBy, String reason) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId).entity("ABILITY_TAG_CANDIDATE", candidateId).build();
        }
        requirePending(candidate, "reject");

        candidate.setStatus("REJECTED");
        candidate.setReviewedBy(reviewedBy);
        candidate.setReviewedTime(LocalDateTime.now());
        candidate.setReasoning(reason);
        updateById(candidate);

        log.info("候选标签已拒绝: candidateId={}, name={}, reason={}", candidateId, candidate.getCandidateName(), reason);
    }

    @Override
    @Transactional
    public void mergeCandidateToExisting(Long candidateId, Long targetTagId, Long reviewedBy) {
        AbilityTagCandidate candidate = getById(candidateId);
        if (candidate == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "候选标签不存在: " + candidateId).entity("ABILITY_TAG_CANDIDATE", candidateId).build();
        }
        requirePending(candidate, "merge");

        // 验证目标标签存在
        AbilityTag targetTag = abilityTagService.getById(targetTagId);
        if (targetTag == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "目标标签不存在: " + targetTagId).entity("ABILITY_TAG", targetTagId).build();
        }

        // 将候选名称作为目标标签的别名（不创建新标签）
        abilityTagService.addAlias(targetTagId, candidate.getCandidateName(), candidate.getSourceType());

        // 更新候选状态
        candidate.setStatus("MERGED");
        candidate.setMergedTagId(targetTagId);
        candidate.setMatchedTagId(targetTagId);
        candidate.setReviewedBy(reviewedBy);
        candidate.setReviewedTime(LocalDateTime.now());
        candidate.setReviewComment("合并到标签: " + targetTag.getTagName());
        updateById(candidate);

        log.info("候选标签已合并: candidateId={}, candidateName={}, targetTagId={}, targetTagName={}",
                candidateId, candidate.getCandidateName(), targetTagId, targetTag.getTagName());
    }

    private void requirePending(AbilityTagCandidate candidate, String operation) {
        if (!"PENDING".equals(candidate.getStatus())) {
            throw BusinessException.of(ErrorCodeEnum.STATE_CONFLICT,
                            "候选标签状态不正确，无法" + operation + ": " + candidate.getStatus())
                    .entity("ABILITY_TAG_CANDIDATE", candidate.getId())
                    .operation(operation)
                    .build();
        }
    }

    @Override
    @Transactional
    public void computeUsageStats() {
        LocalDate today = LocalDate.now();

        // 岗位能力表是主数据源。先做无 AI、幂等的旁路同步，确保标签健康页不会因
        // tagId 未挂载或历史事件未消费而显示空数据。
        syncPostAbilityTags();
        List<com.example.matching.port.post.PostQueryPort.PostAbilityDTO> postAbilities =
                postQueryPort.listAllPostAbilityModels();

        // 获取所有启用的标签
        List<AbilityTag> allTags = abilityTagService.list(
                Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1));

        // 清理孤儿统计记录：删除 tag_id 不在当前启用标签中的旧记录
        Set<Long> activeTagIds = allTags.stream().map(AbilityTag::getId).collect(Collectors.toSet());
        if (!activeTagIds.isEmpty()) {
            usageStatMapper.delete(
                    Wrappers.<AbilityTagUsageStat>lambdaQuery().notIn(AbilityTagUsageStat::getTagId, activeTagIds));
        } else {
            // 没有启用标签时清空所有统计
            usageStatMapper.delete(Wrappers.<AbilityTagUsageStat>lambdaQuery().isNotNull(AbilityTagUsageStat::getId));
        }

        for (AbilityTag tag : allTags) {
            // 统计被多少岗位引用
            Long postCount = countPostRequirementsByName(tag.getTagName(), postAbilities);

            // 统计被多少员工引用
            Long empCount = talentQueryPort.countAbilitiesByTagId(tag.getId());

            // 计算热度分数（简单公式：岗位引用*2 + 员工引用*1）
            BigDecimal heatScore = BigDecimal.valueOf(postCount * 2 + empCount);

            // 更新或插入统计记录
            AbilityTagUsageStat existing = usageStatMapper.selectOne(
                    Wrappers.<AbilityTagUsageStat>lambdaQuery()
                            .eq(AbilityTagUsageStat::getTagId, tag.getId())
                            .eq(AbilityTagUsageStat::getStatDate, today));

            if (existing != null) {
                existing.setUsedByPostCount(postCount.intValue());
                existing.setUsedByEmpCount(empCount.intValue());
                existing.setHeatScore(heatScore);
                usageStatMapper.updateById(existing);
            } else {
                AbilityTagUsageStat stat = new AbilityTagUsageStat();
                stat.setTagId(tag.getId());
                stat.setUsedByPostCount(postCount.intValue());
                stat.setUsedByEmpCount(empCount.intValue());
                stat.setHeatScore(heatScore);
                stat.setStatDate(today);
                usageStatMapper.insert(stat);
            }
        }

        log.info("标签使用统计计算完成: tagCount={}, cleanedOrphanStats=true", allTags.size());
    }

    private void syncPostAbilityTags() {
        List<com.example.matching.port.post.PostQueryPort.PostAbilityDTO> abilities =
                postQueryPort.listAllPostAbilityModels();
        if (abilities == null) return;
        Set<String> names = abilities.stream()
                .map(com.example.matching.port.post.PostQueryPort.PostAbilityDTO::abilityName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(this::isValidAbilityName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String name : names) {
            AbilityTag existing = abilityTagService.findByName(name);
            if (existing == null) existing = abilityTagService.findByAlias(name);
            if (existing != null) continue;
            try {
                com.example.matching.dto.system.AbilityTagSaveDTO dto =
                        new com.example.matching.dto.system.AbilityTagSaveDTO();
                dto.setTagCode("POST_ABILITY_" + Integer.toUnsignedString(name.toLowerCase(Locale.ROOT).hashCode()));
                dto.setTagName(name);
                dto.setParentId(0L);
                dto.setTagCategory("TECHNICAL");
                dto.setTagLevel(0);
                dto.setDescription("来自岗位能力表的标准能力名称");
                dto.setSortOrder(0);
                abilityTagService.saveTag(dto);
            } catch (Exception ex) {
                // 单条失败不影响其余标签和主业务，下次统计时继续补偿。
                log.warn("岗位能力同步标签失败: ability={}, error={}", name, ex.getMessage());
            }
        }
    }

    private Long countPostRequirementsByName(String tagName,
            List<com.example.matching.port.post.PostQueryPort.PostAbilityDTO> postAbilities) {
        if (!StringUtils.hasText(tagName)) return 0L;
        String normalized = normalizeName(tagName);
        return (postAbilities == null ? List.<com.example.matching.port.post.PostQueryPort.PostAbilityDTO>of() : postAbilities).stream()
                .map(com.example.matching.port.post.PostQueryPort.PostAbilityDTO::abilityName)
                .filter(StringUtils::hasText)
                .map(this::normalizeName)
                .filter(normalized::equals)
                .count();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isValidAbilityName(String value) {
        String normalized = normalizeName(value);
        return !normalized.isBlank() && !normalized.matches("能力#?(null|未命名能力)?")
                && !normalized.equals("null") && !normalized.equals("unknown")
                && !normalized.equals("未命名能力");
    }

    @Override
    public List<AbilityTagUsageStat> getUsageStats(int topN) {
        int limit = Math.max(1, Math.min(topN, 500));
        List<AbilityTag> activeTags = abilityTagService.list(
                Wrappers.<AbilityTag>lambdaQuery()
                        .eq(AbilityTag::getStatus, 1)
                        .orderByAsc(AbilityTag::getSortOrder)
                        .orderByAsc(AbilityTag::getId));
        List<AbilityTagUsageStat> persistedStats = usageStatMapper.selectList(
                Wrappers.<AbilityTagUsageStat>lambdaQuery()
                        .orderByDesc(AbilityTagUsageStat::getStatDate)
                        .orderByDesc(AbilityTagUsageStat::getHeatScore)
                        .last("LIMIT 5000"));

        // 同一标签可能有多天快照，取最新一条；没有快照的启用标签也返回 0 值，避免健康页被“架空”。
        Map<Long, AbilityTagUsageStat> latestByTag = new LinkedHashMap<>();
        for (AbilityTagUsageStat stat : persistedStats) {
            if (stat.getTagId() != null) latestByTag.putIfAbsent(stat.getTagId(), stat);
        }

        List<AbilityTagUsageStat> result = new ArrayList<>();
        for (AbilityTag tag : activeTags) {
            AbilityTagUsageStat stat = latestByTag.get(tag.getId());
            if (stat == null) {
                stat = new AbilityTagUsageStat();
                stat.setTagId(tag.getId());
                stat.setUsedByPostCount(0);
                stat.setUsedByEmpCount(0);
                stat.setHeatScore(BigDecimal.ZERO);
            }
            stat.setTagName(tag.getTagName());
            stat.setTagCategory(tag.getTagCategory());
            result.add(stat);
        }
        result.sort(Comparator.comparing(
                (AbilityTagUsageStat s) -> s.getHeatScore() == null ? BigDecimal.ZERO : s.getHeatScore())
                .reversed());
        return result.stream().limit(limit).toList();
    }

    @Override
    public void addCandidate(String candidateName, String tagCategory, String sourceType,
                             Long sourceRefId, Long matchedTagId, Double similarityScore, String reasoning) {
        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setCandidateName(candidateName);
        candidate.setTagCategory(tagCategory);
        candidate.setSourceType(sourceType);
        candidate.setSourceRefId(sourceRefId);
        candidate.setMatchedTagId(matchedTagId);
        candidate.setSimilarityScore(similarityScore != null ? BigDecimal.valueOf(similarityScore) : null);
        candidate.setStatus("PENDING");
        candidate.setReasoning(reasoning);
        candidateMapper.insert(candidate);

        log.info("添加候选标签: name={}, source={}, matched={}", candidateName, sourceType, matchedTagId);
    }
}
