package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 从 JD 智能生成能力模型草稿请求（M18：替代 @RequestBody String 原始入参）
 */
@Schema(description = "从 JD 智能生成能力模型草稿请求")
public record PostModelGenerationFromJdDTO(

        @Schema(description = "JD 文本")
        @NotBlank(message = "JD 文本不能为空")
        String jdText
) {
}
