package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采集通道可用性视图。
 * <p>
 * 前端据此决定「立即抓取」按钮是否可点，以及是否展示「本地爬虫未上线」提示。
 * {@code legacyProxyEnabled} 用于提示当前仍处于反向代理过渡期（上游验收项 6 要求默认关闭）。
 *
 * @author system
 */
@Data
public class CrawlerAvailabilityView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命令队列是否可用（等价于已配置 api-key 且命令表可写） */
    private boolean queueAvailable;

    /** 在线爬虫实例数 */
    private int agentsOnline;

    /** 全部实例中最近一次心跳时间 */
    private LocalDateTime lastHeartbeatTime;

    /** 过渡期反向代理开关（默认 false；true 表示仍可能反向访问本地地址） */
    private boolean legacyProxyEnabled;
}
