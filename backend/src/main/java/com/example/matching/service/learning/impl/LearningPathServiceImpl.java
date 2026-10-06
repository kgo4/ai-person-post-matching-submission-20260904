package com.example.matching.service.learning.impl;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.service.learning.LearningPathService;
import com.example.matching.service.learning.support.LearningResourceMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径服务实现
 * <p>
 * 基于检索的确定性学习路径推荐。
 * <p>
 * 资源关联规则统一使用 {@link LearningResourceMatcher}：
 * abilityName 归一化为主关联，tagId 仅辅助；无资源时保留能力差距但不阻断。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathServiceImpl implements LearningPathService {

    private final LearningResourceMapper resourceMapper;

    @Override
    public List<LearningPathItemDTO> generateLearningPath(LearningPathRequestDTO request) {
        if (request.getAbilityNames() == null || request.getAbilityNames().isEmpty()) {
            return List.of();
        }

        List<LearningPathItemDTO> result = new ArrayList<>();
        int currentLevel = request.getCurrentLevel() != null ? request.getCurrentLevel() : 1;
        int targetLevel = request.getTargetLevel() != null ? request.getTargetLevel() : 3;
        int levelGap = targetLevel - currentLevel;

        // 加载所有启用的资源
        List<LearningResource> allResources = loadAllEnabledResources();

        List<Long> tagIds = request.getTagIds();
        List<String> abilityNames = request.getAbilityNames();

        for (int i = 0; i < abilityNames.size(); i++) {
            String abilityName = abilityNames.get(i);
            Long tagId = (tagIds != null && i < tagIds.size()) ? tagIds.get(i) : null;

            // 能力名称是业务主数据；标签库关联只用于补充召回。统一匹配规则，避免与学习路径主流程口径不一致。
            List<LearningResource> matched = LearningResourceMatcher.matchAndSort(
                    allResources, abilityName, tagId, currentLevel, targetLevel);

            if (matched.isEmpty()) {
                LearningPathItemDTO item = new LearningPathItemDTO();
                item.setAbilityName(abilityName);
                item.setTagId(tagId);
                item.setTitle("暂无推荐资源");
                item.setDescription("系统暂未收录「" + abilityName + "」相关学习资源，请联系管理员添加");
                result.add(item);
            } else {
                int recommendCount = levelGap >= 3 ? 5 : levelGap >= 2 ? 3 : 2;
                for (LearningResource r : matched.stream().limit(recommendCount).collect(Collectors.toList())) {
                    result.add(toPathItem(r, abilityName, tagId, currentLevel, targetLevel));
                }
            }
        }

        return result;
    }

    /**
     * 加载所有启用的学习资源
     */
    private List<LearningResource> loadAllEnabledResources() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningResource> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getStatus, 1);
        return resourceMapper.selectList(wrapper);
    }

    /**
     * 将 LearningResource 转换为 LearningPathItemDTO
     */
    private LearningPathItemDTO toPathItem(LearningResource r, String abilityName, Long tagId,
                                            int currentLevel, int targetLevel) {
        LearningPathItemDTO item = new LearningPathItemDTO();
        item.setAbilityName(abilityName);
        item.setTagId(tagId != null ? tagId : r.getTagId());
        item.setResourceId(r.getId());
        item.setTitle(r.getTitle());
        item.setResourceType(r.getResourceType());
        item.setDifficultyLevel(r.getDifficultyLevel());
        item.setUrl(r.getUrl());
        item.setPlatform(r.getPlatform());
        item.setPlatformIcon(r.getPlatformIcon());
        item.setCoverImageUrl(r.getCoverImageUrl());
        item.setDuration(r.getDuration());
        item.setDescription(buildPersonalizedDescription(r, currentLevel, targetLevel));
        return item;
    }

    /**
     * 构建个性化描述
     */
    private String buildPersonalizedDescription(LearningResource r, int currentLevel, int targetLevel) {
        StringBuilder desc = new StringBuilder();

        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            desc.append(r.getDescription());
        }

        int resourceLevel = r.getDifficultyLevel() != null ? r.getDifficultyLevel() : 3;
        if (resourceLevel <= currentLevel) {
            desc.append(" [巩固基础]");
        } else if (resourceLevel <= targetLevel) {
            desc.append(" [核心提升]");
        } else {
            desc.append(" [进阶拓展]");
        }

        return desc.toString();
    }
}

