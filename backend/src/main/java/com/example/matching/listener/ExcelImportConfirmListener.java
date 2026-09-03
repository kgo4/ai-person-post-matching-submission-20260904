package com.example.matching.listener;

import com.example.matching.config.RabbitMQConfig;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.service.post.PostExcelAiImportService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 异步执行用户确认后的岗位导入。
 * <p>Outbox 只传批次 ID，确认内容从数据库载荷读取，避免大批次消息超过 TEXT/消息限制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelImportConfirmListener {

    private final PostImportBatchMapper importBatchMapper;
    private final PostExcelAiImportService importService;

    @RabbitListener(queues = RabbitMQConfig.EXCEL_IMPORT_CONFIRM_QUEUE,
            containerFactory = "slowRabbitListenerContainerFactory")
    public void handleConfirm(Long batchId) {
        log.info("收到Excel确认导入任务: batchId={}", batchId);
        SecurityUtils.setSystemContext();
        try {
            if (importBatchMapper.claimImportExecution(batchId) != 1) {
                log.debug("Excel确认导入抢占失败或已处理，直接返回: batchId={}", batchId);
                return;
            }
            importService.processConfirmedImport(batchId);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "后台导入任务异常" : e.getMessage();
            importBatchMapper.markImportFailed(batchId, "IMPORT_EXECUTION_ERROR", message.substring(0, Math.min(message.length(), 500)));
            log.error("Excel确认导入失败: batchId={}, error={}", batchId, e.getMessage(), e);
        } finally {
            SecurityUtils.clear();
        }
    }
}
