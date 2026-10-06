package com.example.matching.schedule;

import com.example.matching.service.contest.EvidenceCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 证据自动回填调度器
 * <p>
 * 从 JD 导入、简历解析、匹配反馈、员工能力、岗位模型自动回填证据，
 * 回填后交由 AI Trust Harness 自动审核（见 {@code EvidenceBackfillService}），
 * 人工无需点击回填按钮、也无需逐条审核。
 * <p>
 * 幂等设计：回填按「来源 + 目标」去重（{@code existsBySourceAndTarget}），
 * 重复执行只会补齐缺失的证据，不会产生重复数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceBackfillScheduler {

    /** 自动回填的来源类型（与证据中心「数据回填」按钮一一对应） */
    private static final List<String> BACKFILL_SOURCES = List.of(
            "JD_IMPORT", "RESUME_PARSE", "MATCHING_FEEDBACK", "EMP_ABILITY", "POST_ABILITY_MODEL");

    /** 每次回填的批量上限（大于前端手动回填的 100，覆盖更多历史数据） */
    private static final int BACKFILL_LIMIT = 500;

    private final EvidenceCenterService evidenceCenterService;
    private final ScheduledTaskRunner scheduledTaskRunner;

    /**
     * 启动 1 分钟后首次回填，之后每小时自动回填一次。
     * 通过 {@link ScheduledTaskRunner} 统一获取分布式锁、设置系统上下文并写审计日志。
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void autoBackfill() {
        scheduledTaskRunner.run("evidence-backfill", () -> {
            for (String sourceType : BACKFILL_SOURCES) {
                try {
                    int created = evidenceCenterService.backfillEvidence(sourceType, BACKFILL_LIMIT);
                    if (created > 0) {
                        log.info("证据自动回填完成: sourceType={}, created={}", sourceType, created);
                    }
                } catch (Exception e) {
                    log.warn("证据自动回填失败: sourceType={}, error={}", sourceType, e.getMessage());
                }
            }
        });
    }
}
