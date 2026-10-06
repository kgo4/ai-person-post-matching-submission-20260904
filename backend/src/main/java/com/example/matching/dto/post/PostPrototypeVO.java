package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位原型视图VO
 */
@Data
@Schema(description = "岗位原型详情")
public class PostPrototypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "原型ID")
    private Long id;

    @Schema(description = "原型名称")
    private String prototypeName;

    @Schema(description = "行业方向")
    private String industry;

    @Schema(description = "岗位族分类")
    private String category;

    @Schema(description = "原型描述")
    private String description;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "向量召回相似度分数 (0-1)")
    private Float similarityScore;

    @Schema(description = "关联的能力标签列表")
    private List<PrototypeTagVO> tags;

    /**
     * 原型标签VO
     */
    @Data
    @Schema(description = "原型标签详情")
    public static class PrototypeTagVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "ID")
        private Long id;

        @Schema(description = "能力标签ID")
        private Long tagId;

        @Schema(description = "标签名称")
        private String tagName;

        @Schema(description = "标签分类")
        private String tagCategory;

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
