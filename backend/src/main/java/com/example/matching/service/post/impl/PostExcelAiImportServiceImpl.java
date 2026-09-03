package com.example.matching.service.post.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.*;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.entity.post.PostImportItem;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.mapper.post.PostImportItemMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostExcelAiImportService;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.post.PostPostWriteService;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.mapper.post.PostPostMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Excel AI导入服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostExcelAiImportServiceImpl implements PostExcelAiImportService {

    private final PostImportBatchMapper importBatchMapper;
    private final PostImportItemMapper importItemMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final PostPostMapper postPostMapper;
    private final PostPostWriteService postPostWriteService;
    private final PostCapabilityGenerationService capabilityGenerationService;
    private final AbilityTagService abilityTagService;
    private final EventOutboxDispatcher outboxDispatcher;
    private final LangChain4jChatService langChain4jChatService;
    private final AiServiceResilience aiServiceResilience;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExcelStructureRecognizer structureRecognizer;
    private final com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    private final MarketJdImportService marketJdImportService;

    @Override
    public PostImportPreviewDTO uploadAndAnalyze(String fileName, InputStream inputStream) {
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 格式");
        }

        // 1. 读取Excel原始数据
        List<List<String>> rawData = structureRecognizer.readExcelRaw(inputStream);

        // 2. AI识别Excel结构
        String aiStructureResponse = structureRecognizer.callAiForStructureRecognition(rawData, fileName);
        ExcelStructureDTO structure = structureRecognizer.parseStructureResponse(aiStructureResponse);

        // 3. 根据AI识别的结构，组装岗位对象
        List<PostImportItem> items = structureRecognizer.assemblePostItems(rawData, structure);

        // 4. 创建导入批次
        PostImportBatch batch = new PostImportBatch();
        batch.setFileName(fileName);
        batch.setTotalRows(items.size());
        batch.setAiStructureResponse(aiStructureResponse);
        batch.setImportStatus(0); // 待分析（不做能力分析，等前端触发）
        importBatchMapper.insert(batch);

        // 5. 保存明细（不做能力分析）
        for (PostImportItem item : items) {
            item.setBatchId(batch.getId());
        }
        if (!items.isEmpty()) {
            importItemMapper.insert(items);
        }

        // 6. 返回预览（岗位名称已解析，能力分析待触发）
        return structureRecognizer.buildPreview(batch, items, structure);
    }

    @Override
    @Transactional
    public void analyzeBatch(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }

        // 重复消费保护：如果已在分析中，跳过
        if (batch.getImportStatus() != null && batch.getImportStatus() == 1) {
            log.warn("批次已在分析中，跳过重复提交: batchId={}", batchId);
            return;
        }

        // 只有待分析状态(0)才能触发分析；状态置为分析中由消费者幂等抢占完成
        if (batch.getImportStatus() != null && batch.getImportStatus() != 0) {
            log.warn("批次状态不允许触发分析: batchId={}, status={}", batchId, batch.getImportStatus());
            return;
        }

        // 初始化Redis进度计数器
        initProgressInRedis(batchId, batch.getTotalRows());

        outboxDispatcher.enqueue("EXCEL_IMPORT_ANALYZE", RabbitMQConfig.MATCHING_EXCHANGE,
                "excel.import.analyze.execute", batchId);
        log.info("Excel导入分析任务已写入 Outbox: batchId={}", batchId);
    }

    @Override
    public PostImportPreviewDTO getPreview(Long batchId) {
        // 尝试从Redis缓存获取
        String cacheKey = RedisCacheNames.IMPORT_PREVIEW + ":" + batchId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof PostImportPreviewDTO) {
                return (PostImportPreviewDTO) cached;
            }
        } catch (Exception e) {
            log.warn("Redis读取预览缓存失败: batchId={}, error={}", batchId, e.getMessage());
        }

        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }

        List<PostImportItem> items = importItemMapper.selectList(
                Wrappers.<PostImportItem>lambdaQuery()
                        .eq(PostImportItem::getBatchId, batchId)
                        .orderByAsc(PostImportItem::getRowIndex));

        ExcelStructureDTO structure = null;
        if (batch.getAiStructureResponse() != null) {
            try {
                structure = objectMapper.readValue(batch.getAiStructureResponse(), ExcelStructureDTO.class);
            } catch (Exception e) {
                log.warn("解析结构响应失败: {}", e.getMessage());
            }
        }

        PostImportPreviewDTO preview = structureRecognizer.buildPreview(batch, items, structure);

        // 写入Redis缓存，TTL 10秒（前端每3秒轮询）
        try {
            redisTemplate.opsForValue().set(cacheKey, preview, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis写入预览缓存失败: batchId={}, error={}", batchId, e.getMessage());
        }

        return preview;
    }

    @Override
    @Transactional
    public void confirmAndImport(PostImportConfirmDTO confirmDTO) {
        PostImportBatch batch = importBatchMapper.selectById(confirmDTO.getBatchId());
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + confirmDTO.getBatchId());
        }

        // 幂等守卫：仅"分析完成待确认"(2) 可进入"导入中"(3)，重复确认/并发确认只允许一次成功
        if (importBatchMapper.confirmImport(batch.getId()) != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "批次状态不允许确认导入（可能已导入或仍在分析中）: batchId=" + batch.getId()
                            + ", importStatus=" + batch.getImportStatus());
        }
        batch.setImportStatus(3); // 与 DB 保持一致，供前端立即显示“导入中”

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(confirmDTO);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "确认导入内容无法保存");
        }
        if (importBatchMapper.saveConfirmPayload(batch.getId(), payload) != 1) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "确认导入任务保存失败");
        }
        outboxDispatcher.enqueue("EXCEL_IMPORT_CONFIRM", RabbitMQConfig.MATCHING_EXCHANGE,
                "excel.import.confirm.execute", batch.getId());
        log.info("Excel确认导入任务已写入Outbox: batchId={}, itemCount={}", batch.getId(),
                confirmDTO.getItems() == null ? 0 : confirmDTO.getItems().size());
    }

    /**
     * 消费确认导入任务，执行原有岗位创建、能力模型应用和市场 JD 纳入逻辑。
     * 该方法只由 ExcelImportConfirmListener 调用，所有写入仍在一个事务中完成。
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_ENABLED, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.POST_MODEL, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.POST_POST_PAGE, allEntries = true)
    })
    public void processConfirmedImport(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null || !Integer.valueOf(3).equals(batch.getImportStatus())) {
            log.debug("确认导入批次已不存在或不在导入中: batchId={}", batchId);
            return;
        }

        final PostImportConfirmDTO confirmDTO;
        try {
            confirmDTO = objectMapper.readValue(batch.getConfirmPayload(), PostImportConfirmDTO.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "确认导入任务载荷损坏");
        }
        if (confirmDTO.getItems() == null) {
            confirmDTO.setItems(List.of());
        }

        // 1. 预加载所有需要确认的item（一次查询）
        List<Long> itemIds = confirmDTO.getItems().stream()
                .filter(i -> i.getConfirmed() != null && i.getConfirmed())
                .map(PostImportConfirmDTO.ConfirmItem::getItemId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PostImportItem> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            List<PostImportItem> loadedItems = importItemMapper.selectBatchIds(itemIds);
            for (PostImportItem item : loadedItems) {
                itemMap.put(item.getId(), item);
            }
        }

        // 2. 预加载所有启用标签到Map（避免每个NEW标签都查DB）
        Map<String, AbilityTag> tagNameMap = new HashMap<>();
        List<AbilityTag> allTags = abilityTagService.list(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AbilityTag>lambdaQuery()
                        .eq(AbilityTag::getStatus, 1));
        for (AbilityTag tag : allTags) {
            tagNameMap.put(tag.getTagName(), tag);
        }

        // 3. 批量处理：收集所有岗位和能力模型配置
        List<PostPost> postsToInsert = new ArrayList<>();
        Map<Integer, List<JdAbilityItemDTO>> postAbilitiesMap = new HashMap<>(); // index -> abilities
        // itemId -> 岗位映射：与 postsToInsert 一一对应回填 createdPostId，避免失败项穿插导致索引错位
        Map<Long, PostPost> postByItemId = new LinkedHashMap<>();
        List<PostImportItem> itemsToUpdate = new ArrayList<>();

        int idx = 0;
        for (PostImportConfirmDTO.ConfirmItem confirmItem : confirmDTO.getItems()) {
            if (confirmItem.getConfirmed() == null || !confirmItem.getConfirmed()) {
                continue;
            }

            PostImportItem item = itemMap.get(confirmItem.getItemId());
            if (item == null) continue;

            try {
                PostPost post = new PostPost();
                post.setPostCode("IMP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                post.setPostName(confirmItem.getPostName() != null ? confirmItem.getPostName() : item.getPostName());
                post.setJobDescription(confirmItem.getPostDescription() != null ? confirmItem.getPostDescription() : item.getPostDescription());
                post.setStatus(1);
                postsToInsert.add(post);
                postByItemId.put(item.getId(), post);

                // 解析能力项
                List<JdAbilityItemDTO> abilities = confirmItem.getAbilities();
                if (abilities == null || abilities.isEmpty()) {
                    if (item.getAiAnalysisResponse() != null) {
                        try {
                            abilities = objectMapper.readValue(item.getAiAnalysisResponse(),
                                    new TypeReference<List<JdAbilityItemDTO>>() {});
                        } catch (Exception e) {
                            log.warn("解析AI分析结果失败: itemId={}", item.getId());
                        }
                    }
                }
                postAbilitiesMap.put(idx, abilities);

                // 标记待更新
                item.setAnalysisStatus(2);
                itemsToUpdate.add(item);

                idx++;
            } catch (Exception e) {
                log.error("准备导入岗位失败: itemId={}, error={}", item.getId(), e.getMessage(), e);
                item.setAnalysisStatus(3);
                item.setErrorMessage(e.getMessage());
                itemsToUpdate.add(item);
            }
        }

        // 4. 批量插入岗位
        int successCount = 0;
        if (!postsToInsert.isEmpty()) {
            postPostWriteService.batchSave(postsToInsert);
            successCount = postsToInsert.size();
        }

        // 5. 批量应用能力模型（使用预加载的标签Map）
        for (int i = 0; i < postsToInsert.size(); i++) {
            PostPost post = postsToInsert.get(i);
            List<JdAbilityItemDTO> abilities = postAbilitiesMap.get(i);
            if (abilities != null && !abilities.isEmpty()) {
                capabilityGenerationService.applyAbilityItemsToPost(post.getId(), abilities, tagNameMap);
            }
        }
        // 5.1 回填 createdPostId：按 itemId 精确对应，失败项(status=3)跳过，杜绝索引错位
        for (Map.Entry<Long, PostPost> entry : postByItemId.entrySet()) {
            PostImportItem item = itemMap.get(entry.getKey());
            if (item != null && item.getAnalysisStatus() == 2) {
                item.setCreatedPostId(entry.getValue().getId());
            }
        }

        // 6. 批量更新明细
        if (!itemsToUpdate.isEmpty()) {
            importItemMapper.updateById(itemsToUpdate);
        }

        // 7. 更新批次状态
        int failCount = (int) itemsToUpdate.stream().filter(i -> i.getAnalysisStatus() == 3).count();
        batch.setSuccessCount(successCount);
        batch.setFailCount(failCount);
        batch.setImportStatus(4); // 导入完成
        importBatchMapper.updateById(batch);

        // 8. 失效Redis缓存
        invalidatePreviewCache(confirmDTO.getBatchId());
        invalidateProgressCache(confirmDTO.getBatchId());
        vectorRecallCacheEpoch.advance();

        if (Boolean.TRUE.equals(confirmDTO.getIncludeMarketJd())) {
            int marketJdCount = includeBatchInMarketDiscovery(batch.getId());
            log.info("岗位导入批次已纳入市场发现: batchId={}, marketJdCount={}", batch.getId(), marketJdCount);
        }

        log.info("Excel批量导入完成: batchId={}, success={}, fail={}", batch.getId(), successCount, failCount);
    }

    @Override
    @Transactional
    public int includeBatchInMarketDiscovery(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }
        if (!Integer.valueOf(4).equals(batch.getImportStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已完成的导入批次可以纳入市场发现: batchId=" + batchId);
        }
        List<PostImportItem> items = importItemMapper.selectList(Wrappers.<PostImportItem>lambdaQuery()
                .eq(PostImportItem::getBatchId, batchId)
                .isNotNull(PostImportItem::getCreatedPostId));
        if (items.isEmpty()) {
            return 0;
        }
        List<Long> postIds = items.stream().map(PostImportItem::getCreatedPostId).distinct().toList();
        Map<Long, PostPost> posts = postPostMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostPost::getId, post -> post));
        Map<Long, List<Long>> tagIdsByPostId = postAbilityModelMapper.selectList(
                        Wrappers.<com.example.matching.entity.post.PostAbilityModel>lambdaQuery()
                                .in(com.example.matching.entity.post.PostAbilityModel::getPostId, postIds)
                                .isNotNull(com.example.matching.entity.post.PostAbilityModel::getTagId))
                .stream().collect(Collectors.groupingBy(
                        com.example.matching.entity.post.PostAbilityModel::getPostId,
                        Collectors.mapping(com.example.matching.entity.post.PostAbilityModel::getTagId,
                                Collectors.filtering(Objects::nonNull, Collectors.toList()))));
        List<MarketJdImportService.VerifiedPostImportJd> result = new ArrayList<>();
        for (Long postId : postIds) {
            PostPost post = posts.get(postId);
            if (post == null) {
                continue;
            }
            result.add(new MarketJdImportService.VerifiedPostImportJd(
                    post.getPostName(), post.getJobDescription(), postId,
                    tagIdsByPostId.getOrDefault(postId, List.of())));
        }
        return marketJdImportService.importVerifiedPostBatch(batchId, result);
    }

    @Override
    public void cancelBatch(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }

        // 只有分析中的批次才能取消
        if (batch.getImportStatus() == null || batch.getImportStatus() != 1) {
            log.warn("批次不在分析中，无需取消: batchId={}, status={}", batchId, batch.getImportStatus());
            return;
        }

        // 设置取消标志
        batch.setCancelFlag(1);
        importBatchMapper.updateById(batch);

        // 失效Redis缓存
        invalidatePreviewCache(batchId);

        log.info("已设置取消标志: batchId={}", batchId);
    }

    @Override
    public Page<PostImportBatchVO> pageBatches(long current, long size, Integer importStatus) {
        Page<PostImportBatch> page = new Page<>(current, size);
        var wrapper = Wrappers.<PostImportBatch>lambdaQuery()
                .orderByDesc(PostImportBatch::getId);
        if (importStatus != null) {
            wrapper.eq(PostImportBatch::getImportStatus, importStatus);
        }
        Page<PostImportBatch> batchPage = importBatchMapper.selectPage(page, wrapper);

        // 转换为VO
        Page<PostImportBatchVO> voPage = new Page<>(batchPage.getCurrent(), batchPage.getSize(), batchPage.getTotal());
        List<PostImportBatchVO> voList = new ArrayList<>();
        if (batchPage.getRecords().isEmpty()) {
            voPage.setRecords(voList);
            return voPage;
        }

        // 构建VO基础字段
        List<Long> batchIds = new ArrayList<>();
        Map<Long, PostImportBatchVO> voMap = new HashMap<>();
        for (PostImportBatch batch : batchPage.getRecords()) {
            PostImportBatchVO vo = new PostImportBatchVO();
            vo.setId(batch.getId());
            vo.setFileName(batch.getFileName());
            vo.setTotalRows(batch.getTotalRows());
            vo.setSuccessCount(batch.getSuccessCount());
            vo.setFailCount(batch.getFailCount());
            vo.setImportStatus(batch.getImportStatus());
            vo.setCancelFlag(batch.getCancelFlag());
            vo.setErrorMessage(batch.getErrorMessage());
            vo.setCreatedTime(batch.getCreatedTime());
            vo.setUpdatedTime(batch.getUpdatedTime());
            vo.setPendingCount(0);
            vo.setAnalyzingCount(0);
            vo.setSuccessAnalyzedCount(0);
            vo.setFailedAnalyzedCount(0);
            batchIds.add(batch.getId());
            voMap.put(batch.getId(), vo);
        }

        // 一次聚合查询获取所有批次的统计
        List<Map<String, Object>> stats = importItemMapper.countByBatchIds(batchIds);
        for (Map<String, Object> row : stats) {
            Long batchId = ((Number) row.get("batch_id")).longValue();
            int status = ((Number) row.get("analysis_status")).intValue();
            int cnt = ((Number) row.get("cnt")).intValue();
            PostImportBatchVO vo = voMap.get(batchId);
            if (vo == null) continue;
            switch (status) {
                case 0 -> vo.setPendingCount(cnt);
                case 1 -> vo.setAnalyzingCount(cnt);
                case 2 -> vo.setSuccessAnalyzedCount(cnt);
                case 3 -> vo.setFailedAnalyzedCount(cnt);
            }
        }

        voList.addAll(voMap.values());
        // 保持分页顺序
        voList.sort(Comparator.comparing(PostImportBatchVO::getId).reversed());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional
    public void retryBatch(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }

        Integer status = batch.getImportStatus();
        // 仅对待解析(0)、导入失败(5)、已取消的批次允许重试
        boolean canRetry = (status == 0 || status == 5)
                || (status == 1 && batch.getCancelFlag() != null && batch.getCancelFlag() == 1);
        if (!canRetry) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "当前状态不允许重试: batchId=" + batchId + ", status=" + status);
        }

        // 重置失败项的分析状态为待分析
        importItemMapper.update(null,
                Wrappers.<PostImportItem>lambdaUpdate()
                        .eq(PostImportItem::getBatchId, batchId)
                        .in(PostImportItem::getAnalysisStatus, 0, 1, 3)
                        .set(PostImportItem::getAnalysisStatus, 0)
                        .set(PostImportItem::getErrorMessage, null));

        // 重置批次状态
        batch.setImportStatus(0);
        batch.setCancelFlag(0);
        batch.setErrorMessage(null);
        importBatchMapper.updateById(batch);

        // 重新触发分析
        analyzeBatch(batchId);
        log.info("已重试批次分析: batchId={}", batchId);
    }

    @Override
    @Transactional
    public void deleteBatch(Long batchId) {
        PostImportBatch batch = importBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "导入批次不存在: " + batchId);
        }
        // 先设置取消标志，已在队列中的消费者在下一次检查时安全退出。
        if (Integer.valueOf(1).equals(batch.getImportStatus()) || Integer.valueOf(3).equals(batch.getImportStatus())) {
            batch.setCancelFlag(1);
            importBatchMapper.updateById(batch);
        }
        importItemMapper.deleteByBatchId(batchId);
        importBatchMapper.deleteById(batchId);
        invalidatePreviewCache(batchId);
        invalidateProgressCache(batchId);
        log.info("已删除Excel导入批次及临时明细，不影响已导入岗位: batchId={}", batchId);
    }

    // ===== 内部方法 =====

    /**
     * 读取Excel原始数据（不使用DTO映射，直接读取单元格文本）
     */

    private void initProgressInRedis(Long batchId, int total) {
        try {
            String key = RedisCacheNames.IMPORT_PROGRESS + ":" + batchId;
            Map<String, Object> progress = new HashMap<>();
            progress.put("total", total);
            progress.put("pending", total);
            progress.put("analyzing", 0);
            progress.put("success", 0);
            progress.put("failed", 0);
            redisTemplate.opsForValue().set(key, progress, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis初始化进度失败: batchId={}, error={}", batchId, e.getMessage());
        }
    }

    private void invalidatePreviewCache(Long batchId) {
        try {
            redisTemplate.delete(RedisCacheNames.IMPORT_PREVIEW + ":" + batchId);
        } catch (Exception e) {
            log.warn("Redis失效预览缓存失败: batchId={}, error={}", batchId, e.getMessage());
        }
    }

    private void invalidateProgressCache(Long batchId) {
        try {
            redisTemplate.delete(RedisCacheNames.IMPORT_PROGRESS + ":" + batchId);
        } catch (Exception e) {
            log.warn("Redis失效进度缓存失败: batchId={}, error={}", batchId, e.getMessage());
        }
    }
}
