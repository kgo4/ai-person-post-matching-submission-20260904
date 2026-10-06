package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 岗位模型模板保存请求DTO
 */
@Data
@Schema(description = "岗位模型模板保存请求")
public class PostTemplateSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID（新增时不传）")
    private Long id;

    @Schema(description = "模板编码；新增时留空由系统自动生成")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Schema(description = "模板名称")
    private String templateName;

    @NotBlank(message = "岗位序列不能为空")
    @Schema(description = "岗位序列：TECHNICAL-技术，MANAGEMENT-管理")
    private String postSequence;

    @Schema(description = "适用职级范围")
    private String postLevelRange;

    @Schema(description = "模板描述")
    private String description;
}
