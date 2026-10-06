package com.example.matching.service.post.impl;

import com.example.matching.dto.post.api.PostModelVersionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-07 测试：PostModelVersionResponse 新增 unmatchedAbilities 字段后保持向后兼容
 */
class PostModelVersionResponseBackwardCompatTest {

    @Test
    @DisplayName("API 返回字段向后兼容：PostModelVersionResponse 保留原有字段")
    void versionResponseKeepsOriginalFields() {
        PostModelVersionResponse response = new PostModelVersionResponse(
                1L, 10L, "v1", "JD_AI", "DRAFT", null, 0,
                BigDecimal.ZERO, "desc", null,
                LocalDateTime.now(), LocalDateTime.now(), null);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.itemCount()).isZero();
        assertThat(response.unmatchedAbilities()).isNull();
    }
}
