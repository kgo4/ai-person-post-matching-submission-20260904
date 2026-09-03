package com.example.matching.dto.employee.video;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 视频面试能力导入请求DTO
 */
@Data
@Schema(description = "视频面试能力导入请求，用于将审核通过的能力提取结果导入到员工能力档案")
public class VideoInterviewImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "导入的能力ID列表不能为空")
    @Schema(description = "待导入的能力提取记录ID列表，指定哪些能力项需要导入到员工能力档案", example = "[1, 2, 4]")
    private List<Long> abilityIds;

    @Schema(description = "是否覆盖已有的同来源记录，true-覆盖已存在的AI_INTERVIEW来源记录，false-跳过已存在的记录", example = "false")
    private Boolean overwriteExistingSource = false;

    @Schema(description = "备注后缀，追加到导入记录的备注中，用于标记导入来源和审核信息", example = "人工复核后导入")
    private String remarkSuffix;
}
