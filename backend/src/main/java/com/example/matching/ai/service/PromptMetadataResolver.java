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

    public PromptMetadata resolve(String resourceName) {
        try {
            Resource res = resourceLoader.getResource("classpath:/ai/prompt/" + resourceName);
            if (!res.exists()) {
                throw new IllegalArgumentException("Prompt resource not found: " + resourceName);
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
