package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningResourceMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习资源工具 - 供LangChain4j Agent调用
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningResourceTool {

    private final LearningResourceMapper learningResourceMapper;

    private static final Map<String, Object> UNAVAILABLE_SINGLE;

    static {
        UNAVAILABLE_SINGLE = new HashMap<>();
        UNAVAILABLE_SINGLE.put("available", false);
        UNAVAILABLE_SINGLE.put("reason", "learning_resource_unavailable");
    }

    @Tool("根据能力名称搜索学习资源")
    public List<Map<String, Object>> searchLearningResources(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) {
            log.warn("[LEARNING_RESOURCE] searchLearningResources called with empty abilityName");
            Map<String, Object> unavailable = new HashMap<>();
            unavailable.put("available", false);
            unavailable.put("reason", "no_results_found");
            return List.of(unavailable);
        }

        try {
            log.info("Agent调用: searchLearningResources(abilityName={})", abilityName);

            LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(LearningResource::getStatus, 1);
            wrapper.and(w -> w
                    .like(LearningResource::getAbilityName, abilityName)
                    .or()
                    .like(LearningResource::getTitle, abilityName));
            wrapper.orderByAsc(LearningResource::getSortOrder);
            wrapper.last("LIMIT 10");

            List<Map<String, Object>> results = learningResourceMapper.selectList(wrapper).stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("available", false);
                empty.put("reason", "no_results_found");
                return List.of(empty);
            }
            return results;
        } catch (Exception e) {
            log.warn("[LEARNING_RESOURCE] searchLearningResources query failed: method=searchLearningResources, errorType={}",
                    e.getClass().getSimpleName());
            return List.of(UNAVAILABLE_SINGLE);
        }
    }

    @Tool("根据能力标签ID精确搜索学习资源")
    public List<Map<String, Object>> searchByTagId(Long tagId) {
        if (tagId == null) {
            log.warn("[LEARNING_RESOURCE] searchByTagId called with null tagId");
            Map<String, Object> unavailable = new HashMap<>();
            unavailable.put("available", false);
            unavailable.put("reason", "no_results_found");
            return List.of(unavailable);
        }

        try {
            log.info("Agent调用: searchByTagId(tagId={})", tagId);

            LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(LearningResource::getStatus, 1);
            wrapper.eq(LearningResource::getTagId, tagId);
            wrapper.orderByAsc(LearningResource::getSortOrder);
            wrapper.last("LIMIT 10");

            List<Map<String, Object>> results = learningResourceMapper.selectList(wrapper).stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("available", false);
                empty.put("reason", "no_results_found");
                return List.of(empty);
            }
            return results;
        } catch (Exception e) {
            log.warn("[LEARNING_RESOURCE] searchByTagId query failed: method=searchByTagId, errorType={}",
                    e.getClass().getSimpleName());
            return List.of(UNAVAILABLE_SINGLE);
        }
    }

    @Tool("获取学习资源详情")
    public Map<String, Object> getLearningResourceDetail(Long resourceId) {
        if (resourceId == null) {
            log.warn("[LEARNING_RESOURCE] getLearningResourceDetail called with null resourceId");
            return UNAVAILABLE_SINGLE;
        }

        try {
            log.info("Agent调用: getLearningResourceDetail(resourceId={})", resourceId);

            LearningResource resource = learningResourceMapper.selectById(resourceId);
            if (resource == null || resource.getStatus() != 1) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("available", false);
                empty.put("reason", "no_results_found");
                return empty;
            }
            return toMap(resource);
        } catch (Exception e) {
            log.warn("[LEARNING_RESOURCE] getLearningResourceDetail query failed: method=getLearningResourceDetail, errorType={}",
                    e.getClass().getSimpleName());
            return UNAVAILABLE_SINGLE;
        }
    }

    private Map<String, Object> toMap(LearningResource resource) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", resource.getId());
        map.put("resourceCode", resource.getResourceCode());
        map.put("abilityName", resource.getAbilityName());
        map.put("tagId", resource.getTagId());
        map.put("title", resource.getTitle());
        map.put("resourceType", resource.getResourceType());
        map.put("difficultyLevel", resource.getDifficultyLevel());
        map.put("url", resource.getUrl());
        map.put("description", resource.getDescription());
        map.put("platform", resource.getPlatform());
        map.put("platformIcon", resource.getPlatformIcon());
        map.put("coverImageUrl", resource.getCoverImageUrl());
        map.put("duration", resource.getDuration());
        return map;
    }
}
