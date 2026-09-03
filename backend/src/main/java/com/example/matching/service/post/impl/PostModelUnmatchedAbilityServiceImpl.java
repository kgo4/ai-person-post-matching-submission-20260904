package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import com.example.matching.entity.post.PostModelUnmatchedAbility;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.mapper.post.PostModelUnmatchedAbilityMapper;
import com.example.matching.mapper.post.PostModelVersionItemMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import com.example.matching.service.post.PostModelVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 岗位模型未匹配能力标签服务实现（M-07）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModelUnmatchedAbilityServiceImpl implements PostModelUnmatchedAbilityService {

    /** 允许绑定标签的版本状态 */
    private static final Set<String> BINDABLE_VERSION_STATUS = Set.of("DRAFT", "REVIEW_REQUIRED");

    private final PostModelUnmatchedAbilityMapper unmatchedAbilityMapper;
    private final PostModelVersionItemMapper versionItemMapper;
    private final PostModelVersionService modelVersionService;
    private final AbilityTagMapper abilityTagMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<PostModelUnmatchedAbility> listByVersionId(Long versionId) {
        return unmatchedAbilityMapper.selectList(Wrappers.<PostModelUnmatchedAbility>lambdaQuery()
                .eq(PostModelUnmatchedAbility::getVersionId, versionId)
                .orderByAsc(PostModelUnmatchedAbility::getId));
    }

    @Override
    public PostModelUnmatchedAbility getById(Long id) {
        return unmatchedAbilityMapper.selectById(id);
    }

    @Override
    @Transactional
    public void saveAll(Long versionId, List<PostModelUnmatchedAbility> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return;
        }
        for (PostModelUnmatchedAbility ability : abilities) {
            ability.setId(null);
            ability.setVersionId(versionId);
            unmatchedAbilityMapper.insert(ability);
        }
        log.info("保存未匹配能力标签: versionId={}, 数量={}", versionId, abilities.size());
    }

    @Override
    public void updateCandidateId(Long id, Long candidateId) {
        PostModelUnmatchedAbility record = unmatchedAbilityMapper.selectById(id);
        if (record == null) {
            return;
        }
        record.setCandidateId(candidateId);
        unmatchedAbilityMapper.updateById(record);
    }

    @Override
    @Transactional
    public void bind(Long id, Long tagId) {
        PostModelUnmatchedAbility record = unmatchedAbilityMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未匹配能力记录不存在: " + id);
        }
        if (!PostModelUnmatchedAbility.STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "只能绑定待处理(PENDING)的未匹配能力记录");
        }

        AbilityTag tag = abilityTagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "标签不存在: " + tagId);
        }
        if (tag.getStatus() == null || tag.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "标签未启用，无法绑定: " + tagId);
        }

        PostModelVersion version = modelVersionService.getById(record.getVersionId());
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "岗位模型版本不存在: " + record.getVersionId());
        }
        if (!BINDABLE_VERSION_STATUS.contains(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "只能编辑草稿/待审核状态的版本，当前状态: " + version.getStatus());
        }

        // 0. 原子抢占：条件更新（status=PENDING）保证并发请求只有一个能继续，防止重复生成版本明细
        int claimed = unmatchedAbilityMapper.update(null,
                Wrappers.<PostModelUnmatchedAbility>lambdaUpdate()
                        .eq(PostModelUnmatchedAbility::getId, id)
                        .eq(PostModelUnmatchedAbility::getStatus, PostModelUnmatchedAbility.STATUS_PENDING)
                        .set(PostModelUnmatchedAbility::getStatus, PostModelUnmatchedAbility.STATUS_TAG_BOUND)
                        .set(PostModelUnmatchedAbility::getBoundTagId, tagId));
        if (claimed == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未匹配能力记录已被其他管理员处理");
        }

        // 1. 创建版本明细
        PostModelVersionItem item = new PostModelVersionItem();
        item.setVersionId(version.getId());
        item.setTagId(tagId);
        item.setMinRequiredLevel(record.getMinRequiredLevel() != null ? record.getMinRequiredLevel() : 2);
        item.setWeight(record.getWeight() != null ? record.getWeight() : BigDecimal.TEN);
        item.setIsRequired(record.getIsRequired() != null ? record.getIsRequired() : 0);
        item.setIsCore(record.getIsCore() != null ? record.getIsCore() : 0);
        item.setReason(record.getReasoning() != null ? record.getReasoning() : "未匹配能力人工绑定:" + record.getAbilityName());
        versionItemMapper.insert(item);

        // 2. 原子累加版本统计，避免不同未匹配项同时绑定时覆盖彼此的计数和权重。
        if (!modelVersionService.incrementStatisticsForBinding(version.getId(), item.getWeight())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位模型版本已不可编辑，请刷新后重试");
        }

        // 3. 发布岗位模型变更事件（向量同步等监听）
        try {
            eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", version.getPostId()));
        } catch (Exception e) {
            log.warn("发布岗位模型变更事件失败: postId={}, error={}", version.getPostId(), e.getMessage());
        }

        log.info("未匹配能力已绑定: id={}, tagId={}, versionId={}, abilityName={}",
                id, tagId, record.getVersionId(), record.getAbilityName());
    }

    @Override
    @Transactional
    public void ignore(Long id) {
        PostModelUnmatchedAbility record = unmatchedAbilityMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未匹配能力记录不存在: " + id);
        }
        // 条件更新原子抢占：只有 PENDING 状态能被置为 IGNORED
        int claimed = unmatchedAbilityMapper.update(null,
                Wrappers.<PostModelUnmatchedAbility>lambdaUpdate()
                        .eq(PostModelUnmatchedAbility::getId, id)
                        .eq(PostModelUnmatchedAbility::getStatus, PostModelUnmatchedAbility.STATUS_PENDING)
                        .set(PostModelUnmatchedAbility::getStatus, PostModelUnmatchedAbility.STATUS_IGNORED));
        if (claimed == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未匹配能力记录已被其他管理员处理");
        }
        log.info("未匹配能力已忽略: id={}, abilityName={}", id, record.getAbilityName());
    }

    @Override
    public UnmatchedAbilityDTO toDto(PostModelUnmatchedAbility ability) {
        if (ability == null) {
            return null;
        }
        String recommendedTagName = null;
        if (ability.getBoundTagId() != null) {
            AbilityTag tag = abilityTagMapper.selectById(ability.getBoundTagId());
            recommendedTagName = tag != null ? tag.getTagName() : null;
        }
        return UnmatchedAbilityDTO.builder()
                .id(ability.getId())
                .versionId(ability.getVersionId())
                .abilityName(ability.getAbilityName())
                .normalizedAbilityName(ability.getNormalizedAbilityName())
                .minRequiredLevel(ability.getMinRequiredLevel())
                .weight(ability.getWeight())
                .isRequired(ability.getIsRequired())
                .isCore(ability.getIsCore())
                .reasoning(ability.getReasoning())
                .reason(ability.getReason())
                .status(ability.getStatus())
                .recommendedTagId(ability.getBoundTagId())
                .recommendedTagName(recommendedTagName)
                .candidateId(ability.getCandidateId())
                .createdTime(ability.getCreatedTime())
                .build();
    }
}
