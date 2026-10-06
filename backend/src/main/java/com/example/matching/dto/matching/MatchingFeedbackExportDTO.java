package com.example.matching.dto.matching;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 反馈数据导出DTO
 */
@Data
public class MatchingFeedbackExportDTO {

    @ExcelProperty("ID")
    @ColumnWidth(10)
    private Long id;

    @ExcelProperty("匹配记录ID")
    @ColumnWidth(15)
    private Long matchingRecordId;

    @ExcelProperty("员工ID")
    @ColumnWidth(12)
    private Long empId;

    @ExcelProperty("岗位ID")
    @ColumnWidth(12)
    private Long postId;

    @ExcelProperty("AI匹配分")
    @ColumnWidth(12)
    private BigDecimal aiMatchScore;

    @ExcelProperty("人工最终匹配分")
    @ColumnWidth(15)
    private BigDecimal finalMatchScore;

    @ExcelProperty("匹配状态")
    @ColumnWidth(12)
    private Integer finalMatchStatus;

    @ExcelProperty("采纳状态")
    @ColumnWidth(12)
    private String adoptionStatusText;

    @ExcelProperty("反馈原因")
    @ColumnWidth(30)
    private String feedbackReasons;

    @ExcelProperty("反馈说明")
    @ColumnWidth(40)
    private String feedbackComment;

    @ExcelProperty("导出授权")
    @ColumnWidth(12)
    private String exportStatusText;

    @ExcelProperty("反馈时间")
    @ColumnWidth(20)
    private LocalDateTime feedbackTime;
}
