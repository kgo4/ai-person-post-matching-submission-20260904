package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 岗位硬性条件规则配置 DTO。
 */
@Data
@Schema(description = "岗位硬性条件规则配置")
public class PostHardConditionRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull(message = "岗位ID不能为空")
    private Long postId;

    @NotBlank(message = "字段名不能为空")
    private String fieldName;

    @NotBlank(message = "字段名称不能为空")
    private String fieldLabel;

    private String fieldType;

    @NotBlank(message = "操作符不能为空")
    private String operator;

    @NotBlank(message = "条件值不能为空")
    private String expectedValue;

    private String valueRankJson;

    private Integer enabled;

    private Integer sortOrder;

    private String remark;
}
