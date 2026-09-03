package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.service.common.EventOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Excel 导入僵尸批次扫描器
 * <p>
 * 每 5 分钟扫描超过 15 分钟的 PROCESSING 批次：
 * <ul>
 *   <li>重试次数未达上限：回到待解析并重新投递</li>
 *   <li>重试次数已达上限：进入失败终态，不再自动投递</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelImportZombieScanner {

    /** PROCESSING 超过该时长视为僵尸 */
    private static final long STALE_MINUTES = 15;

    /** 最大自动重试次数（与 PostImportBatchMapper.recoverZombie 的 retry_count < 3 一致） */
    private static final int MAX_RETRY_COUNT = 3;

    private final PostImportBatchMapper importBatchMapper;
    private final EventOutboxDispatcher outboxDispatcher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void scanZombieBatches() {
        if (taskRunner != null) {
            taskRunner.run("excel_import_zombie_scan", this::scanZombieBatchesInternal);
        } else {
            scanZombieBatchesInternal();
        }
    }

    private void scanZombieBatchesInternal() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        List<PostImportBatch> zombies;
        try {
            zombies = importBatchMapper.selectZombieBatches(before);
        } catch (Exception e) {
            log.error("Excel导入僵尸批次扫描失败", e);
            return;
        }

        for (PostImportBatch batch : zombies) {
            recover(batch);
        }
    }

    private void recover(PostImportBatch batch) {
        int retryCount = batch.getRetryCount() != null ? batch.getRetryCount() : 0;
        if (Integer.valueOf(3).equals(batch.getImportStatus())) {
            recoverImportZombie(batch, retryCount);
            return;
        }
        if (retryCount >= MAX_RETRY_COUNT) {
            int updated = importBatchMapper.failZombie(batch.getId(), "ZOMBIE_RECOVERY",
                    "处理超时且重试次数已耗尽");
            if (updated == 1) {
                log.error("Excel导入批次僵尸恢复失败（重试耗尽），标记失败: batchId={}, retryCount={}",
                        batch.getId(), retryCount);
            }
            return;
        }

        int updated = importBatchMapper.recoverZombie(batch.getId(), "ZOMBIE_RECOVERY", "处理超时，自动恢复重试");
        if (updated != 1) {
            log.debug("Excel导入批次状态已变化，跳过恢复: batchId={}", batch.getId());
            return;
        }

        outboxDispatcher.enqueue("EXCEL_IMPORT_ANALYZE", RabbitMQConfig.MATCHING_EXCHANGE,
                "excel.import.analyze.execute", batch.getId());
        log.warn("Excel导入批次僵尸恢复并重新投递: batchId={}, retryCount={}",
                batch.getId(), retryCount + 1);
    }

    private void recoverImportZombie(PostImportBatch batch, int retryCount) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(STALE_MINUTES);
        if (retryCount >= MAX_RETRY_COUNT) {
            int updated = importBatchMapper.failImportZombie(batch.getId(), before, "IMPORT_ZOMBIE_RECOVERY",
                    "岗位导入处理超时且重试次数已耗尽");
            if (updated == 1) {
                log.error("Excel确认导入僵尸批次标记失败: batchId={}, retryCount={}", batch.getId(), retryCount);
            }
            return;
        }
        int updated = importBatchMapper.recoverImportZombie(batch.getId(), before, "IMPORT_ZOMBIE_RECOVERY",
                "岗位导入处理超时，自动恢复重试");
        if (updated == 1) {
            outboxDispatcher.enqueue("EXCEL_IMPORT_CONFIRM", RabbitMQConfig.MATCHING_EXCHANGE,
                    "excel.import.confirm.execute", batch.getId());
            log.warn("Excel确认导入僵尸批次已重新投递: batchId={}, retryCount={}", batch.getId(), retryCount + 1);
        }
    }
}
