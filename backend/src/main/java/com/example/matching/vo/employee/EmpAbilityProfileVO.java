package com.example.matching.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Personnel ability profile view.
 */
@Data
@Schema(description = "人员能力画像视图")
public class EmpAbilityProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "人员ID")
    private Long empId;

    @Schema(description = "人员编号")
    private String empCode;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "综合能力评分")
    private BigDecimal overallScore;

    @Schema(description = "能力标签明细")
    private List<AbilityDetail> abilityDetails;

    @Data
    @Schema(description = "能力标签明细")
    public static class AbilityDetail implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "能力标签ID")
        private Long tagId;

        @Schema(description = "能力标签名称")
        private String tagName;

        @Schema(description = "能力标签分类")
        private String tagCategory;

        @Schema(description = "掌握等级")
        private Integer masteryLevel;

        @Schema(description = "掌握等级名称")
        private String masteryLevelName;
    }
}
