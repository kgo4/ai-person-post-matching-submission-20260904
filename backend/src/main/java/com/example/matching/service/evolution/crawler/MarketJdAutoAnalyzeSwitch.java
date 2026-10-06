package com.example.matching.service.evolution.crawler;

import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.dto.evolution.api.CrawlerAutoAnalyzeView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 「爬虫推送的批次是否自动解析」的运行期开关。
 *
 * <h2>为什么不是直接改 {@code MarketJdCrawlerProperties}</h2>
 * 配置项 {@code market-jd.crawler.auto-analyze-enabled} 的默认值已改为 <b>false（默认手动）</b>：
 * 爬虫按自己的计划推送，自动解析会在无人值守时消耗 LLM 配额，并把能力提取 / 准入结论直接写库，
 * 运营没有机会「先看数据、再决定跑不跑」。
 * <p>
 * 但「一律手动」又太死板：旺季数据量大时运营会希望推来即解析。于是把开关拆成两层：
 * <ul>
 *   <li><b>配置层</b>（{@link MarketJdCrawlerProperties#isAutoAnalyzeEnabled()}）—— 部署时的默认值，
 *       由环境变量 {@code MARKET_JD_CRAWLER_AUTO_ANALYZE_ENABLED} 决定；</li>
 *   <li><b>运行期层</b>（本类）—— 管理端在页面上临时切换，<b>不落库、重启后回落到配置层</b>。</li>
 * </ul>
 * 之所以刻意不落库：{@code sys_config} 一类配置表在本仓库不存在，为单个开关新增 schema 的代价
 * （生产是手工建库、Flyway 已停用）远大于收益；「重启回落」也让线上不会被一个误点的开关长期带偏。
 * 需要长期打开时应当改环境变量，而不是依赖页面开关。
 *
 * <h2>只读语义</h2>
 * 本类只回答「现在要不要自动解析」，不触发解析、不感知批次；触发逻辑仍在
 * {@code CrawlerMarketJdIngestService}（入库编排）与 {@code MarketJdBatchAnalysisListener}（异步执行）。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketJdAutoAnalyzeSwitch {

    private final MarketJdCrawlerProperties properties;

    /**
     * 运行期覆盖值；{@code null} = 跟随配置。
     * <p>
     * 用 {@code volatile} 而不是 {@code AtomicBoolean}：这里只有一个布尔读写在跨线程可见性上的要求，
     * 没有「比较并交换」的语义；{@code null} 还必须能被表达，而 {@code AtomicBoolean} 表达不了三态。
     */
    private volatile Boolean override;

    /** 当前是否自动解析（运行期覆盖优先，否则跟随配置）。 */
    public boolean isEnabled() {
        Boolean current = override;
        return current != null ? current : properties.isAutoAnalyzeEnabled();
    }

    /** 配置层默认值（环境变量决定的那个），用于界面区分「跟随配置」与「本次临时覆盖」。 */
    public boolean isConfiguredDefault() {
        return properties.isAutoAnalyzeEnabled();
    }

    /** 是否处于运行期临时覆盖状态（重启会丢，界面需要显式提示）。 */
    public boolean isOverridden() {
        return override != null;
    }

    /**
     * 设置运行期开关。
     *
     * @param enabled true = 推来即自动解析；false = 只入库，等人工解析
     */
    public void apply(boolean enabled) {
        Boolean previous = override;
        // 与配置层一致时清掉覆盖，让界面回到「跟随配置」而不是显示成一次临时改动
        override = enabled == properties.isAutoAnalyzeEnabled() ? null : enabled;
        log.info("爬虫批次自动解析开关变更: {} -> {}, 配置默认={}, 覆盖={}",
                previous != null ? previous : properties.isAutoAnalyzeEnabled(),
                enabled, properties.isAutoAnalyzeEnabled(), override != null);
    }

    /** 清掉运行期覆盖，回到配置层默认值。 */
    public void resetToConfiguredDefault() {
        override = null;
        log.info("爬虫批次自动解析开关已恢复为配置默认值: {}", properties.isAutoAnalyzeEnabled());
    }

    /**
     * 组装给前端的状态视图。
     * <p>
     * 中文说明由后端给出（前端不再拼第二份口径）；**必须说清「临时 vs 配置」**：
     * 运行期覆盖不落库，如果界面把它画成一个普通持久开关，运维改完会以为长期生效。
     */
    public CrawlerAutoAnalyzeView toView() {
        boolean enabled = isEnabled();
        boolean overridden = isOverridden();
        CrawlerAutoAnalyzeView view = new CrawlerAutoAnalyzeView();
        view.setEnabled(enabled);
        view.setConfiguredDefault(isConfiguredDefault());
        view.setOverridden(overridden);
        String base = enabled
                ? "推送的批次入库后会立即进入解析链路（治理 → 去重 → 能力提取 → 准入）。"
                : "推送的批次只入库、不解析；需要解析时请在批次行点「重新解析」，或到市场 JD 池按批次解析。";
        view.setMessage(overridden
                ? base + "当前为页面上的临时开关，服务重启后回到配置默认值（"
                        + (isConfiguredDefault() ? "开启" : "关闭") + "）。"
                : base);
        return view;
    }
}
