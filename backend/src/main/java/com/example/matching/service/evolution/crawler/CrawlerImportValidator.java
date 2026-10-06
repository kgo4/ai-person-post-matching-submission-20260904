package com.example.matching.service.evolution.crawler;

import com.example.matching.dto.evolution.api.CrawlerMarketJdImportRequest;
import com.example.matching.dto.evolution.api.CrawlerMarketJdItem;

/**
 * 爬虫推送请求的校验规则（纯函数，无 Spring 依赖，便于单测）。
 * <p>
 * 对齐对接文档 §3.4：{@code items[i].xxx} 式错误消息；同时区分两种失败语义：
 * <ul>
 *   <li>{@link #validateBatch} —— 整批 400 拒绝：缺批次标识、超单批上限、某条连唯一键都构不齐；</li>
 *   <li>{@link #validateItem} —— 单条 failed：唯一键成立但正文为空，跳过该条且不回滚整批。</li>
 * </ul>
 * <p>
 * {@code sourcePlatform} 只校验非空、不限定取值：文档 §5 的 {@code jd/remoteok/themuse/arbeitnow}
 * 是当前爬虫来源，早期推送方使用的 {@code BOSS/LAGOU} 等历史值必须继续兼容
 * （{@code source_platform} 参与唯一键，强行归一化会让存量数据失配）。
 *
 * @author system
 */
public final class CrawlerImportValidator {

    private CrawlerImportValidator() {
    }

    /**
     * 整批校验。
     *
     * @return {@code null} 表示整批可进入逐条处理；否则返回可直接回给爬虫的错误消息
     */
    public static String validateBatch(CrawlerMarketJdImportRequest request, int maxItems) {
        if (request == null) {
            return "请求体不能为空";
        }
        if (isBlank(request.getBatchNo())) {
            return "batchNo 不能为空";
        }
        if (isBlank(request.getSourcePlatform())) {
            return "sourcePlatform 不能为空";
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return "items 不能为空";
        }
        if (request.getItems().size() > maxItems) {
            return "items 数量不能超过 " + maxItems + "，当前 " + request.getItems().size() + " 条";
        }
        for (int index = 0; index < request.getItems().size(); index++) {
            CrawlerMarketJdItem item = request.getItems().get(index);
            String prefix = "items[" + index + "].";
            if (item == null) {
                return prefix + "不能为空";
            }
            if (isBlank(item.getPostName())) {
                return prefix + "jobTitle 不能为空";
            }
            if (isBlank(item.getExternalId())
                    && (isBlank(item.getCompanyName()) || isBlank(item.getCity()))) {
                return prefix + "externalId 不能为空（或补齐 jobTitle、companyName、city 作为退化唯一键）";
            }
        }
        return null;
    }

    /**
     * 单条校验。
     *
     * @return {@code null} 表示该条可落库；否则返回该条 failed 的原因
     */
    public static String validateItem(int index, CrawlerMarketJdItem item) {
        if (item == null) {
            return "items[" + index + "] 不能为空";
        }
        if (isBlank(item.getJobDescription()) && isBlank(item.getRequirements())) {
            return "items[" + index + "].jobDescription 与 items[" + index + "].requirements 不能同时为空";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
