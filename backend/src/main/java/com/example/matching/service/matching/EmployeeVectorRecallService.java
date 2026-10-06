package com.example.matching.service.matching;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.vector.MilvusVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vector recall service for existing employee transfer scenarios.
 * <p>
 * It returns employeeId -> semantic similarity score and degrades to empty
 * results when Milvus is unavailable.
 */
@Slf4j
@Service
public class EmployeeVectorRecallService {

    private static final int DEFAULT_TOP_K = 100;

    private final ObjectProvider<MilvusVectorService> milvusVectorServiceProvider;
    private final MatchingProfileTextBuilder matchingProfileTextBuilder;
    private final VectorRecallCacheEpoch vectorRecallCacheEpoch;

    public EmployeeVectorRecallService(ObjectProvider<MilvusVectorService> milvusVectorServiceProvider,
                                       MatchingProfileTextBuilder matchingProfileTextBuilder,
                                       VectorRecallCacheEpoch vectorRecallCacheEpoch) {
        this.milvusVectorServiceProvider = milvusVectorServiceProvider;
        this.matchingProfileTextBuilder = matchingProfileTextBuilder;
        this.vectorRecallCacheEpoch = vectorRecallCacheEpoch;
    }

    public Map<Long, BigDecimal> recallEmployeesForPost(MatchingPostProfile post) {
        return recallEmployeesForPost(post, DEFAULT_TOP_K);
    }

    /**
     * 缓存 key 包含 epoch：相关变更递增 epoch 后，旧 key 自然过期，
     * 不再需要一次变更清空所有岗位的召回结果。
     */
    @Cacheable(cacheNames = RedisCacheNames.VECTOR_RECALL,
            key = "#post.postId() + ':' + #topK + ':e' + @vectorRecallCacheEpoch.current()", sync = true)
    public Map<Long, BigDecimal> recallEmployeesForPost(MatchingPostProfile post, int topK) {
        MilvusVectorService milvusVectorService = milvusVectorServiceProvider.getIfAvailable();
        if (milvusVectorService == null || post == null) {
            log.warn("向量召回跳过：milvusVectorService={}, post={}", milvusVectorService == null ? "null" : "ok", post == null ? "null" : "ok");
            return Map.of();
        }

        String postText = buildPostRecallText(post);
        if (postText.isBlank()) {
            log.warn("向量召回跳过：postText 为空。postId={}", post.postId());
            return Map.of();
        }

        try {
            log.info("向量召回开始：postId={}, postText={}", post.postId(), postText);
            List<Map<String, Object>> searchResults = milvusVectorService.searchEmployeesForPost(postText, topK);
            log.info("向量召回搜索返回 {} 条结果：postId={}", searchResults.size(), post.postId());

            Map<Long, BigDecimal> scores = new LinkedHashMap<>();
            for (Map<String, Object> item : searchResults) {
                Long empId = toLong(item.get("refId"));
                BigDecimal score = toScore(item.get("score"));
                log.debug("向量召回结果解析：empId={}, score={}, rawItem={}", empId, score, item);
                if (empId != null && score != null) {
                    scores.put(empId, score);
                }
            }
            log.info("向量召回完成：postId={}, 有效分数 {} 条", post.postId(), scores.size());
            return scores;
        } catch (Exception e) {
            log.warn("Employee vector recall failed, fallback to original matching scope. postId={}, reason={}",
                    post.postId(), e.getMessage());
            return Map.of();
        }
    }

    private String buildPostRecallText(MatchingPostProfile post) {
        Map<Long, String> tagNameMap = new LinkedHashMap<>();
        List<MatchingRequirementSnapshot> requirements = post.requirements();
        if (requirements != null) {
            for (MatchingRequirementSnapshot req : requirements) {
                if (req.tagId() != null) {
                    tagNameMap.putIfAbsent(req.tagId(),
                            req.abilityName() != null ? req.abilityName() : "ability" + req.tagId());
                }
            }
        }
        return matchingProfileTextBuilder.buildFormalPostAbilityRecallText(post, tagNameMap);
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toScore(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
