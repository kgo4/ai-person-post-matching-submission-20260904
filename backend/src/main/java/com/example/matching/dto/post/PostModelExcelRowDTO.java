package com.example.matching.dto.post;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 岗位能力模型Excel导入行DTO
 * <p>
 * 支持两种模板格式：
 * <ul>
 *   <li>模板A（AI补齐）：岗位编码 | 岗位名称 | 岗位描述/JD</li>
 *   <li>模板B（直接导入）：岗位编码 | 岗位名称 | 能力标签编码 | 最低等级 | 权重 | 是否核心 | 是否必填</li>
 * </ul>
 */
@Data
public class PostModelExcelRowDTO {

    @ExcelProperty("岗位编码")
    private String postCode;

    @ExcelProperty("岗位名称")
    private String postName;

    @ExcelProperty("岗位描述")
    private String postDescription;

    @ExcelProperty("能力标签编码")
    private String tagCode;

    @ExcelProperty("能力标签名称")
    private String tagName;

    @ExcelProperty("最低等级")
    private Integer minRequiredLevel;

    @ExcelProperty("权重")
    private BigDecimal weight;

    @ExcelProperty("是否核心")
    private Integer isCore;

    @ExcelProperty("是否必填")
    private Integer isRequired;

    @ExcelProperty("备注")
    private String remark;

    /**
     * 判断是模板A还是模板B
     * 模板A：有岗位描述，无能力标签编码
     * 模板B：有能力标签编码
     */
    public boolean isTemplateB() {
        return tagCode != null && !tagCode.isBlank();
    }
}
