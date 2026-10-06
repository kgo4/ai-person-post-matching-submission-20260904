package com.example.matching.service.rag;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 来源目录。所有来源到知识层的默认映射集中在这里，避免在业务 Agent 中散落字符串判断。
 */
public final class RagSourceCatalog {

    private static final Map<String, RagKnowledgeLayer> SOURCE_LAYERS;

    static {
        Map<String, RagKnowledgeLayer> mappings = new LinkedHashMap<>();
        // 结构化主数据/事实
        put(mappings, RagKnowledgeLayer.FACT,
                "POST_ABILITY_MODEL", "ABILITY_TAG", "JD_IMPORT", "EMP_ABILITY",
                "POST_PROTOTYPE", "MATCHING_FACT", "POST_FACT", "MARKET_JD",
                "RECRUITMENT_JD", "MANUAL_JD", "POST_DESCRIPTION",
                "POST_TEMPLATE", "MANUAL_POST_MODEL", "INTERNAL_POST_REQUIREMENT");
        // 人员评估、审核和业务追溯证据
        put(mappings, RagKnowledgeLayer.EVIDENCE,
                "CONTEST_EVIDENCE", "EMP_EVIDENCE", "INTERVIEW_EVIDENCE",
                "ASSESSMENT_EVIDENCE", "EVIDENCE", "EVIDENCE_TRACE");
        // 稳定领域知识、学习资料和内部知识
        put(mappings, RagKnowledgeLayer.DOMAIN,
                "LEARNING_RESOURCE", "KNOWLEDGE_DOC", "MANUAL_TEXT",
                "CLOUD_KNOWLEDGE_INTERNAL", "INTERNAL_KNOWLEDGE", "DOMAIN_KNOWLEDGE");
        // 政策、标准、白皮书和外部趋势
        put(mappings, RagKnowledgeLayer.TREND,
                "POLICY_DOCUMENT", "OFFICIAL_POLICY", "OFFICIAL_DOCUMENT",
                "OCCUPATION_STANDARD", "INDUSTRY_WHITEPAPER", "INDUSTRY_REPORT",
                "MARKET_REPORT", "ZHIHU_TREND", "EXTERNAL_TREND", "MARKET_TREND", "TREND");
        SOURCE_LAYERS = Collections.unmodifiableMap(mappings);
    }

    private RagSourceCatalog() {
    }

    private static void put(Map<String, RagKnowledgeLayer> mappings,
                            RagKnowledgeLayer layer, String... sourceTypes) {
        Arrays.stream(sourceTypes).forEach(sourceType -> mappings.put(sourceType, layer));
    }

    public static RagKnowledgeLayer resolveLayer(String sourceType) {
        return resolveLayer(sourceType, Map.of());
    }

    /**
     * 云知识库命中必须携带业务来源或 knowledgeLayer，否则无法安全判断归属层。
     */
    public static RagKnowledgeLayer resolveLayer(String sourceType, Map<String, ?> metadata) {
        String explicitLayer = stringValue(metadata, "knowledgeLayer", "knowledge_layer");
        if (explicitLayer != null) {
            try {
                return RagKnowledgeLayer.valueOf(explicitLayer.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 继续使用来源目录推断
            }
        }

        String effectiveSourceType = sourceType;
        if ("VOLCENGINE_KB".equalsIgnoreCase(sourceType)) {
            effectiveSourceType = stringValue(metadata,
                    "originSourceType", "origin_source_type", "sourceType", "source_type");
            if (effectiveSourceType == null) {
                return null;
            }
        }
        if (effectiveSourceType == null || effectiveSourceType.isBlank()) {
            return null;
        }
        return SOURCE_LAYERS.get(effectiveSourceType.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isAllowed(String sourceType, Map<String, ?> metadata,
                                    Set<RagKnowledgeLayer> allowedLayers) {
        if (allowedLayers == null || allowedLayers.isEmpty()) {
            return false;
        }
        RagKnowledgeLayer layer = resolveLayer(sourceType, metadata);
        return layer != null && allowedLayers.contains(layer);
    }

    public static Set<String> sourceTypesForLayers(Set<RagKnowledgeLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return Set.of();
        }
        return SOURCE_LAYERS.entrySet().stream()
                .filter(entry -> layers.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> knownSourceTypes() {
        return SOURCE_LAYERS.keySet();
    }

    public static Set<RagKnowledgeLayer> allLayers() {
        return EnumSet.allOf(RagKnowledgeLayer.class);
    }

    private static String stringValue(Map<String, ?> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
