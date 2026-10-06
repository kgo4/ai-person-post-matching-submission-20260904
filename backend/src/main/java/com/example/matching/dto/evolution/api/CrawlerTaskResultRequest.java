package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 本地爬虫回传的任务结果（上游对接文档 §9）。
 * <p>
 * 爬虫执行完命令（或本地抓取失败）后调用，主系统据此把命令推进到
 * {@code RUNNING} / {@code SUCCEEDED} / {@code FAILED}。
 * 注意：抓取到的 JD **不**通过本接口传输，仍走
 * {@code POST /api/internal/market-jd/crawler-import}，两条链路职责分离。
 *
 * @author system
 */
@Data
public class CrawlerTaskResultRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例标识，必填 */
    private String agentId;

    /** 命令ID，必填 */
    private String commandId;

    /** RUNNING / SUCCEEDED / FAILED */
    private String status;

    /** 本地任务ID，便于双方排障对齐 */
    private String taskId;

    /** 已推送成功的 JD 条数 */
    private Integer pushedCount;

    /** 推送失败条数 */
    private Integer failedCount;

    /** 失败原因或结果说明 */
    private String message;
}
