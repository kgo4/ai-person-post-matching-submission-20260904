package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 扩展字段配置请求DTO
 */
@Data
@Schema(description = "扩展字段配置请求，用于动态定义员工、岗位、能力等业务模块的自定义扩展字段，支持文本、数字、日期、下拉等多种字段类型")
public class ExtendFieldConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "扩展字段配置主键ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotBlank(message = "业务模块不能为空")
    @Schema(description = "业务模块标识，指定该扩展字段应用于哪个业务领域：EMPLOYEE-员工模块，POST-岗位模块，ABILITY-能力模块", example = "EMPLOYEE")
    private String businessModule;

    @NotBlank(message = "字段名称不能为空")
    @Schema(description = "字段名称，作为JSON数据的key存储，建议使用驼峰命名，用于系统内部引用", example = "certificateLevel")
    private String fieldName;

    @NotBlank(message = "字段显示标签不能为空")
    @Schema(description = "字段显示标签，在前端表单和列表中展示给用户看的字段标题", example = "证书等级")
    private String fieldLabel;

    @NotBlank(message = "字段类型不能为空")
    @Schema(description = "字段数据类型：STRING-单行文本，TEXT-多行长文本，NUMBER-数字（支持小数），DATE-日期（yyyy-MM-dd格式），SELECT-下拉单选（需配合selectOptions使用）", example = "SELECT")
    private String fieldType;

    @Schema(description = "下拉选项内容，仅当fieldType为SELECT时有效，以逗号分隔的选项列表", example = "初级,中级,高级,专家级")
    private String selectOptions;

    @Schema(description = "是否必填：0-非必填（选填项），1-必填（提交时必须提供值）", example = "0")
    private Integer isRequired;

    @Schema(description = "排序字段，数值越小排序越靠前，用于控制扩展字段在表单中的展示顺序", example = "5")
    private Integer sortOrder;

    @Schema(description = "Status: 0 disabled, 1 enabled", example = "1")
    private Integer status;
}
