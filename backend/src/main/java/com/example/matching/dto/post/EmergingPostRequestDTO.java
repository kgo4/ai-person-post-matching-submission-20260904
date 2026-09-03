package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新兴岗位定义请求DTO
 */
@Data
@Schema(description = "新兴岗位定义请求")
public class EmergingPostRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "岗位名称不能为空")
    @Schema(description = "岗位名称", example = "AI提示词工程师")
    private String postName;

    @Schema(description = "岗位描述", example = "负责设计和优化AI大模型的提示词...")
    private String description;

    @Schema(description = "行业/业务方向", example = "人工智能")
    private String industry;

    @Schema(description = "关键职责描述")
    private String keyResponsibilities;

    @Schema(description = "是否创建岗位（true=直接创建，false=仅返回推荐结果）")
    private Boolean createPost;
}
