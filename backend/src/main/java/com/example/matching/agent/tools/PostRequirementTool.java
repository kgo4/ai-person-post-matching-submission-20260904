package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 岗位要求工具 - 供LangChain4j Agent调用
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostRequirementTool {

    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;

    @Tool("获取岗位基本信息")
    public Map<String, Object> getPostInfo(Long postId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("postId", postId);
        if (validation.isPresent()) {
            log.warn("getPostInfo invalid input: {}", validation.get());
            return Map.of("available", false, "found", false, "reason", validation.get());
        }

        log.info("Agent调用: getPostInfo(postId={})", postId);
        try {
            PostPost post = postPostMapper.selectById(postId);
            if (post == null || post.getIsDeleted() == 1) {
                return Map.of("available", true, "found", false, "reason", "post not found");
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", post.getId());
            item.put("postCode", post.getPostCode());
            item.put("postName", post.getPostName());
            item.put("postLevel", post.getPostLevel());
            item.put("departmentId", post.getDepartmentId());
            item.put("jobDescription", post.getJobDescription());

            return Map.of("available", true, "found", true, "item", item);
        } catch (Exception e) {
            log.error("getPostInfo 查询失败: postId={}", postId, e);
            return Map.of("available", false, "found", false, "reason", "post_data_unavailable");
        }
    }

    @Tool("获取岗位能力要求列表")
    public Map<String, Object> getPostRequirements(Long postId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("postId", postId);
        if (validation.isPresent()) {
            log.warn("getPostRequirements invalid input: {}", validation.get());
            return Map.of("available", false, "items", List.of(), "reason", validation.get());
        }

        log.info("Agent调用: getPostRequirements(postId={})", postId);
        try {
            // 修复：限制返回条数（防全量要求注入 prompt 撑爆上下文）
            LambdaQueryWrapper<PostAbilityModel> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PostAbilityModel::getPostId, postId)
                    .eq(PostAbilityModel::getIsDeleted, 0)
                    .orderByDesc(PostAbilityModel::getWeight)
                    .last("LIMIT 50");
            List<PostAbilityModel> requirements = postAbilityModelMapper.selectList(wrapper);

            List<Long> tagIds = requirements.stream()
                    .map(PostAbilityModel::getTagId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();

            Map<Long, AbilityTag> tagsById;
            if (tagIds.isEmpty()) {
                tagsById = Map.of();
            } else {
                tagsById = abilityTagMapper.selectBatchIds(tagIds).stream()
                        .collect(Collectors.toMap(AbilityTag::getId, Function.identity(), (a, b) -> a));
            }

            List<Map<String, Object>> items = requirements.stream().map(req -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", req.getId());
                map.put("tagId", req.getTagId());
                map.put("minRequiredLevel", req.getMinRequiredLevel());
                map.put("weight", req.getWeight());
                map.put("isRequired", req.getIsRequired());
                map.put("isCore", req.getIsCore());

                AbilityTag tag = tagsById.get(req.getTagId());
                // 岗位能力表自身名称是权威来源；标签库仅作为兼容回退。
                String abilityName = req.getAbilityName();
                if (abilityName == null || abilityName.isBlank()) {
                    abilityName = tag != null ? tag.getTagName() : null;
                }
                if (abilityName != null && !abilityName.isBlank()) {
                    map.put("abilityName", abilityName);
                }
                if (tag != null) {
                    map.put("tagCategory", tag.getTagCategory());
                }

                return map;
            }).filter(map -> map.get("abilityName") != null)
                    .collect(Collectors.toList());

            return Map.of("available", true, "items", items);
        } catch (Exception e) {
            log.error("getPostRequirements 查询失败: postId={}", postId, e);
            return Map.of("available", false, "items", List.of(), "reason", "post_requirements_unavailable");
        }
    }
}
