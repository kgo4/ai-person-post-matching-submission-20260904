package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.post.PostModelUnmatchedAbility;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.post.PostPrototypeTag;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.post.PostPrototypeTagMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostModelGenerationService;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import com.example.matching.service.post.PostModelVersionService;
import com.example.matching.service.system.AbilityTagHierarchy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 岗位模型生成中心服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModelGenerationServiceImpl implements PostModelGenerationService {

    private final PostModelVersionService modelVersionService;
    private final PostPrototypeTagMapper prototypeTagMapper;
    private final PostAbilityModelService postAbilityModelService;
    private final PostCapabilityGenerationService capabilityGenerationService;
    private final AbilityTagMapper abilityTagMapper;
    private final PostPostMapper postPostMapper;
    private final PostModelUnmatchedAbilityService unmatchedAbilityService;

    @Override
    @Transactional
    public PostModelVersion generateFromPrototype(Long postId, Long prototypeId, String description) {
        // 1. 获取原型标签
        List<PostPrototypeTag> protoTags = prototypeTagMapper.selectList(
                Wrappers.<PostPrototypeTag>lambdaQuery()
                        .eq(PostPrototypeTag::getPrototypeId, prototypeId)
                        .orderByAsc(PostPrototypeTag::getSortOrder));

        if (protoTags.isEmpty()) {
            throw BusinessException.of(ErrorCodeEnum.PARAM_ERROR, "岗位原型没有关联的能力标签").entity("POST_PROTOTYPE", prototypeId).build();
        }

        // 2. 创建草稿版本
        PostModelVersion version = modelVersionService.createDraft(postId, "TEMPLATE",
                description != null ? description : "从岗位原型生成");

        // 3. 转换为版本明细
        List<PostModelVersionItem> items = new ArrayList<>();
        for (PostPrototypeTag pt : protoTags) {
            PostModelVersionItem item = new PostModelVersionItem();
            item.setTagId(pt.getTagId());
            item.setMinRequiredLevel(pt.getMinRequiredLevel());
            item.setWeight(pt.getWeight());
            item.setIsRequired(pt.getIsRequired());
            item.setIsCore(pt.getIsCore());
            item.setReason("来自岗位原型配置");
            items.add(item);
        }

        // 4. 保存版本明细
        modelVersionService.saveVersionItems(version.getId(), items);

        log.info("从原型生成草稿: postId={}, prototypeId={}, versionId={}, 能力项数={}",
                postId, prototypeId, version.getId(), items.size());

        return modelVersionService.getVersionDetail(version.getId());
    }

    @Override
    @Transactional
    public PostModelVersion generateFromJD(Long postId, String jdText, String description) {
        // 1. 调用AI分析JD
        List<JdAbilityItemDTO> abilities = capabilityGenerationService.analyzePostText(
                getPostName(postId), jdText);

        if (abilities == null || abilities.isEmpty()) {
            throw BusinessException.of(ErrorCodeEnum.PARAM_ERROR, "AI未能从JD中提取能力要求").entity("POST", postId).operation("generateFromJD").build();
        }

        // 2. 创建草稿版本
        PostModelVersion version = modelVersionService.createDraft(postId, "JD_AI",
                description != null ? description : "从JD智能生成");

        // 3. 转换为版本明细，未匹配能力进入未匹配列表（M-07）
        List<PostModelVersionItem> items = new ArrayList<>();
        List<PostModelUnmatchedAbility> unmatchedAbilities = new ArrayList<>();
        for (JdAbilityItemDTO ability : abilities) {
            ResolvedTag resolved = resolveTag(ability);
            if (resolved.tagId() == null) {
                log.warn("无法匹配能力标签: suggestedName={}, reason={}, 进入未匹配列表",
                        ability.getSuggestedName(), resolved.reason());
                unmatchedAbilities.add(toUnmatchedAbility(ability, resolved.reason()));
                continue;
            }

            PostModelVersionItem item = new PostModelVersionItem();
            item.setTagId(resolved.tagId());
            item.setMinRequiredLevel(ability.getMinRequiredLevel() != null ? ability.getMinRequiredLevel() : 2);
            item.setWeight(ability.getWeight() != null ? ability.getWeight() : BigDecimal.TEN);
            item.setIsRequired(ability.getIsRequired() != null ? ability.getIsRequired() : 0);
            item.setIsCore(ability.getIsCore() != null ? ability.getIsCore() : 0);
            item.setReason(ability.getReasoning());
            items.add(item);
        }

        if (items.isEmpty()) {
            // 全部未匹配：保留草稿版本供管理员查看，状态置为 REVIEW_REQUIRED
            version.setStatus("REVIEW_REQUIRED");
            modelVersionService.updateById(version);
            log.warn("JD全部能力未匹配，创建待审核草稿: postId={}, versionId={}, 未匹配数量={}",
                    postId, version.getId(), unmatchedAbilities.size());
        }

        // 4. 保存版本明细
        if (!items.isEmpty()) {
            modelVersionService.saveVersionItems(version.getId(), items);
        }

        // 5. 持久化未匹配能力并进入标签候选流程
        saveUnmatchedAbilities(postId, version, unmatchedAbilities);

        log.info("从JD生成草稿: postId={}, versionId={}, 能力项数={}, 未匹配能力数={}",
                postId, version.getId(), items.size(), unmatchedAbilities.size());

        return modelVersionService.getVersionDetail(version.getId());
    }

    /**
     * 解析 AI 提取能力对应的标签，返回 null 表示无法匹配（含未匹配原因）。
     */
    private ResolvedTag resolveTag(JdAbilityItemDTO ability) {
        if (ability.getMatchedTagId() != null) {
            AbilityTag matched = abilityTagMapper.selectById(ability.getMatchedTagId());
            if (matched == null || matched.getIsDeleted() == null || matched.getIsDeleted() == 1) {
                return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_MATCHED_TAG_ID_NOT_FOUND);
            }
            if (matched.getStatus() == null || matched.getStatus() != 1) {
                return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_TAG_DISABLED);
            }
            return AbilityTagHierarchy.isAssessable(matched)
                    ? new ResolvedTag(matched.getId(), null)
                    : new ResolvedTag(null, "TAG_NOT_ASSESSABLE_L2");
        }
        if (ability.getSuggestedName() != null) {
            // 通过名称查找：查询候选集合，过滤删除/禁用项；多条有效候选视为名称歧义
            List<AbilityTag> nameMatches = abilityTagMapper.selectList(
                    Wrappers.<AbilityTag>lambdaQuery()
                            .eq(AbilityTag::getTagName, ability.getSuggestedName())
                            .eq(AbilityTag::getIsDeleted, 0));
            List<AbilityTag> enabledMatches = nameMatches.stream()
                    .filter(AbilityTagHierarchy::isAssessable)
                    .toList();
            if (enabledMatches.isEmpty()) {
                if (!nameMatches.isEmpty()) {
                    return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_TAG_DISABLED);
                }
                return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_TAG_NAME_NOT_FOUND);
            }
            if (enabledMatches.size() > 1) {
                log.warn("能力名称存在歧义: suggestedName={}, 匹配到 {} 个启用标签，进入未匹配列表",
                        ability.getSuggestedName(), enabledMatches.size());
                return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_TAG_NAME_AMBIGUOUS);
            }
            return new ResolvedTag(enabledMatches.get(0).getId(), null);
        }
        return new ResolvedTag(null, PostModelUnmatchedAbility.REASON_TAG_NAME_NOT_FOUND);
    }

    /**
     * 构建未匹配能力记录（M-07）
     */
    private PostModelUnmatchedAbility toUnmatchedAbility(JdAbilityItemDTO ability, String reason) {
        PostModelUnmatchedAbility record = new PostModelUnmatchedAbility();
        record.setAbilityName(ability.getSuggestedName());
        record.setNormalizedAbilityName(AbilityNameNormalizer.normalize(ability.getSuggestedName()));
        record.setReason(reason);
        record.setMinRequiredLevel(ability.getMinRequiredLevel() != null ? ability.getMinRequiredLevel() : 2);
        record.setWeight(ability.getWeight() != null ? ability.getWeight() : BigDecimal.TEN);
        record.setIsRequired(ability.getIsRequired() != null ? ability.getIsRequired() : 0);
        record.setIsCore(ability.getIsCore() != null ? ability.getIsCore() : 0);
        record.setReasoning(ability.getReasoning());
        record.setStatus(PostModelUnmatchedAbility.STATUS_PENDING);
        return record;
    }

    /** 持久化草稿中的未匹配能力；草稿生成不进入系统标签库。 */
    private void saveUnmatchedAbilities(Long postId, PostModelVersion version,
                                        List<PostModelUnmatchedAbility> unmatchedAbilities) {
        if (unmatchedAbilities.isEmpty()) {
            return;
        }
        unmatchedAbilityService.saveAll(version.getId(), unmatchedAbilities);
    }

    private record ResolvedTag(Long tagId, String reason) {
    }

    @Override
    @Transactional
    public PostModelVersion generateFromCopy(Long sourcePostId, Long targetPostId, String description) {
        // 1. 获取源岗位的能力模型
        List<PostAbilityModel> sourceModels = postAbilityModelService.listByPostId(sourcePostId);
        if (sourceModels.isEmpty()) {
            throw BusinessException.of(ErrorCodeEnum.POST_NOT_FOUND, "源岗位没有能力模型配置").entity("POST", sourcePostId).operation("generateFromCopy").build();
        }

        // 2. 创建草稿版本
        PostModelVersion version = modelVersionService.createDraft(targetPostId, "COPY",
                description != null ? description : "从岗位ID=" + sourcePostId + "复制");

        // 3. 转换为版本明细
        List<PostModelVersionItem> items = new ArrayList<>();
        for (PostAbilityModel model : sourceModels) {
            PostModelVersionItem item = new PostModelVersionItem();
            item.setTagId(model.getTagId());
            item.setMinRequiredLevel(model.getMinRequiredLevel());
            item.setWeight(model.getWeight());
            item.setIsRequired(model.getIsRequired());
            item.setIsCore(model.getIsCore());
            item.setReason("从岗位ID=" + sourcePostId + "复制");
            items.add(item);
        }

        // 4. 保存版本明细
        modelVersionService.saveVersionItems(version.getId(), items);

        log.info("从复制生成草稿: sourcePostId={}, targetPostId={}, versionId={}, 能力项数={}",
                sourcePostId, targetPostId, version.getId(), items.size());

        return modelVersionService.getVersionDetail(version.getId());
    }

    // ===== 内部方法 =====

    private String getPostName(Long postId) {
        PostPost post = postPostMapper.selectById(postId);
        return post != null ? post.getPostName() : "岗位" + postId;
    }
}
