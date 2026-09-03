package com.example.matching.application.post;

import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.service.post.PostPanoramaApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostPanoramaApiFacade {

    private final PostPanoramaApplicationService panoramaService;

    public Map<String, Object> getOverview(String level, String techStack, String keyword) {
        List<PostDTO> posts = panoramaService.queryPosts(level, keyword, 200);
        Set<Long> postIds = posts.stream().map(PostDTO::id).collect(Collectors.toSet());
        List<PostAbilityDTO> models = panoramaService.queryModels(postIds, techStack);

        Map<Long, Long> abilityCountByPost = models.stream()
                .collect(Collectors.groupingBy(PostAbilityDTO::postId, Collectors.counting()));

        List<Map<String, Object>> postList = posts.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("postName", p.postName());
            m.put("postCode", p.postCode());
            m.put("level", p.postLevel());
            m.put("abilityCount", abilityCountByPost.getOrDefault(p.id(), 0L).intValue());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> abilityList = models.stream().map(am -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", am.id());
            m.put("postId", am.postId());
            m.put("modelId", am.id());
            m.put("tagName", abilityName(am, null));
            m.put("abilityName", abilityName(am, null));
            m.put("category", am.techStack());
            m.put("requiredLevel", am.minRequiredLevel());
            m.put("weight", am.weight());
            m.put("isCore", am.isCore() != null && am.isCore() == 1);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> skillPointList = models.stream().map(am -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", am.id());
            m.put("tagName", abilityName(am, null));
            m.put("modelId", am.id());
            m.put("postId", am.postId());
            m.put("techStack", am.techStack());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = models.stream().map(am -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("source", am.postId());
            e.put("target", am.id());
            e.put("type", "REQUIRES");
            e.put("requiredLevel", am.minRequiredLevel());
            e.put("weight", am.weight());
            return e;
        }).collect(Collectors.toList());

        Map<String, Object> stats = Map.of(
                "postCount", posts.size(),
                "abilityCount", models.size(),
                "skillPointCount", skillPointList.size()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("posts", postList);
        result.put("abilities", abilityList);
        result.put("skillPoints", skillPointList);
        result.put("edges", edges);
        result.put("stats", stats);
        return result;
    }

    public Map<String, Object> getFilters() {
        List<PostDTO> posts = panoramaService.queryPosts(null, null, 500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("levels", posts.stream()
                .map(PostDTO::postLevel).filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList()));
        result.put("techStacks", panoramaService.queryDistinctTechStacks());
        result.put("abilityCategories", List.of());
        return result;
    }

    public Map<String, Object> getPostDetail(Long postId) {
        PostDTO post = panoramaService.getPostById(postId);
        if (post == null) return null;

        List<PostAbilityDTO> models = panoramaService.queryModels(Set.of(postId), null);
        List<Map<String, Object>> abilities = models.stream().map(am -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modelId", am.id());
            m.put("abilityTagId", am.tagId());
            m.put("abilityName", abilityName(am, null));
            m.put("requiredLevel", am.minRequiredLevel());
            m.put("weight", am.weight());
            m.put("isCore", am.isCore() != null && am.isCore() == 1);
            m.put("category", am.techStack());
            m.put("skillPoints", List.of(abilityName(am, null)));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postId", postId);
        result.put("postName", post.postName());
        result.put("abilities", abilities);
        return result;
    }

    public Map<String, Object> getAbilityDetail(Long abilityId) {
        Map<Long, TagDTO> tagMap = panoramaService.queryTagMap(Set.of(abilityId));
        TagDTO tag = tagMap.get(abilityId);
        if (tag == null) return null;

        List<TagDTO> childTags = panoramaService.queryChildTags(Set.of(abilityId));
        List<PostAbilityDTO> models = panoramaService.queryModelsByTagId(abilityId);

        Set<Long> postIds = models.stream().map(PostAbilityDTO::postId).collect(Collectors.toSet());
        Map<Long, PostDTO> postMap = panoramaService.queryPostMap(postIds);

        List<Map<String, Object>> posts = models.stream().map(am -> {
            PostDTO p = postMap.get(am.postId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("postId", am.postId());
            m.put("postName", p != null ? p.postName() : "未知");
            m.put("requiredLevel", am.minRequiredLevel());
            m.put("weight", am.weight());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("abilityId", abilityId);
        result.put("abilityName", tag.tagName());
        result.put("category", tag.tagCategory());
        result.put("tagLevel", tag.tagLevel());
        result.put("skillPoints", childTags.stream().map(TagDTO::tagName).collect(Collectors.toList()));
        result.put("postCount", posts.size());
        result.put("posts", posts);
        return result;
    }

    public Map<String, Object> getGraph(Long postId, String level, String techStack, String keyword, Integer limit) {
        return getGraph(postId, level, techStack, keyword, limit, null, false);
    }

    public Map<String, Object> getGraph(Long postId, String level, String techStack, String keyword, Integer limit,
                                        Integer requiredLevel, Boolean coreOnly) {
        int effectiveLimit = limit != null ? limit : 200;
        List<PostDTO> posts = panoramaService.queryPosts(level, keyword, effectiveLimit);
        if (postId != null) {
            PostDTO specific = panoramaService.getPostById(postId);
            posts = specific != null ? List.of(specific) : List.of();
        }

        Set<Long> postIds = posts.stream().map(PostDTO::id).collect(Collectors.toSet());
        List<PostAbilityDTO> models = panoramaService.queryModels(postIds, techStack).stream()
                .filter(model -> requiredLevel == null || Objects.equals(model.minRequiredLevel(), requiredLevel))
                .filter(model -> !Boolean.TRUE.equals(coreOnly) || Integer.valueOf(1).equals(model.isCore()))
                .toList();

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> addedNodeKeys = new HashSet<>();

        for (PostDTO p : posts) {
            String key = "POST:" + p.id();
            if (addedNodeKeys.add(key)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", key);
                node.put("type", "post");
                node.put("label", p.postName());
                node.put("category", p.postLevel());
                node.put("level", normalizePostLevel(p.postLevel()));
                node.put("status", "ACTIVE");
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("postId", p.id());
                meta.put("postCode", p.postCode());
                meta.put("level", p.postLevel());
                node.put("meta", meta);
                nodes.add(node);
            }
        }

        Set<String> abilityKeys = new LinkedHashSet<>();
        for (PostAbilityDTO am : models) {
            if (!hasText(abilityName(am, null))) continue;
            String abilityKey = skillPointNodeKey(am);
            if (abilityKeys.add(abilityKey) && addedNodeKeys.add(abilityKey)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", abilityKey);
                node.put("type", "skillPoint");
                node.put("label", abilityName(am, null));
                node.put("category", hasText(am.techStack()) ? am.techStack() : "通用工程能力");
                node.put("level", am.minRequiredLevel());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("modelId", am.id());
                meta.put("postId", am.postId());
                meta.put("abilityName", abilityName(am, null));
                meta.put("requiredLevel", am.minRequiredLevel());
                node.put("meta", meta);
                nodes.add(node);
            }
        }

        for (String stack : models.stream()
                .map(am -> hasText(am.techStack()) ? am.techStack() : "通用工程能力")
                .collect(Collectors.toCollection(LinkedHashSet::new))) {
            String key = "TECH_STACK:" + stack;
            if (addedNodeKeys.add(key)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", key);
                node.put("type", "techStack");
                node.put("label", stack);
                node.put("category", stack);
                nodes.add(node);
            }
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> addedEdgeKeys = new HashSet<>();

        // 岗位视图：岗位 -> 技术栈 -> 技能点，技术栈名称必须作为岗位能力结构的一层展示。
        for (PostAbilityDTO am : models) {
            String stack = hasText(am.techStack()) ? am.techStack() : "通用工程能力";
            String edgeKey = "POST:" + am.postId() + "-TECH_STACK->TECH_STACK:" + stack;
            if (addedEdgeKeys.add(edgeKey)) {
                edges.add(Map.of(
                        "id", edgeKey,
                        "source", "POST:" + am.postId(),
                        "target", "TECH_STACK:" + stack,
                        "type", "POST_TECH_STACK",
                        "label", "岗位技术栈",
                        "weight", 0.9));
            }
        }

        // Explicit hierarchy edge: technology stack -> post.
        // The post node remains the business subject; the stack is only a grouping dimension.
        for (PostAbilityDTO am : models) {
            String stack = hasText(am.techStack()) ? am.techStack() : "通用工程能力";
            String edgeKey = "TECH_STACK:" + stack + "-POST->POST:" + am.postId();
            if (addedEdgeKeys.add(edgeKey)) {
                edges.add(Map.of(
                        "id", edgeKey,
                        "source", "TECH_STACK:" + stack,
                        "target", "POST:" + am.postId(),
                        "type", "TECH_STACK_POST",
                        "label", "技术栈岗位",
                        "weight", 0.9));
            }
        }

        for (PostAbilityDTO am : models) {
            String abilityKey = skillPointNodeKey(am);
            String edgeKey = "POST:" + am.postId() + "-REQ->" + abilityKey;
            if (addedEdgeKeys.add(edgeKey)) {
                boolean isCore = am.isCore() != null && am.isCore() == 1;
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", edgeKey);
                edge.put("source", "POST:" + am.postId());
                edge.put("target", abilityKey);
                edge.put("type", "REQUIRES");
                edge.put("label", isCore ? "核心要求" : "要求");
                edge.put("weight", am.weight() != null ? am.weight().doubleValue() / 100.0 : 0.5);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("dash", !isCore);
                edge.put("metadata", meta);
                edges.add(edge);
            }
        }

        for (PostAbilityDTO am : models) {
            String stack = hasText(am.techStack()) ? am.techStack() : "通用工程能力";
            String edgeKey = "TECH_STACK:" + stack + "-HAS_SKILL->" + skillPointNodeKey(am);
            if (addedEdgeKeys.add(edgeKey)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", edgeKey);
                edge.put("source", "TECH_STACK:" + stack);
                edge.put("target", skillPointNodeKey(am));
                edge.put("type", "TECH_STACK_SKILL");
                edge.put("label", "技术栈技能");
                edges.add(edge);
            }
        }

        Map<String, Object> stats = Map.of(
                "nodeCount", nodes.size(),
                "edgeCount", edges.size(),
                "postCount", posts.size(),
                "abilityCount", abilityKeys.size(),
                "skillPointCount", models.stream().map(PostPanoramaApiFacade::skillPointNodeKey).distinct().count()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("stats", stats);
        return result;
    }

    private static int normalizePostLevel(String level) {
        if (level == null || level.isBlank()) return 3;
        String value = level.trim().toUpperCase(Locale.ROOT);
        if (value.matches("L[1-5]")) return Integer.parseInt(value.substring(1));
        if (value.matches("[1-5]")) return Integer.parseInt(value);
        if (value.contains("初") || value.contains("助理") || value.contains("实习")) return 1;
        if (value.contains("中级") || value.contains("中级")) return 3;
        if (value.contains("高级") || value.contains("专家") || value.contains("资深")) return 4;
        if (value.contains("首席") || value.contains("架构师") || value.contains("总监")) return 5;
        return 3;
    }

    /**
     * Fact-oriented graph. Every node represents one post_ability_model row,
     * including unnormalised abilities. Taxonomy is an optional relation only.
     */
    public Map<String, Object> getAbilityFactGraph(Long postId, String level, String keyword, Integer limit) {
        int effectiveLimit = limit != null ? limit : 200;
        List<PostDTO> posts = panoramaService.queryPosts(level, keyword, effectiveLimit);
        if (postId != null) {
            PostDTO specific = panoramaService.getPostById(postId);
            posts = specific != null ? List.of(specific) : List.of();
        }
        Set<Long> postIds = posts.stream().map(PostDTO::id).collect(Collectors.toSet());
        List<PostAbilityDTO> facts = panoramaService.queryModels(postIds, null);
        Map<Long, TagDTO> tags = panoramaService.queryTagMap(tagIds(facts));
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (PostDTO post : posts) {
            String id = "POST:" + post.id();
            ids.add(id);
            nodes.add(Map.of("id", id, "type", "post", "label", post.postName()));
        }
        for (PostAbilityDTO fact : facts) {
            String factId = "POST_ABILITY_FACT:" + fact.id();
            TagDTO tag = tagById(tags, fact.tagId());
            String factName = abilityName(fact, tag);
            if (!hasText(factName)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("modelId", fact.id());
            metadata.put("postId", fact.postId());
            metadata.put("abilityName", factName);
            metadata.put("normalized", fact.tagId() != null);
            metadata.put("tagId", fact.tagId());
            metadata.put("requiredLevel", fact.minRequiredLevel());
            metadata.put("isCore", fact.isCore() != null && fact.isCore() == 1);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", factId);
            node.put("type", fact.tagId() == null ? "unnormalizedPostAbilityFact" : "postAbilityFact");
            node.put("label", factName);
            node.put("metadata", metadata);
            nodes.add(node);
            edges.add(Map.of("id", "POST:" + fact.postId() + "-REQUIRES->" + factId,
                    "source", "POST:" + fact.postId(), "target", factId, "type", "REQUIRES"));
            if (tag != null) {
                String tagId = "ABILITY_TAG:" + tag.id();
                if (ids.add(tagId)) nodes.add(Map.of("id", tagId, "type", "taxonomyTag", "label", tag.tagName()));
                edges.add(Map.of("id", factId + "-NORMALIZED_TO->" + tagId,
                        "source", factId, "target", tagId, "type", "NORMALIZED_TO"));
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("postCount", posts.size());
        stats.put("factCount", facts.size());
        stats.put("unnormalizedFactCount", facts.stream().filter(f -> f.tagId() == null).count());
        return Map.of("available", true, "nodes", nodes, "edges", edges, "stats", stats);
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    private static Set<Long> tagIds(List<PostAbilityDTO> models) {
        return models.stream().map(PostAbilityDTO::tagId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static String abilityNodeKey(PostAbilityDTO ability) {
        return ability.tagId() != null ? "ABILITY_TAG:" + ability.tagId() : "ABILITY_MODEL:" + ability.id();
    }

    private static String skillPointNodeKey(PostAbilityDTO ability) {
        // The panorama is sourced from post_ability_model: one row is one skill point.
        // skillPointKey is only an internal fallback when historical rows have no abilityName.
        String key = hasText(ability.abilityName()) ? ability.abilityName() : ability.skillPointKey();
        String stack = hasText(ability.techStack()) ? ability.techStack() : "通用工程能力";
        return "SKILL_POINT:" + stack + ":" + key;
    }

    private static String abilityName(PostAbilityDTO ability, TagDTO tag) {
        if (hasText(ability.abilityName())) return ability.abilityName();
        if (tag != null && hasText(tag.tagName())) return tag.tagName();
        return null;
    }

    private static TagDTO tagById(Map<Long, TagDTO> tagMap, Long tagId) {
        return tagId == null ? null : tagMap.get(tagId);
    }
}
