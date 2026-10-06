package com.example.matching.schedule;

import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 「仅试算」材料兜底清理调度器。
 *
 * <p>背景：趋势发现支持「只看材料能解析出什么岗位」——这种上传不打算长期留存，
 * 材料被标记为 {@code ephemeral}，既不进材料管理列表，也不会被正式上传复用。
 * 但只靠「不显示」是不够的：分块与向量索引仍然占着空间，得有人把它们真正清掉。
 *
 * <p>为什么需要兜底：试算材料本来计划在解析任务进入终态时清理，但任务被取消、
 * 服务重启、或用户上传后压根没发起解析，都会留下无人认领的试算材料。
 *
 * <p>保留时长默认 24 小时。这个下限是刻意留够的：解析任务停在 RUNNING 超过 30 分钟
 * 就会被判定为僵尸并置为 FAILED，所以「正在被任务使用」的材料不可能活到 24 小时，
 * 清理不会打断进行中的解析，也给了用户当轮会话内反复重试的余地。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EphemeralMaterialPurgeScheduler {

    private final EvolutionSourceIngestionService evolutionSourceIngestionService;
    private final SchedulerMetrics schedulerMetrics;

    @Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    /** 试算材料保留时长（小时）。小于等于 0 时按 24 小时处理。 */
    @Value("${post.trend.ephemeral-retention-hours:24}")
    private int retentionHours;

    @Scheduled(
            fixedDelayString = "${post.trend.ephemeral-purge-delay-ms:3600000}",
            initialDelayString = "${post.trend.ephemeral-purge-initial-delay-ms:600000}")
    public void purgeExpiredEphemeralMaterials() {
        if (taskRunner != null) {
            taskRunner.run("ephemeral_material_purge", this::purgeInternal);
        } else {
            purgeInternal();
        }
    }

    private void purgeInternal() {
        try {
            int purged = evolutionSourceIngestionService.purgeExpiredEphemeralMaterials(retentionHours);
            if (purged > 0) {
                log.info("试算材料兜底清理完成: retentionHours={}, purged={}", retentionHours, purged);
            }
        } catch (Exception e) {
            log.error("试算材料兜底清理失败，试算材料可能持续占用索引空间", e);
            schedulerMetrics.recordFailure("ephemeral_material_purge");
        }
    }
}
