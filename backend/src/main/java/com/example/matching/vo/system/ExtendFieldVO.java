package com.example.matching.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 扩展字段视图
 */
@Data
@Schema(description = "扩展字段视图")
public class ExtendFieldVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段ID")
    private Long id;

    @Schema(description = "业务模块")
    private String businessModule;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段显示标签")
    private String fieldLabel;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "下拉选项")
    private String selectOptions;

    @Schema(description = "是否必填")
    private Integer isRequired;

    @Schema(description = "排序字段")
    private Integer sortOrder;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
