package com.example.matching.service.evolution.crawler;

import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.mapper.evolution.MarketJdCrawlerBatchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场 JD 批次解析的僵尸回收。
 * <p>
 * <b>为什么需要：</b>解析状态是「先写 RUNNING、再执行」的（{@code MarketJdBatchAnalysisListener}
 * 与手动重解析都这样），一旦 JVM 在解析途中退出（发版替换 jar、进程被杀），
 * 那行批次日志就<b>永远停在「解析中」</b>：界面上表现为这一批永远在转，
 * 用户以为系统欠它一次解析，反复点「重新解析」也不见好转。
 * <p>
 * 项目里其它长任务领域（{@code matching_task} / {@code post_evolution_task} /
 * {@code emp_resume_parse} / {@code post_import_batch} / {@code post_trend_task}）都有
 * {@code findZombieTasks} 配套回收，市场 JD 这条链路此前漏了。
 * <p>
 * 回收只改<b>批次日志</b>的状态，不动 {@code market_jd_data}：批次解析中每条 JD 仍是
 * {@code analysis_status=0}，本来就处于「可重试」态。把批次日志置为失败，
 * 用户才能看到真实结论并重新发起解析。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketJdBatchAnalysisZombieScanner {

    /**
     * 单次扫描最多回收的行数。
     * <p>
     * 与项目其它领域一致地设上限：回收发生在启动初期或长时间停机之后，
     * 那种场景下可能积压大量僵尸批次，逐个 UPDATE 不应一次性铺开。
     */
    private static final int ZOMBIE_SCAN_LIMIT = 100;

    /** 与 {@code CrawlerBatchLogService.ANALYSIS_NOTE_MAX} 对齐，避免写超列宽被截断报错。 */
    private static final int ANALYSIS_NOTE_MAX = 500;

    private final MarketJdCrawlerBatchLogMapper batchLogMapper;

    /**
     * 超时阈值。
     * <p>
     * 默认 120 分钟而不是 30 分钟：批次解析是逐条调用大模型的串行链路，
     * 一个几百条 JD 的批次跑上几十分钟属正常。阈值定小了会把**正在正常解析**的批次
     * 判成僵尸并写成「失败」——这比不回收更糟糕（用户看到失败就以为数据丢了）。
     * 解析中途进程退出留下的僵尸会一直留着，多等一会儿没有额外代价。
     */
    @Value("${market-jd.crawler.zombie-timeout-minutes:120}")
    private int zombieTimeoutMinutes;

    /**
     * 回收僵尸批次：把长时间停在「进行中」的批次置为失败，并写清原因与可重试入口。
     *
     * @return 实际回收的行数
     */
    @Scheduled(fixedDelayString = "${market-jd.crawler.zombie-scan-delay-ms:600000}",
            initialDelayString = "${market-jd.crawler.zombie-scan-initial-delay-ms:120000}")
    public int scanZombieAnalyses() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(zombieTimeoutMinutes);
        String note = "解析超过 " + zombieTimeoutMinutes + " 分钟无进展（通常因服务重启中断），"
                + "已自动终止；该批次的数据仍为待分析，可点「重新解析」重试。";
        int recovered = 0;
        recovered += recover(MarketJdCrawlerBatchLog.ANALYSIS_RUNNING, before, note);
        recovered += recover(MarketJdCrawlerBatchLog.ANALYSIS_QUEUED, before, note);
        if (recovered > 0) {
            log.warn("已回收僵尸的市场JD批次解析: count={}, timeoutMinutes={}", recovered, zombieTimeoutMinutes);
        }
        return recovered;
    }

    private int recover(String fromState, LocalDateTime before, String note) {
        List<MarketJdCrawlerBatchLog> zombies;
        try {
            zombies = batchLogMapper.findZombieAnalysisLogs(fromState, before, ZOMBIE_SCAN_LIMIT);
        } catch (Exception exception) {
            // 扫描本身失败不能影响其它调度：记录后交给下一轮
            log.warn("僵尸批次扫描失败: state={}", fromState, exception);
            return 0;
        }
        if (zombies == null || zombies.isEmpty()) {
            return 0;
        }
        String safeNote = note.length() <= ANALYSIS_NOTE_MAX ? note : note.substring(0, ANALYSIS_NOTE_MAX);
        int recovered = 0;
        for (MarketJdCrawlerBatchLog zombie : zombies) {
            if (zombie == null || zombie.getId() == null) {
                continue;
            }
            try {
                int affected = batchLogMapper.finishZombieAnalysis(zombie.getId(), fromState,
                        MarketJdCrawlerBatchLog.ANALYSIS_FAILED, safeNote);
                if (affected > 0) {
                    recovered++;
                    log.warn("回收僵尸批次解析: batchNo={}, logId={}, from={}",
                            zombie.getBatchNo(), zombie.getId(), fromState);
                }
            } catch (Exception exception) {
                // 单行失败不阻断整轮：否则一条坏数据会让调度每轮卡在同一处
                log.warn("回收僵尸批次失败: logId={}, batchNo={}", zombie.getId(), zombie.getBatchNo(), exception);
            }
        }
        return recovered;
    }
}


