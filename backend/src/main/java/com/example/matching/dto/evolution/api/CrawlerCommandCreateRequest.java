package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端创建采集命令的请求体。
 * <p>
 * 对应上游对接文档 §9 的命令体：
 * <pre>
 * {
 *   "commandId": "cmd-20260904-001",   // 由主系统生成，不由前端传入
 *   "type": "COLLECT",
 *   "sources": ["jd", "remoteok"],
 *   "keywords": ["Java", "软件工程师"],
 *   "cities": ["北京", "上海"],
 *   "maxItems": 50
 * }
 * </pre>
 * 命令创建后进入 {@code PENDING}，由本地爬虫轮询领取；主系统**不会**反向访问本地地址。
 * <p>
 * 这里刻意不做 Bean Validation：校验文案由 {@code CrawlerCommandService} 统一产出中文消息，
 * 避免框架默认英文文案透出给业务用户。
 *
 * @author system
 */
@Data
public class CrawlerCommandCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源平台，如 jd / remoteok / themuse / arbeitnow */
    private List<String> sources;

    /** 关键词 */
    private List<String> keywords;

    /** 城市 */
    private List<String> cities;

    /** 单次抓取上限；为空时使用默认值 */
    private Integer maxItems;

    /** 指定执行该命令的爬虫实例；为空表示任意在线实例均可领取 */
    private String agentId;
}
