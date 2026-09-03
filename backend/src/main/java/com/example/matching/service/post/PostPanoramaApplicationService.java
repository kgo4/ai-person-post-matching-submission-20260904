package com.example.matching.service.post;

import com.example.matching.entity.post.PostPost;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.service.post.PostPostWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位全景图谱应用服务 — Controller 仅通过此服务和 Port DTO 访问数据。
 */
@Service
@RequiredArgsConstructor
public class PostPanoramaApplicationService {

    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final PostPostWriteService postPostWriteService;

    public List<PostDTO> queryPosts(String level, String keyword, int limit) {
        var all = postQueryPort.listActivePosts(0);
        return all.stream()
                .filter(p -> level == null || level.equals(p.postLevel()))
                .filter(p -> keyword == null || (p.postName() != null && p.postName().contains(keyword)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<PostAbilityDTO> queryModels(Set<Long> postIds, String techStack) {
        if (postIds.isEmpty()) return List.of();
        List<PostAbilityDTO> all = postQueryPort.listRequirementsByPostIds(postIds);
        if (techStack == null || techStack.isBlank()) return all;
        return all.stream().filter(m -> techStack.equals(m.techStack())).collect(Collectors.toList());
    }

    public Map<Long, TagDTO> queryTagMap(Set<Long> tagIds) {
        if (tagIds.isEmpty()) return Map.of();
        return tagQueryPort.batchGetTags(new ArrayList<>(tagIds)).stream()
                .collect(Collectors.toMap(TagDTO::id, t -> t, (a, b) -> a));
    }

    public List<TagDTO> queryChildTags(Set<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        List<TagDTO> result = new ArrayList<>();
        for (Long parentId : parentIds) {
            result.addAll(tagQueryPort.listChildren(parentId));
        }
        return result;
    }

    /** Resolve the taxonomy descendants used by the panorama: L2 ability -> mapped terms. */
    public List<TagDTO> queryParentDomains(Set<Long> tagIds) {
        if (tagIds.isEmpty()) return List.of();
        Set<Long> l1Ids = tagQueryPort.batchGetTags(new ArrayList<>(tagIds)).stream()
                .filter(t -> t.tagLevel() != null && t.tagLevel() == 2 && t.parentId() != null)
                .map(TagDTO::parentId).collect(Collectors.toSet());
        if (l1Ids.isEmpty()) return List.of();
        return tagQueryPort.batchGetTags(new ArrayList<>(l1Ids)).stream()
                .filter(t -> t.tagLevel() != null && t.tagLevel() == 1)
                .collect(Collectors.toList());
    }

    public PostDTO getPostById(Long postId) {
        return postQueryPort.getPostById(postId);
    }

    public List<PostAbilityDTO> queryModelsByTagId(Long tagId) {
        return postQueryPort.listActivePostAbilityModels(0).stream()
                .filter(m -> tagId.equals(m.tagId()))
                .collect(Collectors.toList());
    }

    public Map<Long, PostDTO> queryPostMap(Set<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postQueryPort.batchGetPosts(new ArrayList<>(postIds)).stream()
                .collect(Collectors.toMap(PostDTO::id, p -> p, (a, b) -> a));
    }

    public List<String> queryDistinctTagCategories() {
        return tagQueryPort.listActiveTags(0).stream()
                .map(TagDTO::tagCategory)
                .filter(Objects::nonNull).distinct().sorted()
                .collect(Collectors.toList());
    }

    public List<String> queryDistinctTechStacks() {
        return postQueryPort.listActivePostAbilityModels(0).stream()
                .map(PostAbilityDTO::techStack)
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList();
    }

    public void insertPost(PostPost post) {
        postPostWriteService.save(post);
    }
}
