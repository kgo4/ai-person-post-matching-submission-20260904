package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位原型保存DTO
 */
@Data
@Schema(description = "岗位原型保存请求")
public class PostPrototypeSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "原型ID（更新时传入）")
    private Long id;

    @Schema(description = "原型名称", example = "后端开发工程师")
    private String prototypeName;

    @Schema(description = "行业方向", example = "互联网")
    private String industry;

    @Schema(description = "岗位族分类", example = "技术")
    private String category;

    @Schema(description = "原型描述")
    private String description;

    @Schema(description = "状态：0停用，1启用")
    private Integer status;

    @Schema(description = "原型关联的能力标签列表")
    private List<PrototypeTagItem> tags;

    /**
     * 原型标签项
     */
    @Data
    @Schema(description = "原型标签项")
    public static class PrototypeTagItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "能力标签ID")
        private Long tagId;

        @Schema(description = "建议权重")
        private BigDecimal weight;

        @Schema(description = "建议最低要求等级")
        private Integer minRequiredLevel;

        @Schema(description = "是否核心能力")
        private Integer isCore;

        @Schema(description = "是否必备能力")
        private Integer isRequired;

        @Schema(description = "排序")
        private Integer sortOrder;
    }
}
