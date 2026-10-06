package com.example.matching.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.entity.post.PostImportItem;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.mapper.post.PostImportItemMapper;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Excel导入AI分析消费者
 * <p>
 * 监听 excel.import.analyze.queue，逐条对岗位进行AI能力分析。
 * 进度实时写入数据库，前端可通过轮询 getPreview 获取最新状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelImportAnalyzeListener {

    private final PostImportBatchMapper importBatchMapper;
    private final PostImportItemMapper importItemMapper;
    private final PostCapabilityGenerationService capabilityGenerationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.EXCEL_IMPORT_ANALYZE_QUEUE, containerFactory = "excelImportAiRabbitListenerContainerFactory")
    public void handleAnalyze(Long batchId) {
        log.info("收到Excel导入分析任务: batchId={}", batchId);

        SecurityUtils.setSystemContext();
        try {
            // 幂等抢占：同一消息并发投递只允许一次成功；更新条数不是 1 时直接返回
            int claimed = importBatchMapper.claimAnalysis(batchId);
            if (claimed != 1) {
                log.debug("Excel导入分析抢占失败或已处理，直接返回: batchId={}", batchId);
                return;
            }

            PostImportBatch batch = importBatchMapper.selectById(batchId);
            if (batch == null) {
                log.warn("导入批次不存在: batchId={}", batchId);
                return;
            }

            try {
                // 查询所有待分析的明细
                List<PostImportItem> items = importItemMapper.selectList(
                        Wrappers.<PostImportItem>lambdaQuery()
                                .eq(PostImportItem::getBatchId, batchId)
                                .eq(PostImportItem::getAnalysisStatus, 0));

                log.info("开始AI能力分析: batchId={}, 待分析数量={}", batchId, items.size());

                int successCount = 0;
                int failCount = 0;
                boolean cancelled = false;

                for (PostImportItem item : items) {
                    // 每条处理前检查取消标志
                    PostImportBatch currentBatch = importBatchMapper.selectById(batchId);
                    if (currentBatch.getCancelFlag() != null && currentBatch.getCancelFlag() == 1) {
                        log.info("分析已被用户取消: batchId={}", batchId);
                        cancelled = true;
                        break;
                    }

                    try {
                        // 更新状态为分析中
                        item.setAnalysisStatus(1);
                        importItemMapper.updateById(item);

                        // 构建分析文本
                        String textForAnalysis = item.getPostDescription() != null ? item.getPostDescription() :
                                (item.getResponsibilityText() != null ? item.getResponsibilityText() : "");

                        // 调用AI能力提取
                        List<JdAbilityItemDTO> abilities = capabilityGenerationService.analyzePostText(
                                item.getPostName(), textForAnalysis);

                        // 保存分析结果
                        item.setAiAnalysisResponse(toJson(abilities));
                        item.setAnalysisStatus(2); // 成功
                        importItemMapper.updateById(item);

                        successCount++;
                        log.debug("岗位能力分析成功: batchId={}, row={}, postName={}", batchId, item.getRowIndex(), item.getPostName());

                    } catch (Exception e) {
                        log.error("岗位能力分析失败: batchId={}, row={}, error={}", batchId, item.getRowIndex(), e.getMessage());
                        item.setAnalysisStatus(3); // 失败
                        item.setErrorMessage(e.getMessage());
                        importItemMapper.updateById(item);
                        failCount++;
                    }
                }

                // 更新批次状态
                if (cancelled) {
                    batch.setImportStatus(5); // 失败（用户取消）
                    batch.setErrorMessage("用户手动取消分析");
                    importBatchMapper.updateById(batch);
                    log.info("Excel导入分析已取消: batchId={}, 已处理成功={}, 失败={}", batchId, successCount, failCount);
                } else {
                    batch.setImportStatus(2); // 待确认
                    batch.setSuccessCount(successCount);
                    batch.setFailCount(failCount);
                    importBatchMapper.updateById(batch);
                    log.info("Excel导入AI分析完成: batchId={}, total={}, success={}, fail={}",
                            batchId, items.size(), successCount, failCount);
                }

            } catch (Exception e) {
                log.error("Excel导入分析任务异常: batchId={}, error={}", batchId, e.getMessage(), e);
                batch.setImportStatus(5); // 失败
                batch.setErrorMessage("分析任务异常: " + e.getMessage());
                importBatchMapper.updateById(batch);
            }
        } finally {
            SecurityUtils.clear();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
