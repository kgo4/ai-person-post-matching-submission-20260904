package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.evolution.api.CrawlerBatchLogView;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.mapper.evolution.MarketJdCrawlerBatchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 爬虫推送批次日志读写。
 * <p>
 * 对齐对接文档 §7.5「记录请求 IP、批次号、来源和处理结果」，并额外承载
 * 「推送数据是否已进入分析链路」的状态（{@code analysis_state}），
 * 使前端能直接回答「推了多少、去重多少、解析到什么程度」。
 * <p>
 * 所有写操作都**吞掉异常只告警**：日志不是业务必需，绝不能因为日志表问题让爬虫收到非 2xx
 * 从而触发无意义重试。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerBatchLogService {

    private static final int ERROR_SAMPLE_MAX = 2000;
    private static final int ANALYSIS_NOTE_MAX = 500;

    private final MarketJdCrawlerBatchLogMapper batchLogMapper;

    /**
     * 写入一条批次日志。
     *
     * @return 新日志行 id；写入失败返回 {@code null}
     */
    public Long record(MarketJdCrawlerBatchLog batchLog) {
        if (batchLog.getIngestChannel() == null || batchLog.getIngestChannel().isBlank()) {
            // 历史口径默认值：改造前只有爬虫推送会走到这里，补一个默认值可以让
            // 既有的两处调用点（爬虫推送、被拒入站）不必各自重复设置。
            batchLog.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_CRAWLER);
        }
        try {
            batchLogMapper.insert(batchLog);
            return batchLog.getId();
        } catch (Exception exception) {
            log.warn("爬虫批次日志写入失败（不影响接收结果）: batchNo={}", batchLog.getBatchNo(), exception);
            return null;
        }
    }

    /**
     * 删除某批次的全部登记行。
     * <p>
     * 与 {@code market_jd_data} 的删除同属「批次删除」动作，由
     * {@code MarketJdImportService#deleteBatch} 统一编排；本方法只负责登记表。
     *
     * @return 删除的登记行数
     */
    public int deleteByBatchNo(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return 0;
        }
        return batchLogMapper.delete(new LambdaQueryWrapper<MarketJdCrawlerBatchLog>()
                .eq(MarketJdCrawlerBatchLog::getBatchNo, batchNo));
    }

    /** 某批次在登记表里的行数（用于删除前确认「批次是否存在」）。 */
    public long countByBatchNo(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return 0L;
        }
        Long count = batchLogMapper.selectCount(new LambdaQueryWrapper<MarketJdCrawlerBatchLog>()
                .eq(MarketJdCrawlerBatchLog::getBatchNo, batchNo));
        return count == null ? 0L : count;
    }

    /** 回写某次推送的解析状态（成功/失败/跳过）。 */
    public void markAnalysis(Long logId, String analysisState, String note) {
        if (logId == null) {
            return;
        }
        try {
            MarketJdCrawlerBatchLog patch = new MarketJdCrawlerBatchLog();
            patch.setId(logId);
            patch.setAnalysisState(analysisState);
            patch.setAnalysisNote(truncate(note, ANALYSIS_NOTE_MAX));
            patch.setAnalysisStartedAt(startedAtOf(analysisState));
            batchLogMapper.updateById(patch);
        } catch (Exception exception) {
            log.warn("爬虫批次日志解析状态回写失败: logId={}, state={}", logId, analysisState, exception);
        }
    }

    /**
     * 「进行中」状态才写开始时间，供僵尸回收判定。
     * <p>
     * 终态不写该字段（{@code updateById} 忽略 null，旧值会留下）。这没有影响：
     * 扫描只挑 {@code QUEUED} / {@code RUNNING} 的行，终态行带什么时间都不会被处理；
     * 而该批次若再次进入解析，{@code markAnalysis} 会刷新成新的开始时间。
     */
    private static LocalDateTime startedAtOf(String analysisState) {
        if (MarketJdCrawlerBatchLog.ANALYSIS_QUEUED.equals(analysisState)
                || MarketJdCrawlerBatchLog.ANALYSIS_RUNNING.equals(analysisState)) {
            return LocalDateTime.now();
        }
        return null;
    }

    /** 最近接收批次（倒序），供前端展示。 */
    public List<CrawlerBatchLogView> recent(int limit) {
        return recent(limit, null);
    }

    /**
     * 最近接收批次（倒序），可按入库通道筛选。
     * <p>
     * 通道筛选放在服务端而不是前端本地过滤：列表本身有 limit，若在前端过滤，
     * 「最近 20 条都是爬虫批次」时用户筛「人工上传」会看到空列表——明明数据是存在的。
     *
     * @param limit         返回条数（1–200，默认 20）
     * @param ingestChannel 入库通道；为空表示不筛选
     */
    public List<CrawlerBatchLogView> recent(int limit, String ingestChannel) {
        int safeLimit = limit < 1 ? 20 : Math.min(limit, 200);
        LambdaQueryWrapper<MarketJdCrawlerBatchLog> wrapper = new LambdaQueryWrapper<>();
        // 按业务时间倒序而不是 id：V167 为历史批次补登记行时 id 很大，
        // 若按 id 排序，几年前的老批次会顶到列表最前面。
        wrapper.orderByDesc(MarketJdCrawlerBatchLog::getCreatedTime);
        wrapper.orderByDesc(MarketJdCrawlerBatchLog::getId);
        if (ingestChannel != null && !ingestChannel.isBlank()) {
            wrapper.eq(MarketJdCrawlerBatchLog::getIngestChannel,
                    ingestChannel.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.last("LIMIT " + safeLimit);
        return batchLogMapper.selectList(wrapper).stream()
                .map(CrawlerBatchLogService::toView)
                .toList();
    }

    /** 某批次最近一次推送的日志。 */
    public MarketJdCrawlerBatchLog latestByBatchNo(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            return null;
        }
        return batchLogMapper.selectOne(new LambdaQueryWrapper<MarketJdCrawlerBatchLog>()
                .eq(MarketJdCrawlerBatchLog::getBatchNo, batchNo)
                .orderByDesc(MarketJdCrawlerBatchLog::getId)
                .last("LIMIT 1"));
    }

    /** 某批次最近一次推送的日志视图（含中文状态），供管理端 reanalyze 返回最新状态。 */
    public CrawlerBatchLogView latestView(String batchNo) {
        MarketJdCrawlerBatchLog latest = latestByBatchNo(batchNo);
        return latest == null ? null : toView(latest);
    }

    /** 组装错误摘要：最多 3 条 + 条数说明，并截断到列宽。 */
    public static String buildErrorSample(List<String> errors, int failed) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        List<String> head = errors.size() > 3 ? errors.subList(0, 3) : errors;
        String joined = String.join("；", head);
        if (failed > head.size()) {
            joined = joined + "（另有 " + (failed - head.size()) + " 条同类失败）";
        }
        return truncate(joined, ERROR_SAMPLE_MAX);
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static CrawlerBatchLogView toView(MarketJdCrawlerBatchLog entity) {
        CrawlerBatchLogView view = new CrawlerBatchLogView();
        view.setId(entity.getId());
        view.setBatchNo(entity.getBatchNo());
        view.setIngestChannel(entity.getIngestChannel());
        view.setIngestChannelText(ingestChannelText(entity.getIngestChannel()));
        view.setSourcePlatform(entity.getSourcePlatform());
        view.setRequestIp(entity.getRequestIp());
        view.setItemCount(entity.getItemCount());
        view.setImported(entity.getImported());
        view.setUpdated(entity.getUpdated());
        view.setDuplicate(entity.getDuplicate());
        view.setFailed(entity.getFailed());
        view.setHttpStatus(entity.getHttpStatus());
        view.setResultStatus(entity.getResultStatus());
        view.setAnalysisState(entity.getAnalysisState());
        view.setAnalysisNote(entity.getAnalysisNote());
        view.setCostMillis(entity.getCostMillis());
        view.setCreatedTime(entity.getCreatedTime());
        view.setResultStatusText(resultStatusText(entity.getResultStatus()));
        view.setAnalysisStateText(analysisStateText(entity.getAnalysisState()));
        return view;
    }

    /**
     * 入库通道 → 中文名。
     * <p>
     * 未知取值回显原值而不是吞成「—」：通道是运维判断数据可信度的第一依据，
     * 静默显示成空白比显示一个陌生代码更危险。
     */
    public static String ingestChannelText(String channel) {
        if (channel == null || channel.isBlank()) {
            return "—";
        }
        return switch (channel) {
            case MarketJdCrawlerBatchLog.CHANNEL_CRAWLER -> "爬虫推送";
            case MarketJdCrawlerBatchLog.CHANNEL_MANUAL_UPLOAD -> "人工上传";
            case MarketJdCrawlerBatchLog.CHANNEL_POST_IMPORT -> "岗位导入";
            default -> channel;
        };
    }

    private static String resultStatusText(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case MarketJdCrawlerBatchLog.RESULT_OK -> "接收成功";
            case MarketJdCrawlerBatchLog.RESULT_PARTIAL_FAILED -> "部分条目失败";
            case MarketJdCrawlerBatchLog.RESULT_REJECTED -> "整批被拒";
            case MarketJdCrawlerBatchLog.RESULT_UNAUTHORIZED -> "鉴权失败";
            case MarketJdCrawlerBatchLog.RESULT_HISTORICAL -> "历史批次";
            default -> status;
        };
    }

    private static String analysisStateText(String state) {
        if (state == null) {
            return "—";
        }
        return switch (state) {
            case MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED -> "未触发解析";
            case MarketJdCrawlerBatchLog.ANALYSIS_QUEUED -> "已排队解析";
            case MarketJdCrawlerBatchLog.ANALYSIS_RUNNING -> "解析中";
            case MarketJdCrawlerBatchLog.ANALYSIS_SUCCEEDED -> "解析完成";
            case MarketJdCrawlerBatchLog.ANALYSIS_FAILED -> "解析失败（可重试）";
            case MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED -> "无需解析";
            default -> state;
        };
    }
}


