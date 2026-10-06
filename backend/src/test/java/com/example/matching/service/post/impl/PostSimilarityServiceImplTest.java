package com.example.matching.service.post.impl;

import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.post.PostSimilarityService.PostSimilarityResult;
import com.example.matching.vector.MilvusVectorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位相似度服务单测。
 * <p>
 * 核心回归点：**「检索不可用」与「没有相似岗位」必须可区分**。
 * 趋势发现据此决定候选是新建岗位还是既有岗位的能力变更；若把不可用当成「无相似」，
 * 与既有岗位高度重合的岗位会被误判为全新岗位并重复创建。
 */
class PostSimilarityServiceImplTest {

    private final MilvusVectorService milvusVectorService = mock(MilvusVectorService.class);
    private final PostQueryPort postQueryPort = mock(PostQueryPort.class);
    private final PostSimilarityServiceImpl service =
            new PostSimilarityServiceImpl(milvusVectorService, postQueryPort);

    /** Milvus 不可用时必须标记 available=false，调用方不得据此判定「无相似岗位」。 */
    @Test
    void vectorUnavailableIsReportedAsUnavailableNotAsNoSimilarPost() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(false);

        PostSimilarityResult result = service.findSimilarPosts(List.of("内容安全"), 3, null);

        assertThat(result.available()).isFalse();
        assertThat(result.matches()).isEmpty();
        assertThat(result.best()).isNull();
        verify(milvusVectorService, never()).searchSimilarPostsByAbilities(any(), anyInt());
    }

    /** 能力名为空同样无法比对，属于证据不足而非「无相似岗位」。 */
    @Test
    void emptyAbilityNamesAreNotTreatedAsNoSimilarPost() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(true);

        PostSimilarityResult result = service.findSimilarPosts(List.of(), 3, null);

        assertThat(result.available()).isFalse();
        verify(milvusVectorService, never()).searchSimilarPostsByAbilities(any(), anyInt());
    }

    /** 检索通道可用但没有命中 —— 这是真实的「无相似岗位」。 */
    @Test
    void noHitsMeansGenuinelyNoSimilarPost() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(true);
        when(milvusVectorService.searchSimilarPostsByAbilities(any(), anyInt())).thenReturn(List.of());

        PostSimilarityResult result = service.findSimilarPosts(List.of("内容安全"), 3, null);

        assertThat(result.available()).isTrue();
        assertThat(result.matches()).isEmpty();
        assertThat(result.best()).isNull();
    }

    /** 命中按相似度降序返回，并补上岗位名称；同时只保留前 topK 条。 */
    @Test
    void matchesAreOrderedBySimilarityAndEnrichedWithPostNames() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(true);
        when(milvusVectorService.searchSimilarPostsByAbilities(any(), anyInt())).thenReturn(List.of(
                new MilvusVectorService.VectorMatch(11L, 0.91D),
                new MilvusVectorService.VectorMatch(22L, 0.64D),
                new MilvusVectorService.VectorMatch(33L, 0.20D)));
        stubPostNames(11L, "AIGC 内容审核师", 22L, "算法工程师", 33L, "数据分析师");

        PostSimilarityResult result = service.findSimilarPosts(List.of("内容安全", "模型评测"), 2, null);

        assertThat(result.available()).isTrue();
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).postId()).isEqualTo(11L);
        assertThat(result.matches().get(0).postName()).isEqualTo("AIGC 内容审核师");
        assertThat(result.matches().get(0).similarity()).isEqualTo(0.91D);
        assertThat(result.matches().get(1).postId()).isEqualTo(22L);
        assertThat(result.best().postId()).isEqualTo(11L);
    }

    /** 排除自身：比对对象本身是既有岗位时不应把自己算成最相似。 */
    @Test
    void excludedPostIsFilteredOut() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(true);
        when(milvusVectorService.searchSimilarPostsByAbilities(any(), anyInt())).thenReturn(List.of(
                new MilvusVectorService.VectorMatch(11L, 0.99D),
                new MilvusVectorService.VectorMatch(22L, 0.64D)));
        stubPostNames(11L, "自身岗位", 22L, "算法工程师");

        PostSimilarityResult result = service.findSimilarPosts(List.of("内容安全"), 3, 11L);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).postId()).isEqualTo(22L);
    }

    /** 向量集合可能残留已删除岗位的记录，必须回查主表过滤，不能凭向量命中就认定岗位存在。 */
    @Test
    void staleVectorWithoutLivePostIsDropped() {
        when(milvusVectorService.isVectorSearchAvailable()).thenReturn(true);
        when(milvusVectorService.searchSimilarPostsByAbilities(any(), anyInt())).thenReturn(List.of(
                new MilvusVectorService.VectorMatch(11L, 0.95D)));
        when(postQueryPort.batchGetPosts(any())).thenReturn(List.of());

        PostSimilarityResult result = service.findSimilarPosts(List.of("内容安全"), 3, null);

        assertThat(result.available()).isTrue();
        assertThat(result.matches()).isEmpty();
    }

    private void stubPostNames(Object... idNamePairs) {
        List<PostQueryPort.PostDTO> posts = new java.util.ArrayList<>();
        for (int i = 0; i < idNamePairs.length; i += 2) {
            Long id = (Long) idNamePairs[i];
            String name = (String) idNamePairs[i + 1];
            posts.add(new PostQueryPort.PostDTO(id, name, "POST_" + id, null, null, 1, null));
        }
        when(postQueryPort.batchGetPosts(any())).thenReturn(posts);
    }
}
