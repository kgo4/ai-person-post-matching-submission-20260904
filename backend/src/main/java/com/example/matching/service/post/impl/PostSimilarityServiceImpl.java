package com.example.matching.service.post.impl;

import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.post.PostSimilarityService;
import com.example.matching.vector.MilvusVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 岗位相似度服务实现（基于 Milvus 统一向量集合的 POST 类型）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSimilarityServiceImpl implements PostSimilarityService {

    /** 默认召回条数；即使调用方只要 1 条也多取一些，便于过滤已删除岗位后仍有结果。 */
    private static final int MIN_FETCH_SIZE = 5;

    private final MilvusVectorService milvusVectorService;
    private final PostQueryPort postQueryPort;

    @Override
    public PostSimilarityResult findSimilarPosts(List<String> abilityNames, int topK, Long excludePostId) {
        if (abilityNames == null || abilityNames.isEmpty()) {
            // 无能力名就无法比对。这不是「没有相似岗位」，交由调用方按证据不足处理。
            log.debug("岗位相似度检索跳过：能力名为空");
            return new PostSimilarityResult(false, List.of());
        }
        if (!milvusVectorService.isVectorSearchAvailable()) {
            log.warn("向量检索不可用，岗位相似度结果不可信：abilityCount={}", abilityNames.size());
            return PostSimilarityResult.unavailable();
        }

        int limit = topK > 0 ? topK : 1;
        int fetchSize = Math.max(limit, MIN_FETCH_SIZE) + (excludePostId == null ? 0 : 1);
        List<MilvusVectorService.VectorMatch> hits =
                milvusVectorService.searchSimilarPostsByAbilities(abilityNames, fetchSize);
        if (hits.isEmpty()) {
            // 检索通道可用但没有命中：这是真实的「无相似岗位」
            return new PostSimilarityResult(true, List.of());
        }

        List<Long> candidateIds = hits.stream()
                .map(MilvusVectorService.VectorMatch::refId)
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(excludePostId))
                .distinct()
                .toList();
        if (candidateIds.isEmpty()) {
            return new PostSimilarityResult(true, List.of());
        }

        // 向量集合可能存在已删除岗位的残留记录，必须回查主表确认岗位仍然存在
        Map<Long, String> postNames = new HashMap<>();
        for (PostQueryPort.PostDTO post : postQueryPort.batchGetPosts(candidateIds)) {
            if (post != null && post.id() != null) {
                postNames.put(post.id(), post.postName());
            }
        }

        Map<Long, PostSimilarityMatch> matches = new LinkedHashMap<>();
        for (MilvusVectorService.VectorMatch hit : hits) {
            Long postId = hit.refId();
            if (postId == null || postId.equals(excludePostId) || !postNames.containsKey(postId)) {
                continue;
            }
            if (StringUtils.hasText(postNames.get(postId))) {
                matches.putIfAbsent(postId, new PostSimilarityMatch(postId, postNames.get(postId), hit.similarity()));
            }
            if (matches.size() >= limit) {
                break;
            }
        }
        return new PostSimilarityResult(true, List.copyOf(matches.values()));
    }
}
