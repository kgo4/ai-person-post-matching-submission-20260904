package com.example.matching.agent.json;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

/**
 * 第 1 层统一 JSON 输出契约注入器（AiService 通路）。
 * <p>
 * 12 个 AiService 的 @SystemMessage 由 langchain4j 框架从 .txt 加载，不经
 * PromptTemplateService.render()，因此统一契约段（json_contract.ftl）无法随 render 生效。
 * 本组件通过 AiServices.systemMessageTransformer(...) 在框架加载 system 消息后统一追加
 * 契约段 + 通用 few-shot，与 render 通路（PromptTemplateService）共享同一份 json_contract.ftl。
 * 开关：ai.json.contract.enabled（默认 true），与 render 通路一致。
 */
@Slf4j
@Component
public class JsonContractInjector {

    private final ResourceLoader resourceLoader;
    private final boolean enabled;

    private volatile String contractSnippet;
    private volatile String fewShotSnippet;

    public JsonContractInjector(ResourceLoader resourceLoader,
                                @Value("${ai.json.contract.enabled:true}") boolean enabled) {
        this.resourceLoader = resourceLoader;
        this.enabled = enabled;
    }

    /** 供 AiServices.builder().systemMessageTransformer(...) 使用：在原 system 消息后追加契约段 + few-shot。 */
    public UnaryOperator<String> systemMessageTransformer() {
        return original -> apply(original, "GENERAL");
    }

    /** 场景化契约：保留公共 JSON 防护，同时追加该业务 Agent 的专属示例。 */
    public UnaryOperator<String> systemMessageTransformer(String scene) {
        return original -> apply(original, scene);
    }

    private String apply(String original, String scene) {
        if (!enabled) {
            return original;
        }
        return original + "\n\n" + contractSnippet() + "\n\n" + JsonFewShotRegistry.forScene(scene);
    }

    private String contractSnippet() {
        if (contractSnippet == null) {
            try {
                Resource resource = resourceLoader.getResource("classpath:templates/json_contract.ftl");
                contractSnippet = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("加载 json_contract.ftl 失败，契约段跳过: {}", e.getMessage());
                contractSnippet = "";
            }
        }
        return contractSnippet;
    }

}
