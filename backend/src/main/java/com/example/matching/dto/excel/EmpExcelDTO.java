package com.example.matching.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Personnel Excel import/export DTO.
 */
@Data
public class EmpExcelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("人员编号(留空自动生成)")
    private String empCode;

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("性别(0女1男)")
    private Integer gender;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("状态(0停用1启用)")
    private Integer status;
}
