package com.example.matching.application.evolution.impl;

import com.example.matching.application.evolution.PostEvolutionExternalResourceService;
import com.example.matching.dto.evolution.ExternalTrendResourceDTO;
import com.example.matching.integration.zhihu.ZhihuApiProperties;
import com.example.matching.integration.zhihu.ZhihuSearchClient;
import com.example.matching.integration.zhihu.ZhihuSearchItem;
import com.example.matching.integration.zhihu.ZhihuSearchResponse;
import com.example.matching.service.evolution.ExternalResourceCleaningService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PostEvolutionExternalResourceServiceImplTest {
    @Test
    void ignoresNullItemsAndCachesSuccessfulResult() {
        ZhihuSearchClient client = mock(ZhihuSearchClient.class);
        ExternalResourceCleaningService cleaner = mock(ExternalResourceCleaningService.class);
        ZhihuApiProperties properties = new ZhihuApiProperties();
        properties.setCacheTtlSeconds(60);
        var item = new ZhihuSearchItem("标题", "ARTICLE", "1", "摘要", "https://www.zhihu.com/question/1", 1, 2);
        when(client.search("AI", 8)).thenReturn(new ZhihuSearchResponse(false, "hash", Arrays.asList(item, null), null));
        when(cleaner.clean(anyList())).thenAnswer(invocation -> {
            List<ExternalTrendResourceDTO> input = invocation.getArgument(0);
            return new ExternalResourceCleaningService.CleaningResult(input, 0, 0, 0);
        });

        PostEvolutionExternalResourceService service = new PostEvolutionExternalResourceServiceImpl(client, cleaner, properties);
        var first = service.search(" AI ", 8);
        var second = service.search("AI", 8);

        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        verify(client, times(1)).search("AI", 8);
        verify(cleaner, times(1)).clean(anyList());
    }

    @Test
    void upstreamFailureReturnsDegradedResult() {
        ZhihuSearchClient client = mock(ZhihuSearchClient.class);
        when(client.search(anyString(), anyInt())).thenThrow(new IllegalStateException("down"));
        ExternalResourceCleaningService cleaner = mock(ExternalResourceCleaningService.class);
        PostEvolutionExternalResourceService service = new PostEvolutionExternalResourceServiceImpl(
                client, cleaner, new ZhihuApiProperties());

        var result = service.search("AI", 8);

        assertThat(result.available()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.items()).isEmpty();
        verifyNoInteractions(cleaner);
    }
}
