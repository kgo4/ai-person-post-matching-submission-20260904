package com.example.matching.service.post.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizationPolicyTest {

    @Test
    void normalizesAllCommonLineBreakFormats() {
        assertThat(TextSanitizationPolicy.normalizeLineBreaks("a\r\nb\rc\nd"))
                .isEqualTo("a\nb\nc\nd");
        assertThat(TextSanitizationPolicy.normalizeLineBreaks(null)).isNull();
    }

    @Test
    void removesOnlyControlCharacters() {
        assertThat(TextSanitizationPolicy.removeControlChars("Java\u0000\u0007 AI\tEngine"))
                .isEqualTo("Java AI\tEngine");
        assertThat(TextSanitizationPolicy.removeControlChars(null)).isNull();
    }
}
