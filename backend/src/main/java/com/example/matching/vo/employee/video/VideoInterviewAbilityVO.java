package com.example.matching.vo.employee.video;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 视频面试提取能力响应VO
 */
@Data
@Schema(description = "视频面试提取的能力详情，用于前端展示和审核导入")
public class VideoInterviewAbilityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "能力提取记录ID")
    private Long id;

    @Schema(description = "能力标签ID")
    private Long tagId;

    @Schema(description = "能力标签名称")
    private String tagName;

    @Schema(description = "掌握等级：1-入门，2-熟悉，3-掌握，4-精通，5-专家")
    private Integer masteryLevel;

    @Schema(description = "置信度，表示AI对该能力评估的确定程度，取值范围0.00-1.00", example = "0.87")
    private BigDecimal confidenceScore;

    @Schema(description = "来源权重，用于能力证据融合计算，取值范围0.00-1.00", example = "0.82")
    private BigDecimal sourceWeight;

    @Schema(description = "证据摘要，提取该能力所依据的关键证据概述", example = "回答项目协作问题时，表达完整且条理清晰")
    private String evidenceSummary;

    @Schema(description = "分析评语，AI对该能力的详细分析说明", example = "表达逻辑较强，能清楚说明背景、行动与结果")
    private String analysisComment;

    @Schema(description = "是否已导入：false-未导入，true-已导入")
    private Boolean importedFlag;
}
