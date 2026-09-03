package com.example.matching.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Personnel batch import request.
 */
@Data
@Schema(description = "人员批量导入请求")
public class EmpBatchImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导入人员列表")
    private List<EmpImportItem> items;

    @Data
    @Schema(description = "导入人员数据项")
    public static class EmpImportItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "人员编号（可选，留空自动生成）")
        private String empCode;

        @Schema(description = "姓名")
        private String realName;

        @Schema(description = "性别：0女，1男")
        private Integer gender;

        @Schema(description = "手机号")
        private String phone;

        @Schema(description = "邮箱")
        private String email;
    }
}
