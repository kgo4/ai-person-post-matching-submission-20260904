package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * JD分析请求DTO
 */
@Data
@Schema(description = "JD智能分析请求，提交岗位JD文本，由AI分析提取所需能力项")
public class JdAnalyzeRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "岗位ID不能为空")
    @Schema(description = "岗位ID", example = "2001")
    private Long postId;

    @NotBlank(message = "JD文本不能为空")
    @Schema(description = "岗位JD原文内容，支持直接粘贴职位描述文本")
    private String jdText;
}
