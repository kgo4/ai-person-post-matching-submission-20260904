package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 本地爬虫心跳请求体（上游对接文档 §9）。
 *
 * @author system
 */
@Data
public class CrawlerHeartbeatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例标识，必填 */
    private String agentId;

    /** 实例名称 */
    private String agentName;

    /** 主机信息（操作系统 / 内网地址等，仅用于运维展示） */
    private String hostInfo;

    /** 爬虫版本号 */
    private String version;
}
