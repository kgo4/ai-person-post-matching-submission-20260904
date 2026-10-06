package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地爬虫实例在线状态视图（管理端「爬虫是否在线」用）。
 *
 * @author system
 */
@Data
public class CrawlerAgentView implements Serializable {

    private static final long serialVersionUID = 1L;

    private String agentId;

    private String agentName;

    private String hostInfo;

    private String agentVersion;

    private LocalDateTime lastHeartbeatTime;

    /** 最近领取的命令ID */
    private String lastCommandId;

    /** 是否在 {@code market-jd.crawler.agent-online-threshold-seconds} 内上报过心跳 */
    private boolean online;

    /** 距上次心跳的秒数；从未上报时为 null */
    private Long secondsSinceHeartbeat;
}
