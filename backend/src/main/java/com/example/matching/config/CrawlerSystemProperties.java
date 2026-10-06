package com.example.matching.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 独立爬虫系统对接配置。
 * <p>
 * 爬虫系统提供 {@code /api/crawler/**} 接口，主系统只做代理转发，不落任何爬虫业务数据。
 * <p>
 * <b>爬虫是可选外部系统</b>：未部署爬虫时，主系统启动、运行与既有 JD 解析链路均不受影响，
 * 只是爬虫代理接口统一返回 503。为避免误会，这里在启动期对三种配置组合做显式日志。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "crawler-system")
public class CrawlerSystemProperties {

    /** 爬虫系统基础地址，如 http://127.0.0.1:8081；为空表示未接入，代理接口直接返回不可用 */
    private String baseUrl = "";

    /** 调用爬虫系统的鉴权令牌，可为空（爬虫侧当前未强制校验） */
    private String apiToken = "";

    /** 连接超时（毫秒） */
    private int connectTimeoutMillis = 3000;

    /** 读超时（毫秒）：触发爬取只排队，读超时可短一些 */
    private int readTimeoutMillis = 10000;

    /**
     * 是否启用爬虫代理接口。
     * <p>
     * 默认 {@code false}：未显式配置 {@code CRAWLER_SYSTEM_URL} 时不启用代理，
     * 避免"组件看似存在、一调就失败"的误导状态。
     */
    private boolean enabled = false;

    /**
     * 过渡期反向代理总开关，默认 {@code false}。
     * <p>
     * 爬虫对接文档 {@code MAIN_SYSTEM_INTEGRATION.md} §1.1 明确「主系统不能、也不需要访问本地电脑」，
     * 因此 {@code /trigger}、{@code /task/{id}}、{@code /task/{id}/retry-push} 三个反向代理端点
     * 默认关闭并返回 {@code 410 GONE}，抓取改由「管理端创建命令 → 本地爬虫轮询领取」驱动。
     * <p>
     * 仅在本地爬虫尚未实现命令轮询时临时置 {@code true} 作为兼容，并应尽快关闭。
     */
    private boolean legacyProxyEnabled = false;

    /** 启动期自检：把配置状态一次说清，避免运行时才发现代理不可用。 */
    @PostConstruct
    void logConfigurationState() {
        boolean hasBaseUrl = baseUrl != null && !baseUrl.isBlank();
        if (!legacyProxyEnabled) {
            log.info("反向代理已关闭（默认，符合爬虫对接文档 §1.1）：采集通过采集命令 + 本地爬虫轮询完成");
        } else {
            log.warn("反向代理已启用（过渡兼容，请尽快关闭）：主系统将可能反向访问本地爬虫地址 {}。"
                    + "上游验收项 6 要求主系统全程不访问本地电脑地址，请改用采集命令（本地爬虫主动轮询）。",
                    hasBaseUrl ? baseUrl : "<未配置>");
        }
        if (enabled && hasBaseUrl) {
            log.info("爬虫代理已启用: base-url={}", baseUrl);
        } else if (enabled) {
            log.warn("爬虫代理已启用但未配置 crawler-system.base-url，代理接口将返回 503；"
                    + "如暂未部署爬虫，请设置 CRAWLER_SYSTEM_ENABLED=false");
        } else if (hasBaseUrl) {
            log.warn("已配置 crawler-system.base-url={} 但代理未启用（CRAWLER_SYSTEM_ENABLED=false），"
                    + "代理接口将返回 503", baseUrl);
        } else {
            log.info("爬虫代理未启用（未部署爬虫属正常状态），市场 JD 接收接口不受影响");
        }
    }

    /**
     * 爬虫代理是否真正可用：需同时启用且配置了地址。
     */
    public boolean isAvailable() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * 过渡期反向代理是否可用：开关打开且地址已配置。
     * 关闭（默认）时旧端点统一返回 410，提示改用采集命令。
     */
    public boolean isLegacyProxyUsable() {
        return legacyProxyEnabled && isAvailable();
    }
}

