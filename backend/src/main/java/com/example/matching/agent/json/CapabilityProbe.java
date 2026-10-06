package com.example.matching.agent.json;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 2 层能力探测：判断当前模型支持哪档 JSON 输出约束。
 * 探测结果按模型实例缓存，避免每次请求重复探测。
 */
public final class CapabilityProbe {

    public enum Level { JSON_SCHEMA, JSON_OBJECT, NONE }

    private final Map<ChatModel, Level> cache = new ConcurrentHashMap<>();

    public Level probe(ChatModel model) {
        return cache.computeIfAbsent(model, this::doProbe);
    }

    private Level doProbe(ChatModel model) {
        Set<Capability> capabilities;
        try {
            capabilities = model.supportedCapabilities();
        } catch (RuntimeException e) {
            capabilities = Set.of();
        }
        if (capabilities.contains(Capability.RESPONSE_FORMAT_JSON_SCHEMA)) {
            return Level.JSON_SCHEMA;
        }
        // 无显式声明时默认假设支持 json_object（OpenAI 兼容基线），
        // 厂商不支持时由第 4 层校验兜底，不阻断主链路。
        return Level.JSON_OBJECT;
    }
}
