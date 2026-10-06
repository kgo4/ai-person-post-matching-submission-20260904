package com.example.matching.service.evolution.crawler;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;

/**
 * 市场 JD 文本派生字段的统一口径。
 * <p>
 * 抽成纯静态工具的原因：{@code MarketJdImportServiceImpl}（文本/Excel 导入链路）与
 * {@code CrawlerJdItemWriter}（爬虫推送链路）必须对同一段正文算出**完全一致**的
 * {@code text_hash} / {@code content_hash} / 质量分，否则去重与「正文是否变化」判定会互相打架。
 * <p>
 * 两种哈希职责分离：
 * <ul>
 *   <li>{@link #textHash(String)} —— 既有口径（含岗位名与公司名），服务跨批次精确去重与
 *       SimHash 近似去重，历史值不可变；</li>
 *   <li>{@link #contentHash(String, String)} —— 爬虫对接文档 §4.2 口径，只用于判定
 *       「同源岗位（source_platform + external_id）的正文是否变化」。</li>
 * </ul>
 *
 * @author system
 */
public final class MarketJdTextMetrics {

    /** 爬虫推送数据的内容分类，对齐对接文档 §5。 */
    public static final String CONTENT_CATEGORY_RECRUITMENT_JD = "RECRUITMENT_JD";

    private MarketJdTextMetrics() {
    }

    /**
     * 所有哈希的稳定化预处理：NFKC 兼容归一 + 连续空白折叠为单空格 + 去首尾空白 + 小写。
     * <p>
     * 因此「Java\n  Spring Boot」与「 java Spring   Boot 」得到同一个哈希。
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** SHA-256 十六进制小写。入参必须已是 {@link #normalize(String)} 结果。 */
    public static String sha256Hex(String normalizedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 既有 {@code text_hash} 口径：对整段文本做归一后取 SHA-256。 */
    public static String textHash(String text) {
        return sha256Hex(normalize(text));
    }

    /**
     * 结构化 JD 的 {@code text_hash}（去重键）口径：岗位名 + 公司名 + 描述 + 任职要求。
     * <p>
     * <b>这是全仓唯一的结构化去重键定义，不要在别处再拼一遍。</b>
     * 这里踩过真实的坑：{@code RecruitmentDataGovernanceServiceImpl} 曾经在治理阶段把
     * {@code text_hash} 覆写成「只有描述 + 要求、且未归一化」的哈希，于是
     * <ul>
     *   <li>岗位名与公司名从去重键里消失 → 不同岗位/不同公司只要正文一样就被判为重复；</li>
     *   <li>与写入侧（{@link #textHash(String)}）口径不同 → 同一列混着两种算法的值，
     *       {@code deduplicateByBatch} 的跨批次比较直接对不上；</li>
     * </ul>
     * 用户看到的现象就是「推来的数据全被标成重复跳过，但业务上并不重复」。
     * <p>
     * 拼接用 {@code "\n"} 而不是直接相连：{@code ("AB","C")} 与 {@code ("A","BC")} 若直接相连会得到
     * 同一个字符串，产生边界歧义导致的假重复。
     */
    public static String dedupeKey(String postName, String companyName, String jobDescription, String requirements) {
        return textHash(safe(postName) + "\n" + safe(companyName) + "\n"
                + safe(jobDescription) + "\n" + safe(requirements));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 正文内容哈希：{@code SHA-256(jobDescription + "\n" + requirements)}
     * （对齐爬虫对接文档 §4.2，多一步 NFKC/空白/大小写归一以避免假变更）。
     */
    public static String contentHash(String jobDescription, String requirements) {
        return sha256Hex(normalize(jobDescription) + "\n" + normalize(requirements));
    }

    /**
     * 招聘主体匿名稳定键，仅用于跨主体去重统计。
     * <p>
     * 公式与历史值保持一致（前缀先与公司名拼接后再整体归一），改动会让存量
     * {@code company_diversity_key} 与新值不可比。
     */
    public static String anonymousCompanyDiversityKey(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "";
        }
        return sha256Hex(normalize("market-jd-employer-v1:" + normalize(companyName)));
    }

    /** 完整 JD 正文（描述 + 任职要求），供质量评分、SimHash 近似去重与清洗使用。 */
    public static String buildFullJdText(String jobDescription, String requirements) {
        StringBuilder builder = new StringBuilder();
        if (jobDescription != null && !jobDescription.isBlank()) {
            builder.append(jobDescription);
        }
        if (requirements != null && !requirements.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("任职要求：\n").append(requirements);
        }
        return builder.toString();
    }

    /** JD 质量分（0–100），口径与历史实现完全一致。 */
    public static BigDecimal qualityScore(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            return BigDecimal.ZERO;
        }
        double score = 50.0;
        if (jdText.length() > 200) {
            score += 10;
        }
        if (jdText.length() > 500) {
            score += 10;
        }
        String lowerText = jdText.toLowerCase();
        if (lowerText.contains("要求") || lowerText.contains("职责")) {
            score += 10;
        }
        if (lowerText.contains("经验") || lowerText.contains("技能")) {
            score += 5;
        }
        if (lowerText.contains("学历") || lowerText.contains("本科")) {
            score += 5;
        }
        return BigDecimal.valueOf(Math.min(100, score));
    }
}
