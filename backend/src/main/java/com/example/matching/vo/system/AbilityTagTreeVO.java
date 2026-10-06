package com.example.matching.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 能力标签树形视图
 */
@Data
@Schema(description = "能力标签树形视图")
public class AbilityTagTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标签ID")
    private Long id;

    @Schema(description = "标签编码")
    private String tagCode;

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "标签分类")
    private String tagCategory;

    @Schema(description = "标签层级")
    private Integer tagLevel;

    @Schema(description = "子标签列表")
    private List<AbilityTagTreeVO> children;
}
