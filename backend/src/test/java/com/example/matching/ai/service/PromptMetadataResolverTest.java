package com.example.matching.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptMetadataResolverTest {

    private PromptMetadataResolver resolver;

    @BeforeEach
    void setUp() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        resolver = new PromptMetadataResolver(resourceLoader);

        when(resourceLoader.getResource(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.contains("valid.ftl")) {
                return new ByteArrayResource("# prompt-version: v1.5\ncontent".getBytes());
            } else if (path.contains("valid.txt")) {
                return new ByteArrayResource("# prompt-version: v2.1\ncontent".getBytes());
            } else if (path.contains("no-version.ftl")) {
                return new ByteArrayResource("no version header".getBytes());
            } else if (path.contains("empty.txt")) {
                return new ByteArrayResource("".getBytes());
            } else if (path.contains("old-format.ftl")) {
                return new ByteArrayResource("<#-- prompt-version: v3.0 -->\ncontent".getBytes());
            } else {
                return new ByteArrayResource("# prompt-version: v1.0\ndefault".getBytes());
            }
        });
    }

    @Test
    void resolvesVersionFromFtlFile() {
        PromptMetadataResolver.PromptMetadata meta = resolver.resolve("valid.ftl");
        assertThat(meta.name()).isEqualTo("valid");
        assertThat(meta.version()).isEqualTo("v1.5");
    }

    @Test
    void resolvesVersionFromTxtFile() {
        PromptMetadataResolver.PromptMetadata meta = resolver.resolve("valid.txt");
        assertThat(meta.name()).isEqualTo("valid");
        assertThat(meta.version()).isEqualTo("v2.1");
    }

    @Test
    void rejectsLegacyFreemarkerCommentFormat() {
        assertThatThrownBy(() -> resolver.resolve("old-format.ftl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing valid version header");
    }

    @Test
    void throwsOnMissingHeader() {
        assertThatThrownBy(() -> resolver.resolve("no-version.ftl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing valid version header");
    }

    @Test
    void throwsOnEmptyFile() {
        assertThatThrownBy(() -> resolver.resolve("empty.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no version header");
    }
}
