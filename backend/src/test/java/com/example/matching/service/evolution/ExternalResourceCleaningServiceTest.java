package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.service.evolution.impl.ExternalResourceCleaningServiceImpl;
import com.example.matching.service.post.impl.PostCleaningRulesEngine;
import com.example.matching.service.rag.KnowledgeDocumentDeduplicator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalResourceCleaningServiceTest {
    @Test
    void removesEmptyNoiseAndDuplicateResources() {
        PostCleaningRulesEngine cleaner = mock(PostCleaningRulesEngine.class);
        when(cleaner.cleanText(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        ExternalResourceCleaningService service = new ExternalResourceCleaningServiceImpl(
                new KnowledgeDocumentDeduplicator(), cleaner);
        var useful = new ExternalTrendResourceDTO("趋势标题", "ARTICLE", "1", "这是一个足够长的趋势解释摘要内容，用来描述岗位演化背景。", "https://www.zhihu.com/question/1?utm_source=x", 2, 10, "ZHIHU_TREND", false, false);
        var duplicate = new ExternalTrendResourceDTO("趋势标题", "ARTICLE", "1", "这是一个足够长的趋势解释摘要内容，用来描述岗位演化背景。", "https://www.zhihu.com/question/1", 1, 1, "ZHIHU_TREND", false, false);
        var noise = new ExternalTrendResourceDTO("推广", "ARTICLE", "2", "加微信扫码领取资料，这是广告导流内容。", "https://www.zhihu.com/question/2", 0, 0, "ZHIHU_TREND", false, false);
        var result = service.clean(List.of(useful, duplicate, noise));

        assertThat(result.items()).hasSize(1);
        assertThat(result.deduplicatedCount()).isEqualTo(1);
        assertThat(result.filteredCount() + result.noiseRemovedCount()).isEqualTo(1);
        assertThat(result.items().get(0).url()).isEqualTo("https://www.zhihu.com/question/1");
    }

    @Test
    void returnsTheNoiseRemovedTextThatWillBeUsedByTheAgent() {
        PostCleaningRulesEngine cleaner = mock(PostCleaningRulesEngine.class);
        when(cleaner.cleanText("趋势标题")).thenReturn("清洗后标题");
        when(cleaner.cleanText("原始正文，联系方式：13800000000，保留技术趋势内容。"))
                .thenReturn("保留技术趋势内容。");
        ExternalResourceCleaningService service = new ExternalResourceCleaningServiceImpl(
                new KnowledgeDocumentDeduplicator(), cleaner);

        var result = service.clean(List.of(new ExternalTrendResourceDTO(
                "趋势标题", "ARTICLE", "1", "原始正文，联系方式：13800000000，保留技术趋势内容。",
                "https://www.zhihu.com/question/1", 0, 0, "ZHIHU_TREND", false, false)));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("清洗后标题");
            assertThat(item.summary()).isEqualTo("保留技术趋势内容。");
            assertThat(item.summary()).doesNotContain("13800000000");
        });
    }
}
