package com.example.matching.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PromptMetadataResolver {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^# prompt-version: (v\\d+\\.\\d+)$");

    private final ResourceLoader resourceLoader;

    public PromptMetadataResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 名称 → 候选资源文件名。
     *
     * <p>为什么需要「候选列表」而不是直接拿 `name + ".txt"` 去读：
     * 提示词资源同时存在两套命名约定 ——
     * <ul>
     *   <li>{@code xxx-system.txt}：走 {@code LangChain4jChatService.chat("xxx", ...)} 的 system 文本；</li>
     *   <li>{@code xxx-prompt.ftl}：走 {@code PromptTemplateService.render("xxx-prompt", ...)} 的 FreeMarker 模板。</li>
     * </ul>
     * 而调用方传进来的 {@code name} 是**裸名**（如 {@code gap-diagnosis}）。
     * 原先只拼 {@code name + ".txt"}，于是除个别恰好有同名 txt 的外，
     * 一律落进 catch 返回 {@code "unknown"} —— 运行审计页的「提示词版本」整列 unknown 就是这么来的。
     *
     * <p>顺序即优先级：`-system.txt` 在前（与 chat() 埋点同源），其后是两种 ftl 命名。
     * 首个存在的即采用；全不存在才报错。
     */
    private static String[] candidatesFor(String name) {
        String base = name.replaceAll("\\.(txt|ftl)$", "");
        return new String[] {
                base + "-system.txt",
                base + "-prompt.ftl",
                base + ".ftl",
                base + ".txt",
        };
    }

    public PromptMetadata resolve(String resourceName) {
        try {
            Resource res = null;
            for (String candidate : candidatesFor(resourceName)) {
                Resource r = resourceLoader.getResource("classpath:/ai/prompt/" + candidate);
                if (r.exists()) {
                    res = r;
                    break;
                }
            }
            if (res == null) {
                throw new IllegalArgumentException("Prompt resource not found: " + resourceName
                        + "（已尝试 " + String.join(", ", candidatesFor(resourceName)) + "）");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String firstLine = reader.readLine();
                if (firstLine == null || firstLine.isBlank()) {
                    throw new IllegalArgumentException("Prompt resource has no version header: " + resourceName);
                }
                Matcher matcher = VERSION_PATTERN.matcher(firstLine);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException(
                            "Prompt resource missing valid version header in first line: " + resourceName);
                }
                String version = matcher.group(1);
                String name = resourceName.replaceAll("\\.(txt|ftl)$", "");
                return new PromptMetadata(name, version);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read prompt metadata: " + resourceName, e);
        }
    }

    public record PromptMetadata(String name, String version) {
    }
}
