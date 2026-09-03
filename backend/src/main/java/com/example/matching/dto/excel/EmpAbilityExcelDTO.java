package com.example.matching.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工能力 Excel 导入/导出 DTO
 */
@Data
public class EmpAbilityExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("员工工号")
    private String empCode;

    @ExcelProperty("能力标签编码")
    private String tagCode;

    @ExcelProperty("掌握等级(1-5)")
    private Integer masteryLevel;

    @ExcelProperty("评价来源(MANUAL/AI/PERFORMANCE)")
    private String evaluationSource;

    @ExcelProperty("备注")
    private String remark;
}
