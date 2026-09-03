package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostModelVersionItemMapper;
import com.example.matching.mapper.post.PostModelVersionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostModelVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 岗位能力模型版本管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModelVersionServiceImpl extends ServiceImpl<PostModelVersionMapper, PostModelVersion>
        implements PostModelVersionService {

    private final PostModelVersionItemMapper versionItemMapper;
    private final PostAbilityModelService postAbilityModelService;
    private final AbilityTagMapper abilityTagMapper;

    @Override
    @Transactional
    public PostModelVersion createDraft(Long postId, String sourceType, String description) {
        // 生成版本号
        String versionNo = "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        PostModelVersion version = new PostModelVersion();
        version.setPostId(postId);
        version.setVersionNo(versionNo);
        version.setSourceType(sourceType);
        version.setStatus("DRAFT");
        version.setDescription(description);
        version.setItemCount(0);
        version.setTotalWeight(BigDecimal.ZERO);
        save(version);

        log.info("创建草稿版本: postId={}, versionId={}, versionNo={}", postId, version.getId(), versionNo);
        return version;
    }

    @Override
    @Transactional
    public void saveVersionItems(Long versionId, List<PostModelVersionItem> items) {
        PostModelVersion version = getById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "版本不存在: " + versionId);
        }
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "只能编辑草稿状态的版本");
        }

        // 删除旧明细
        versionItemMapper.delete(Wrappers.<PostModelVersionItem>lambdaQuery()
                .eq(PostModelVersionItem::getVersionId, versionId));

        // 插入新明细
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (PostModelVersionItem item : items) {
            item.setId(null);
            item.setVersionId(versionId);
            versionItemMapper.insert(item);
            totalWeight = totalWeight.add(item.getWeight() != null ? item.getWeight() : BigDecimal.ZERO);
        }

        // 更新版本统计
        version.setItemCount(items.size());
        version.setTotalWeight(totalWeight.setScale(2, RoundingMode.HALF_UP));
        updateById(version);

        log.info("保存版本明细: versionId={}, 项数={}, 权重总和={}", versionId, items.size(), totalWeight);
    }

    @Override
    public boolean incrementStatisticsForBinding(Long versionId, BigDecimal itemWeight) {
        BigDecimal safeWeight = itemWeight != null ? itemWeight : BigDecimal.ZERO;
        return baseMapper.update(null, Wrappers.<PostModelVersion>lambdaUpdate()
                .eq(PostModelVersion::getId, versionId)
                .in(PostModelVersion::getStatus, "DRAFT", "REVIEW_REQUIRED")
                .setSql("item_count = COALESCE(item_count, 0) + 1")
                .setSql("total_weight = COALESCE(total_weight, 0) + {0}", safeWeight)
                .setSql("status = CASE WHEN status = 'REVIEW_REQUIRED' THEN 'DRAFT' ELSE status END")) == 1;
    }

    @Override
    @Transactional
    public void publishVersion(Long versionId) {
        PostModelVersion version = getById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "版本不存在: " + versionId);
        }
        if ("ACTIVE".equals(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "版本已发布");
        }

        // 获取版本明细
        List<PostModelVersionItem> items = getVersionItems(versionId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "版本没有能力项配置");
        }

        // 将当前已发布的版本归档
        List<PostModelVersion> activeVersions = list(Wrappers.<PostModelVersion>lambdaQuery()
                .eq(PostModelVersion::getPostId, version.getPostId())
                .eq(PostModelVersion::getStatus, "ACTIVE"));
        for (PostModelVersion activeVersion : activeVersions) {
            activeVersion.setStatus("ARCHIVED");
            updateById(activeVersion);
        }

        // 将版本明细转换为 PostAbilityModelConfigDTO
        List<PostAbilityModelConfigDTO> configList = new ArrayList<>();
        for (PostModelVersionItem item : items) {
            PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
            config.setPostId(version.getPostId());
            config.setTagId(item.getTagId());
            config.setMinRequiredLevel(item.getMinRequiredLevel());
            config.setWeight(item.getWeight());
            config.setIsRequired(item.getIsRequired());
            config.setIsCore(item.getIsCore());
            config.setRemark(item.getReason());
            configList.add(config);
        }

        // 调用 batchConfig 生效
        postAbilityModelService.batchConfig(configList);

        // 更新版本状态
        version.setStatus("ACTIVE");
        version.setPublishTime(LocalDateTime.now());
        updateById(version);

        log.info("版本已发布: versionId={}, postId={}, 能力项数={}", versionId, version.getPostId(), items.size());
    }

    @Override
    @Transactional
    public void rollbackToVersion(Long versionId) {
        PostModelVersion version = getById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "版本不存在: " + versionId);
        }
        if ("DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "草稿版本不能回滚，请先发布");
        }

        // 重新发布该版本
        version.setStatus("DRAFT");
        updateById(version);
        publishVersion(versionId);

        log.info("回滚到版本: versionId={}, postId={}", versionId, version.getPostId());
    }

    @Override
    public List<PostModelVersion> listVersions(Long postId) {
        return list(Wrappers.<PostModelVersion>lambdaQuery()
                .eq(PostModelVersion::getPostId, postId)
                .orderByDesc(PostModelVersion::getCreatedTime));
    }

    @Override
    public PostModelVersion getVersionDetail(Long versionId) {
        return getById(versionId);
    }

    @Override
    public List<PostModelVersionItem> getVersionItems(Long versionId) {
        return versionItemMapper.selectList(Wrappers.<PostModelVersionItem>lambdaQuery()
                .eq(PostModelVersionItem::getVersionId, versionId));
    }

    @Override
    @Transactional
    public void deleteDraft(Long versionId) {
        PostModelVersion version = getById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "版本不存在: " + versionId);
        }
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "只能删除草稿状态的版本");
        }

        // 删除明细
        versionItemMapper.delete(Wrappers.<PostModelVersionItem>lambdaQuery()
                .eq(PostModelVersionItem::getVersionId, versionId));

        // 删除版本
        removeById(versionId);

        log.info("删除草稿版本: versionId={}", versionId);
    }
}
