package com.example.matching.dto.kg;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 图谱快照创建请求（M18：替代 @RequestBody String 原始入参）
 */
@Schema(description = "图谱快照创建请求")
public record GraphSnapshotCreateDTO(

        @Schema(description = "图谱 JSON 内容")
        @NotBlank(message = "图谱 JSON 内容不能为空")
        String graphJson
) {
}
