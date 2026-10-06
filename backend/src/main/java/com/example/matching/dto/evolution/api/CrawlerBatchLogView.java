package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫推送批次日志视图（前端「最近接收批次」用）。
 *
 * @author system
 */
@Data
public class CrawlerBatchLogView {

    private Long id;

    private String batchNo;

    /**
     * 入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT。
     * <p>
     * 批次列表的分类依据——爬虫推来的与人工上传的必须能一眼分开。
     */
    private String ingestChannel;

    /** 通道中文名，由后端给出，避免前端维护第二份映射 */
    private String ingestChannelText;

    private String sourcePlatform;

    private String requestIp;

    private Integer itemCount;

    private Integer imported;

    private Integer updated;

    private Integer duplicate;

    private Integer failed;

    private Integer httpStatus;

    /** OK / PARTIAL_FAILED / REJECTED / UNAUTHORIZED */
    private String resultStatus;

    /** NOT_TRIGGERED / QUEUED / RUNNING / SUCCEEDED / FAILED / SKIPPED */
    private String analysisState;

    private String analysisNote;

    private Integer costMillis;

    private LocalDateTime createdTime;

    /** 前端展示用的中文结论，避免前端维护映射表时与后端口径漂移 */
    private String resultStatusText;

    /** 前端展示用的中文解析状态 */
    private String analysisStateText;
}
