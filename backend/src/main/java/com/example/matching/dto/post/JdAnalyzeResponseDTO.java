package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * JD分析响应DTO
 */
@Data
@Schema(description = "JD智能分析响应，包含AI提取的岗位摘要和能力项列表")
public class JdAnalyzeResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分析任务ID")
    private Long taskId;

    @Schema(description = "岗位ID")
    private Long postId;

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "AI提取的岗位摘要")
    private String jobSummary;

    @Schema(description = "AI分析出的能力项列表（含与已有标签的匹配状态）")
    private List<JdAbilityItemDTO> abilities;

    @Schema(description = "分析状态：2-成功，3-失败")
    private Integer analysisStatus;

    @Schema(description = "失败时的错误信息")
    private String errorMessage;
}
