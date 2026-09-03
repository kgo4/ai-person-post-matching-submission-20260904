package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 能力标签保存请求DTO
 */
@Data
@Schema(description = "能力标签保存请求，用于新增或更新能力标签定义，支持三级树形分类结构，可划分为技术能力、软技能、业务能力等类别")
public class AbilityTagSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "能力标签主键ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotBlank(message = "标签编码不能为空")
    @Schema(description = "标签唯一编码，用于系统内部标识和引用，建议使用英文大写加下划线格式", example = "JAVA_BASIC")
    private String tagCode;

    @NotBlank(message = "标签名称不能为空")
    @Schema(description = "标签名称，用于前端页面展示，应简洁明确", example = "Java基础")
    private String tagName;

    @Schema(description = "父标签ID，用于构建能力标签的树形层级结构，填0或留空表示该标签为一级标签", example = "0")
    private Long parentId;

    @NotBlank(message = "标签分类不能为空")
    @Schema(description = "标签分类，用于区分能力类型：TECHNICAL-技术能力（编程语言、框架等），SOFT-软技能（沟通、协作等），BUSINESS-业务能力（行业知识、流程等）", example = "TECHNICAL")
    private String tagCategory;

    @NotNull(message = "标签层级不能为空")
    @Schema(description = "标签层级深度：1-一级标签（大类），2-二级标签（子类），3-三级标签（具体能力项）", example = "2")
    private Integer tagLevel;

    @Schema(description = "标签描述，对该能力标签的详细说明，帮助管理员和使用者理解其含义", example = "掌握Java语言的基本语法、面向对象编程及核心类库的使用")
    private String description;

    @Schema(description = "排序字段，数值越小排序越靠前，用于控制前端展示顺序", example = "10")
    private Integer sortOrder;
}
