package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力标签归并服务
 * <p>
 * 查找相似标签并自动归并，保持标签池整洁。
 * 已改为手动触发（通过 AbilityTagGovernanceController 调用），不再定时执行。
 * 执行策略：
 * 1. 查找向量相似度超过阈值的标签对
 * 2. 自动归并（保留手动创建的标签，归并AI生成的标签）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagMergeScheduler {

    private final AbilityTagService abilityTagService;
    private final VectorEmbeddingService vectorEmbeddingService;

    /** 默认归并相似度阈值 */
    public static final double DEFAULT_MERGE_THRESHOLD = 0.9;

    /**
     * 执行标签归并
     *
     * @param threshold 相似度阈值（0~1）
     * @return 归并结果摘要
     */
    public Map<String, Object> executeMerge(double threshold) {
        log.info("开始执行标签归并任务 threshold={}", threshold);

        SecurityUtils.setSystemContext();
        try {
            List<AbilityTag> allTags = abilityTagService.list(
                    Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1)
            );

            List<AbilityTag> tagsWithVector = allTags.stream()
                    .filter(AbilityTagHierarchy::isAssessable)
                    .filter(tag -> tag.getEmbeddingVector() != null && !tag.getEmbeddingVector().isEmpty())
                    .toList();

            if (tagsWithVector.size() < 2) {
                log.info("标签数量不足（有向量标签: {}），跳过归并", tagsWithVector.size());
                return buildResult(0, 0, List.of(), tagsWithVector.size(), allTags.size());
            }

            List<MergePair> mergePairs = findSimilarTagPairs(tagsWithVector, threshold);

            List<Map<String, Object>> mergeDetails = new ArrayList<>();
            for (MergePair pair : mergePairs) {
                try {
                    Long keepTagId, mergeTagId;
                    if ("MANUAL".equals(pair.tag1.getSourceType())) {
                        keepTagId = pair.tag1.getId();
                        mergeTagId = pair.tag2.getId();
                    } else if ("MANUAL".equals(pair.tag2.getSourceType())) {
                        keepTagId = pair.tag2.getId();
                        mergeTagId = pair.tag1.getId();
                    } else {
                        keepTagId = Math.min(pair.tag1.getId(), pair.tag2.getId());
                        mergeTagId = Math.max(pair.tag1.getId(), pair.tag2.getId());
                    }

                    abilityTagService.mergeTags(mergeTagId, keepTagId);

                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("mergeTag", pair.tag1.getId().equals(mergeTagId) ? pair.tag1.getTagName() : pair.tag2.getTagName());
                    detail.put("keepTag", pair.tag1.getId().equals(keepTagId) ? pair.tag1.getTagName() : pair.tag2.getTagName());
                    detail.put("similarity", Math.round(pair.similarity * 10000) / 10000.0);
                    mergeDetails.add(detail);

                    log.debug("标签归并: {} -> {} (similarity={})",
                            pair.tag2.getTagName(), pair.tag1.getTagName(), pair.similarity);
                } catch (Exception e) {
                    log.warn("标签归并失败: {} -> {}, error={}",
                            pair.tag2.getTagName(), pair.tag1.getTagName(), e.getMessage());
                }
            }

            log.info("标签归并任务完成: 共找到 {} 对相似标签，成功归并 {} 对", mergePairs.size(), mergeDetails.size());
            return buildResult(mergePairs.size(), mergeDetails.size(), mergeDetails, tagsWithVector.size(), allTags.size());
        } finally {
            SecurityUtils.clear();
        }
    }

    private Map<String, Object> buildResult(int foundPairs, int mergedCount,
                                             List<Map<String, Object>> details,
                                             int tagsWithVector, int totalTags) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foundPairs", foundPairs);
        result.put("mergedCount", mergedCount);
        result.put("totalTags", totalTags);
        result.put("tagsWithVector", tagsWithVector);
        result.put("details", details);
        return result;
    }

    /**
     * 查找相似标签对
     * <p>
     * 优化策略：先将 List&lt;Float&gt; 转为 float[] 避免自动装箱开销，
     * 使用纯数值余弦相似度计算避免对象分配。
     */
    private List<MergePair> findSimilarTagPairs(List<AbilityTag> tags, double threshold) {
        // 1. 预转换向量为 float 数组，避免比较循环中的自动装箱开销
        List<float[]> vectors = new ArrayList<>(tags.size());
        for (AbilityTag tag : tags) {
            vectors.add(toFloatArray(tag.getEmbeddingVector()));
        }

        // 2. 两两比较（纯数值计算，无对象分配）
        List<MergePair> pairs = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            float[] vecI = vectors.get(i);
            if (vecI == null) continue;

            for (int j = i + 1; j < tags.size(); j++) {
                float[] vecJ = vectors.get(j);
                if (vecJ == null) continue;

                float similarity = cosineSimilarityFloat(vecI, vecJ);
                if (tags.get(i).getParentId() != null
                        && tags.get(i).getParentId().equals(tags.get(j).getParentId())
                        && similarity >= threshold) {
                    pairs.add(new MergePair(tags.get(i), tags.get(j), similarity));
                }
            }
        }

        // 3. 按相似度降序排序
        pairs.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 4. 去重：确保同一个标签不会被多次归并
        Map<Long, Boolean> processed = new HashMap<>();
        List<MergePair> uniquePairs = new ArrayList<>();
        for (MergePair pair : pairs) {
            if (!processed.containsKey(pair.tag1.getId()) && !processed.containsKey(pair.tag2.getId())) {
                uniquePairs.add(pair);
                processed.put(pair.tag1.getId(), true);
                processed.put(pair.tag2.getId(), true);
            }
        }

        return uniquePairs;
    }

    /**
     * List&lt;Float&gt; 转 float[]（避免自动装箱开销）
     */
    private float[] toFloatArray(List<Float> list) {
        if (list == null || list.isEmpty()) return null;
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Float val = list.get(i);
            arr[i] = val != null ? val : 0f;
        }
        return arr;
    }

    /**
     * 计算两个 float 数组的余弦相似度（纯数值计算，无自动装箱）
     */
    private float cosineSimilarityFloat(float[] a, float[] b) {
        if (a.length != b.length) return 0f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denominator = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denominator == 0f ? 0f : dot / denominator;
    }

    /**
     * 标签对记录
     */
    private record MergePair(AbilityTag tag1, AbilityTag tag2, double similarity) {}
}
