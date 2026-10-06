package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 「爬虫推送批次是否自动解析」开关视图。
 * <p>
 * 前端拿 {@code enabled} 渲染开关状态，拿 {@code overridden} 决定是否显示
 * 「重启后回到配置默认值」的常驻提示 —— 运行期开关**不落库**，
 * 若界面把它画成一个普通持久开关，运维会误以为改一次就长期生效。
 *
 * @author system
 */
@Data
public class CrawlerAutoAnalyzeView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前生效值：true = 推来即自动解析，false = 只入库等人工解析 */
    private boolean enabled;

    /** 部署时的默认值（环境变量 MARKET_JD_CRAWLER_AUTO_ANALYZE_ENABLED 决定） */
    private boolean configuredDefault;

    /** 当前值是否来自运行期临时覆盖（true 表示重启会回落） */
    private boolean overridden;

    /** 中文提示语，由后端给出，避免前端再拼一套口径 */
    private String message;
}
