package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * JD质量检测请求DTO
 */
@Data
@Schema(description = "JD质量检测请求")
public class JdQualityCheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "JD文本")
    private String jdText;

    @Schema(description = "能力要求数量")
    private Integer abilityCount;

    @Schema(description = "最高等级要求")
    private Integer maxLevel;
}
