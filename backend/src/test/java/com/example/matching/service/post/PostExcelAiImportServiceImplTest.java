package com.example.matching.service.post;

import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.dto.post.PostImportConfirmDTO;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.entity.post.PostImportItem;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.mapper.post.PostImportItemMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.post.impl.PostExcelAiImportServiceImpl;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.evolution.MarketJdImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PostExcelAiImportServiceImplTest {

    @Mock private PostImportBatchMapper importBatchMapper;
    @Mock private PostImportItemMapper importItemMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostCapabilityGenerationService capabilityGenerationService;
    @Mock private PostPostWriteService postPostWriteService;
    @Mock private AbilityTagService abilityTagService;
    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private AiServiceResilience aiServiceResilience;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private ObjectMapper objectMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock private com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private MarketJdImportService marketJdImportService;

    @InjectMocks private PostExcelAiImportServiceImpl service;

    @Test
    void analyzeBatch_writesReliableOutboxMessage() {
        PostImportBatch batch = new PostImportBatch();
        batch.setId(11L);
        batch.setImportStatus(0);
        batch.setTotalRows(3);
        when(importBatchMapper.selectById(11L)).thenReturn(batch);

        service.analyzeBatch(11L);

        verify(outboxDispatcher).enqueue(
                eq("EXCEL_IMPORT_ANALYZE"),
                eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("excel.import.analyze.execute"),
                eq(11L));
    }

    @Test
    void confirmAndImport_persistsPayloadAndWritesOutbox() throws Exception {
        PostImportBatch batch = new PostImportBatch();
        batch.setId(11L);
        batch.setImportStatus(2); // 分析完成待确认（幂等守卫要求）
        PostImportItem item = new PostImportItem();
        item.setId(21L);
        item.setPostName("Java Engineer");

        PostImportConfirmDTO.ConfirmItem confirmItem = new PostImportConfirmDTO.ConfirmItem();
        confirmItem.setItemId(21L);
        confirmItem.setConfirmed(true);
        PostImportConfirmDTO request = new PostImportConfirmDTO();
        request.setBatchId(11L);
        request.setItems(List.of(confirmItem));

        when(importBatchMapper.selectById(11L)).thenReturn(batch);
        when(importBatchMapper.confirmImport(11L)).thenReturn(1); // 幂等 CAS 成功
        when(objectMapper.writeValueAsString(request)).thenReturn("{\"batchId\":11}");
        when(importBatchMapper.saveConfirmPayload(eq(11L), any(String.class))).thenReturn(1);

        service.confirmAndImport(request);

        verify(importBatchMapper).saveConfirmPayload(eq(11L), any(String.class));
        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_CONFIRM"), eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("excel.import.confirm.execute"), eq(11L));
        verify(postPostWriteService, never()).batchSave(any());
    }

    @Test
    void confirmAndImport_rejectsRepeatConfirmation() {
        // 回归：批次已导入(4)时重复确认必须被拒绝，防止重复创建岗位
        PostImportBatch batch = new PostImportBatch();
        batch.setId(11L);
        batch.setImportStatus(4);

        PostImportConfirmDTO request = new PostImportConfirmDTO();
        request.setBatchId(11L);
        request.setItems(List.of());

        when(importBatchMapper.selectById(11L)).thenReturn(batch);
        when(importBatchMapper.confirmImport(11L)).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.confirmAndImport(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许确认导入");
    }

    @Test
    void includeCompletedBatchReusesSavedPostModelWithoutAiAnalysis() {
        PostImportBatch batch = new PostImportBatch();
        batch.setId(11L);
        batch.setImportStatus(4);
        PostImportItem item = new PostImportItem();
        item.setBatchId(11L);
        item.setCreatedPostId(31L);
        PostPost post = new PostPost();
        post.setId(31L);
        post.setPostName("Java工程师");
        post.setJobDescription("负责Java服务开发");
        com.example.matching.entity.post.PostAbilityModel first = new com.example.matching.entity.post.PostAbilityModel();
        first.setPostId(31L);
        first.setTagId(10L);
        com.example.matching.entity.post.PostAbilityModel second = new com.example.matching.entity.post.PostAbilityModel();
        second.setPostId(31L);
        second.setTagId(20L);
        when(importBatchMapper.selectById(11L)).thenReturn(batch);
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        when(postPostMapper.selectBatchIds(List.of(31L))).thenReturn(List.of(post));
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(first, second));
        when(marketJdImportService.importVerifiedPostBatch(eq(11L), any())).thenReturn(1);

        assertThat(service.includeBatchInMarketDiscovery(11L)).isEqualTo(1);

        ArgumentCaptor<List<MarketJdImportService.VerifiedPostImportJd>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketJdImportService).importVerifiedPostBatch(eq(11L), captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(jd ->
                assertThat(jd.verifiedTagIds()).containsExactlyInAnyOrder(10L, 20L));
        verify(capabilityGenerationService, never()).analyzeMarketJdText(any(), any(), any(), any());
    }

    @Test
    void confirmAndImport_reportsInsertedPostsWithoutSubtractingFailedItemsTwice() throws Exception {
        PostImportBatch batch = new PostImportBatch();
        batch.setId(11L);
        batch.setImportStatus(2);
        PostImportItem item = new PostImportItem() {
            @Override
            public void setAnalysisStatus(Integer status) {
                if (status != null && status == 2) {
                    throw new IllegalStateException("item preparation failed");
                }
                super.setAnalysisStatus(status);
            }
        };
        item.setId(21L);
        item.setPostName("Java Engineer");

        PostImportConfirmDTO.ConfirmItem confirmItem = new PostImportConfirmDTO.ConfirmItem();
        confirmItem.setItemId(21L);
        confirmItem.setConfirmed(true);
        PostImportConfirmDTO request = new PostImportConfirmDTO();
        request.setBatchId(11L);
        request.setItems(List.of(confirmItem));

        when(importBatchMapper.selectById(11L)).thenReturn(batch);
        when(importBatchMapper.confirmImport(11L)).thenReturn(1);
        when(objectMapper.writeValueAsString(request)).thenReturn("{\"batchId\":11}");
        when(importBatchMapper.saveConfirmPayload(eq(11L), any(String.class))).thenReturn(1);

        service.confirmAndImport(request);

        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_CONFIRM"), eq(RabbitMQConfig.MATCHING_EXCHANGE),
                eq("excel.import.confirm.execute"), eq(11L));
        verify(postPostWriteService, never()).batchSave(any());
    }
}
