package com.example.matching.service.evolution.crawler;

import com.example.matching.dto.evolution.api.MarketJdDetailResponse;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 原始能力提取结果（{@code market_jd_data.ai_skill_tags}）的编解码。
 * <p>
 * <b>为什么需要单独一列：</b>解析链路上 AI 提取出的能力项要经过
 * 「与系统标签库匹配 → 准入门禁（新能力需跨 JD/跨公司双阈值）→ Harness 审核」多层过滤，
 * 只有全部通过的才会以 <b>标签ID</b> 形式写进 {@code skill_tags}。
 * 于是「匹配不上 / 未达阈值 / 未过审」的提取结果在库里<b>没有任何痕迹</b> ——
 * 用户看到的是「解析完成了，但一条结果都没有」，体感等同功能损坏。
 * <p>
 * 本列存的是 AI 提取的<b>原始输出</b>，只用于查看，<b>不参与任何下游计算</b>：
 * {@code skill_tags} 的语义（已准入标签 ID）与 4 个按 ID 解析它的下游
 * （列表接口 / 详情接口 / 演化 RAG 证据 / 岗位趋势发现）都保持原样。
 * <p>
 * 编解码都是容错的：编码失败返回 {@code null}（调用方不写该列，也绝不会把已有结果擦成空），
 * 解码失败返回空列表（历史脏数据不该让「结果查看」整页报错）。
 *
 * @author system
 */
public final class MarketJdAiSkillTagCodec {

    /**
     * 证据与推理的截断长度。
     * <p>
     * 一条 JD 常提取出 10–20 项能力，若原样保留 {@code evidence} / {@code reasoning}
     * 的全文，单行 JSON 会膨胀到几十 KB，池列表分页查询的传输与渲染成本都会上去。
     * 展示只需要「这句话支持了什么能力」，300 字足够，且截断处有省略标记不会被误读成完整原文。
     */
    private static final int TEXT_MAX = 300;

    private static final String ELLIPSIS = "…";

    private MarketJdAiSkillTagCodec() {
    }

    /**
     * 把 AI 提取的能力项编码成可入库的 JSON。
     *
     * @param items  AI 提取结果（可为 null / 空）
     * @param mapper Jackson 序列化器
     * @return JSON 数组字符串；{@code items} 为空时返回 {@code "[]"}（区分「跑过但没提取到」与「没跑过」），
     *         序列化本身失败时返回 {@code null}（调用方据此不覆盖库中已有值）
     */
    public static String encode(List<JdAbilityItemDTO> items, ObjectMapper mapper) {
        if (mapper == null) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (items != null) {
            for (JdAbilityItemDTO item : items) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", trimToNull(item.getSuggestedName()));
                row.put("techStack", trimToNull(item.getTechStack()));
                row.put("category", trimToNull(item.getTagCategory()));
                row.put("abilityType", trimToNull(item.getAbilityType()));
                row.put("level", item.getMinRequiredLevel());
                row.put("weight", item.getWeight());
                row.put("isCore", item.getIsCore());
                row.put("isRequired", item.getIsRequired());
                row.put("confidence", item.getConfidenceScore());
                row.put("matchStatus", trimToNull(item.getMatchStatus()));
                row.put("matchedTagId", item.getMatchedTagId());
                row.put("matchedTagName", trimToNull(item.getMatchedTagName()));
                row.put("similarityScore", item.getSimilarityScore());
                row.put("evidence", truncate(item.getEvidenceText()));
                row.put("reasoning", truncate(item.getReasoning()));
                rows.add(row);
            }
        }
        try {
            return mapper.writeValueAsString(rows);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 把 {@code ai_skill_tags} 反解成可读列表。
     * <p>
     * 容错三态：空 / {@code null} / 非法 JSON → 空列表；元素不是对象 → 跳过；
     * 字段缺失 → 该字段为 {@code null}（由前端决定怎么展示，绝不在这里编造默认值）。
     *
     * @param raw    库中原始 JSON
     * @param mapper Jackson 解析器
     * @return 反解后的能力项列表，顺序与入库一致
     */
    public static List<MarketJdDetailResponse.AiTag> decode(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank() || mapper == null) {
            return List.of();
        }
        String text = raw.trim();
        if ("null".equalsIgnoreCase(text)) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<MarketJdDetailResponse.AiTag> tags = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                tags.add(new MarketJdDetailResponse.AiTag(
                        textOrNull(node, "name"),
                        textOrNull(node, "techStack"),
                        textOrNull(node, "category"),
                        textOrNull(node, "abilityType"),
                        intOrNull(node, "level"),
                        decimalOrNull(node, "weight"),
                        intOrNull(node, "isCore"),
                        intOrNull(node, "isRequired"),
                        decimalOrNull(node, "confidence"),
                        textOrNull(node, "matchStatus"),
                        longOrNull(node, "matchedTagId"),
                        textOrNull(node, "matchedTagName"),
                        doubleOrNull(node, "similarityScore"),
                        textOrNull(node, "evidence"),
                        textOrNull(node, "reasoning")));
            }
            return tags;
        } catch (Exception ignored) {
            // 不是合法 JSON：当作「没有 AI 提取结果」，由调用方用 rawAiSkillTags 原文兜底展示
            return List.of();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.asInt();
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.asLong();
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.asDouble();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.decimalValue();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= TEXT_MAX ? trimmed : trimmed.substring(0, TEXT_MAX) + ELLIPSIS;
    }
}
