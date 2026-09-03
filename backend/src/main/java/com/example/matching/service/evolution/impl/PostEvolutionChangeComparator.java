package com.example.matching.service.evolution.impl;

import com.example.matching.common.enums.ChangeTypeEnum;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.mapper.system.AbilityTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位能力变更比较器：对比新旧能力模型，生成变更项。
 * <p>
 * 从 PostEvolutionServiceImpl（1100+ 行）中拆分的纯计算组件，
 * 只依赖能力标签查询，便于独立测试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionChangeComparator {

    private final AbilityTagMapper abilityTagMapper;

    public List<PostEvolutionChangeItem> compareAbilities(Long taskId, List<PostAbilityModel> currentAbilities,
                                                          List<JdAbilityItemDTO> newAbilities) {
        List<PostEvolutionChangeItem> changeItems = new ArrayList<>();
        Map<Long, String> tagNameMap = loadTagNameMap(currentAbilities, newAbilities);

        Map<Long, PostAbilityModel> currentMap = currentAbilities.stream()
                // tagId is optional for source-validated role abilities. They cannot participate
                // in a canonical-tag comparison, but must never make the evolution task fail.
                .filter(model -> model.getTagId() != null)
                .collect(Collectors.toMap(PostAbilityModel::getTagId, m -> m, (a, b) -> a));

        Map<Long, JdAbilityItemDTO> newMap = new HashMap<>();
        for (JdAbilityItemDTO item : newAbilities) {
            if (item.getMatchedTagId() != null) {
                newMap.put(item.getMatchedTagId(), item);
            }
        }

        for (JdAbilityItemDTO newItem : newAbilities) {
            if (newItem.getMatchedTagId() != null && currentMap.containsKey(newItem.getMatchedTagId())) {
                PostAbilityModel current = currentMap.get(newItem.getMatchedTagId());
                if (!Objects.equals(current.getMinRequiredLevel(), newItem.getMinRequiredLevel())) {
                    PostEvolutionChangeItem changeItem = createChangeItem(taskId, newItem.getMatchedTagId(), resolveExistingAbilityName(newItem, tagNameMap));
                    changeItem.setChangeType("UPDATED_LEVEL");
                    changeItem.setOldLevel(current.getMinRequiredLevel());
                    changeItem.setNewLevel(newItem.getMinRequiredLevel());
                    changeItems.add(changeItem);
                }
                if (newItem.getWeight() != null && (current.getWeight() == null
                        || current.getWeight().subtract(newItem.getWeight()).abs().compareTo(new BigDecimal("5")) >= 0)) {
                    PostEvolutionChangeItem changeItem = createChangeItem(taskId, newItem.getMatchedTagId(), resolveExistingAbilityName(newItem, tagNameMap));
                    changeItem.setChangeType("UPDATED_WEIGHT");
                    changeItem.setOldWeight(current.getWeight());
                    changeItem.setNewWeight(newItem.getWeight());
                    changeItems.add(changeItem);
                }
                if (!Objects.equals(current.getIsCore(), newItem.getIsCore())) {
                    PostEvolutionChangeItem changeItem = createChangeItem(taskId, newItem.getMatchedTagId(), resolveExistingAbilityName(newItem, tagNameMap));
                    changeItem.setChangeType("UPDATED_CORE");
                    changeItem.setOldIsCore(current.getIsCore());
                    changeItem.setNewIsCore(newItem.getIsCore());
                    changeItems.add(changeItem);
                }
            } else if (newItem.getMatchedTagId() == null || !currentMap.containsKey(newItem.getMatchedTagId())) {
                PostEvolutionChangeItem changeItem = createChangeItem(taskId, newItem.getMatchedTagId(), resolveNewAbilityName(newItem, tagNameMap));
                changeItem.setChangeType(ChangeTypeEnum.ADDED.getCode());
                changeItem.setNewLevel(newItem.getMinRequiredLevel());
                changeItem.setNewWeight(newItem.getWeight());
                changeItem.setNewIsCore(newItem.getIsCore());
                changeItems.add(changeItem);
            }
        }

        for (PostAbilityModel current : currentAbilities) {
            if (current.getTagId() == null) {
                continue;
            }
            if (!newMap.containsKey(current.getTagId())) {
                PostEvolutionChangeItem changeItem = createChangeItem(taskId, current.getTagId(), resolveTagName(current.getTagId(), tagNameMap));
                changeItem.setChangeType(ChangeTypeEnum.REMOVED.getCode());
                changeItem.setOldLevel(current.getMinRequiredLevel());
                changeItem.setOldWeight(current.getWeight());
                changeItem.setOldIsCore(current.getIsCore());
                changeItems.add(changeItem);
            }
        }

        return changeItems;
    }

    private Map<Long, String> loadTagNameMap(List<PostAbilityModel> currentAbilities, List<JdAbilityItemDTO> newAbilities) {
        Set<Long> tagIds = new HashSet<>();
        for (PostAbilityModel ability : currentAbilities) {
            if (ability.getTagId() != null) {
                tagIds.add(ability.getTagId());
            }
        }
        for (JdAbilityItemDTO ability : newAbilities) {
            if (ability.getMatchedTagId() != null) {
                tagIds.add(ability.getMatchedTagId());
            }
        }
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<AbilityTag> tags = abilityTagMapper.selectBatchIds(tagIds);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        return tags.stream()
                .filter(tag -> tag.getId() != null && hasText(tag.getTagName()))
                .collect(Collectors.toMap(AbilityTag::getId, AbilityTag::getTagName, (a, b) -> a));
    }

    private String resolveExistingAbilityName(JdAbilityItemDTO item, Map<Long, String> tagNameMap) {
        String tagName = tagNameMap.get(item.getMatchedTagId());
        if (hasText(tagName)) {
            return tagName;
        }
        if (hasText(item.getMatchedTagName())) {
            return item.getMatchedTagName();
        }
        if (hasText(item.getSuggestedName())) {
            return item.getSuggestedName();
        }
        return resolveTagName(item.getMatchedTagId(), tagNameMap);
    }

    private String resolveNewAbilityName(JdAbilityItemDTO item, Map<Long, String> tagNameMap) {
        if (hasText(item.getSuggestedName())) {
            return item.getSuggestedName();
        }
        if (hasText(item.getMatchedTagName())) {
            return item.getMatchedTagName();
        }
        return resolveTagName(item.getMatchedTagId(), tagNameMap);
    }

    private String resolveTagName(Long tagId, Map<Long, String> tagNameMap) {
        if (tagId == null) {
            return "New ability";
        }
        String tagName = tagNameMap.get(tagId);
        if (hasText(tagName)) {
            return tagName;
        }
        return "Ability #" + tagId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private PostEvolutionChangeItem createChangeItem(Long taskId, Long tagId, String abilityName) {
        PostEvolutionChangeItem item = new PostEvolutionChangeItem();
        item.setTaskId(taskId);
        item.setTagId(tagId);
        item.setAbilityName(abilityName);
        item.setConfirmStatus("PENDING");
        item.setSupportScore(BigDecimal.ZERO);
        return item;
    }

    public String buildChangeClaimText(PostEvolutionChangeItem item) {
        StringBuilder sb = new StringBuilder();
        switch (item.getChangeType()) {
            case "ADDED":
                sb.append("新增 ");
                sb.append(item.getAbilityName());
                if (item.getNewLevel() != null) {
                    sb.append("，要求等级").append(item.getNewLevel());
                }
                break;
            case "REMOVED":
                sb.append("移除 ").append(item.getAbilityName());
                break;
            case "UPDATED_LEVEL":
                sb.append("调整 ").append(item.getAbilityName());
                sb.append(" 等级 ").append(item.getOldLevel()).append(" -> ").append(item.getNewLevel());
                break;
            case "UPDATED_WEIGHT":
                sb.append("调整 ").append(item.getAbilityName());
                sb.append(" 权重 ").append(item.getOldWeight()).append(" -> ").append(item.getNewWeight());
                break;
            case "UPDATED_CORE":
                sb.append("调整 ").append(item.getAbilityName());
                sb.append(" 核心标志 ").append(item.getOldIsCore()).append(" -> ").append(item.getNewIsCore());
                break;
            default:
                sb.append(item.getAbilityName()).append(" 变更");
        }
        return sb.toString();
    }
}
