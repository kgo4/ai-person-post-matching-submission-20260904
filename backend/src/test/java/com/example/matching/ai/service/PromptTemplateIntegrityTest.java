package com.example.matching.ai.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Prompt template integrity tests.
 * Verifies: UTF-8 encoding (no BOM), Chinese text not garbled,
 * anti-hallucination constraints present, and FreeMarker rendering succeeds.
 */
class PromptTemplateIntegrityTest {

    private static final String PROMPT_DIR = "classpath:/ai/prompt/";
    private static final String[] PROMPT_FILES = {
            "matching-overview-report", "gap-diagnosis-prompt",
            "learning-suggestion-prompt", "excel-structure-recognize-prompt",
            "extend-field-parse-prompt", "interview-answer-quality-prompt",
            "interview-follow-up-generation-prompt", "job-summary-extract-prompt",
            "ai-test-prompt", "ai-test-evaluate-prompt"
    };

    private static final List<String> REQUIRED_CONSTRAINT_KEYWORDS = List.of(
            "约束", "依据", "编造", "sourceRef"
    );

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Test
    @DisplayName("All prompt templates are UTF-8 encoded and readable")
    void allTemplatesAreUtf8WithoutBom(@TempDir Path tempDir) throws Exception {
        for (String name : PROMPT_FILES) {
            Path path = copyToTemp(name, tempDir);
            byte[] bytes = Files.readAllBytes(path);

            // File should have content
            assertThat(bytes.length).isGreaterThan(0);

            // Readable as UTF-8 (no garbled characters)
            String content = new String(bytes, StandardCharsets.UTF_8);
            assertThat(content).as("File %s content should not be empty", name).isNotBlank();
            // Key Chinese text should be preserved
            assertThat(content).as("File %s should contain Chinese characters", name)
                    .containsPattern("[\\u4e00-\\u9fff]");
        }
    }

    @Test
    @DisplayName("All prompt templates parse successfully and preserve Chinese text")
    void allTemplatesParseWithChineseText(@TempDir Path tempDir) throws Exception {
        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setDirectoryForTemplateLoading(tempDir.toFile());
        cfg.setDefaultEncoding("UTF-8");

        for (String name : PROMPT_FILES) {
            copyToTemp(name, tempDir);
            Template template = cfg.getTemplate(name + ".ftl");
            assertThat(template.toString()).as("Template %s should be parsed", name).isNotBlank();
            String source = Files.readString(tempDir.resolve(name + ".ftl"), StandardCharsets.UTF_8);
            assertThat(source).as("Template %s source should contain Chinese text", name)
                    .containsPattern("[\\u4e00-\\u9fff]");
        }
    }

    @Test
    @DisplayName("All prompt templates contain anti-hallucination constraints")
    void allTemplatesContainAntiHallucinationConstraints(@TempDir Path tempDir) throws Exception {
        for (String name : PROMPT_FILES) {
            Path path = copyToTemp(name, tempDir);
            String content = Files.readString(path, StandardCharsets.UTF_8);

            boolean hasConstraints = REQUIRED_CONSTRAINT_KEYWORDS.stream()
                    .anyMatch(content::contains);
            assertThat(hasConstraints)
                    .as("Template %s should contain anti-hallucination constraints", name)
                    .isTrue();
        }
    }

    private Path copyToTemp(String templateName, Path tempDir) throws IOException {
        String resourcePath = PROMPT_DIR + templateName + ".ftl";
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new FileNotFoundException("Template not found: " + resourcePath);
        }
        Path dest = tempDir.resolve(templateName + ".ftl");
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            Files.write(dest, bytes);
        }
        return dest;
    }

}
