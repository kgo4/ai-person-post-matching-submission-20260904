package com.example.matching.agent.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第 3 层可选增强：logit_bias 字典级约束。
 * 仅对支持 logit_bias 的 OpenAI 兼容厂商生效；是否启用由配置 ai.json.logit-bias.enabled 控制，
 * 厂商不支持时由 CapabilityProbe/上层跳过，不阻断主链路。
 * 注：token 号依赖厂商分词器，'{'=123、反引号=12 为常见 OpenAI 兼容默认值；厂商差异由探测校准。
 */
public final class LogitBiasAdapter {

    private LogitBiasAdapter() {
    }

    public static Map<String, Integer> biasMap(boolean enabled) {
        if (!enabled) {
            return Map.of();
        }
        Map<String, Integer> bias = new LinkedHashMap<>();
        bias.put("123", 20);   // '{'
        bias.put("91", 15);    // '['
        bias.put("12", -20);   // '`'
        bias.put("40", -10);   // '('
        return bias;
    }
}
