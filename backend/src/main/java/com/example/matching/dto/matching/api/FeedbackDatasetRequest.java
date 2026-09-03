package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "反馈数据集提交请求")
public record FeedbackDatasetRequest(
    @NotNull(message = "匹配记录ID不能为空")
    @Schema(description = "关联的匹配记录ID", example = "5001") Long matchingRecordId,

    @Schema(description = "人工最终匹配分", example = "85.50") BigDecimal manualScore,

    @Schema(description = "人工补充说明") String feedbackContent,

    @Schema(description = "结构化反馈原因，JSON数组格式") String feedbackDimensions,

    @Schema(description = "是否允许导出：0否，1是", example = "0") Integer exportEnabled
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
