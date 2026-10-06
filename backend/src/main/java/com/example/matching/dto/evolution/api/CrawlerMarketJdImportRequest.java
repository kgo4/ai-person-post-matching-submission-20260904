package com.example.matching.dto.evolution.api;

import lombok.Data;

import java.util.List;

/**
 * 内部批次契约：独立市场 JD 爬虫推送过来的一批岗位。
 * <p>
 * 字段与爬虫侧 {@code MAIN_SYSTEM_INTEGRATION.md} §3.2 一一对应。
 * <p>
 * ⚠️ 本 DTO **不使用 Bean Validation**：{@code /api/internal/market-jd/crawler-import} 的
 * 校验由 {@code CrawlerImportValidator} 统一负责，以便产出文档 §3.4 要求的
 * {@code items[0].externalId 不能为空} 式错误消息。请勿在该接口上补 {@code @Valid}，
 * 否则 Bean Validation 会抢先返回另一套文案。
 *
 * @author system
 */
@Data
public class CrawlerMarketJdImportRequest {

    /** 本次推送批次号；仅用于追踪批次，**不作唯一键**（文档 §4.2） */
    private String batchNo;

    /** 来源代码，如 {@code jd}、{@code remoteok}、{@code themuse}、{@code arbeitnow} */
    private String sourcePlatform;

    /** 岗位数组；空数组按业务错误 400 处理（文档 §3.2） */
    private List<CrawlerMarketJdItem> items;
}
