package com.example.matching.service.post;

import java.util.List;

/**
 * 岗位相似度服务：在既有岗位中检索与目标岗位最接近的一个。
 * <p>
 * 用途：趋势发现流程判断「材料中解析出的岗位」应当<b>新建</b>，还是作为某个既有岗位的<b>能力变更</b>。
 * <p>
 * 与现有向量检索的分工：{@code MilvusVectorService} 此前只覆盖「岗位↔人」两个方向，
 * 本服务补齐「岗位↔岗位」这一维度。
 */
public interface PostSimilarityService {

    /** 单个相似岗位命中 */
    record PostSimilarityMatch(Long postId, String postName, double similarity) {
    }

    /**
     * 相似度检索结果。
     * <p>
     * {@code available=false} 表示检索能力本身不可用（Milvus 不可达）。此时 {@code matches} 必然为空，
     * 但**调用方绝不可将其理解为「没有相似岗位」** —— 否则会把与既有岗位高度重合的岗位误判为新岗位。
     */
    record PostSimilarityResult(boolean available, List<PostSimilarityMatch> matches) {

        public static PostSimilarityResult unavailable() {
            return new PostSimilarityResult(false, List.of());
        }

        /** 最相似的那个岗位；无命中时返回 null。 */
        public PostSimilarityMatch best() {
            return matches == null || matches.isEmpty() ? null : matches.get(0);
        }
    }

    /**
     * 检索与给定能力集合最相似的既有岗位。
     *
     * @param abilityNames  目标岗位的能力名列表（岗位向量只索引能力名，见实现说明）
     * @param topK          最多返回条数
     * @param excludePostId 需要排除的岗位（如比对对象本身就是既有岗位），可为 null
     * @return 按相似度降序；检索不可用时返回 {@link PostSimilarityResult#unavailable()}，调用方必须据此降级
     */
    PostSimilarityResult findSimilarPosts(List<String> abilityNames, int topK, Long excludePostId);
}
