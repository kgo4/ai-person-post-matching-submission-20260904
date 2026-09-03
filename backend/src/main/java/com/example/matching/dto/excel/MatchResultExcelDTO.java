package com.example.matching.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 匹配结果 Excel 导出 DTO
 */
@Data
public class MatchResultExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("批次号")
    @ColumnWidth(18)
    private String batchNo;

    @ExcelProperty("员工工号")
    @ColumnWidth(14)
    private String empCode;

    @ExcelProperty("员工姓名")
    @ColumnWidth(12)
    private String empName;

    @ExcelProperty("岗位名称")
    @ColumnWidth(20)
    private String postName;

    @ExcelProperty("AI匹配度")
    @ColumnWidth(12)
    private BigDecimal aiMatchScore;

    @ExcelProperty("最终匹配度")
    @ColumnWidth(12)
    private BigDecimal finalMatchScore;

    @ExcelProperty("匹配状态")
    @ColumnWidth(12)
    private String matchStatus;

    @ExcelProperty("审批状态")
    @ColumnWidth(12)
    private String approvalStatus;

    @ExcelProperty("是否锁定")
    @ColumnWidth(10)
    private String isLocked;

    @ExcelProperty("人工备注")
    @ColumnWidth(30)
    private String manualRemark;
}
