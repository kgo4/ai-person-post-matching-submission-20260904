package com.example.matching.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 匹配审批请求DTO
 */
@Data
@Schema(description = "匹配审批请求，用于对某条人岗匹配结果进行审批操作，支持通过和驳回两种审批结果")
public class MatchingApprovalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "匹配记录ID不能为空")
    @Schema(description = "匹配记录ID，关联匹配结果表的主键，标识要被审批的具体匹配记录", example = "5001")
    private Long matchingRecordId;

    @NotNull(message = "审批状态不能为空")
    @Schema(description = "审批结果状态：1-审批通过（确认该员工适合该岗位），2-审批驳回（该员工不适合该岗位或需要进一步评估）", example = "1")
    private Integer approvalStatus;

    @Schema(description = "审批意见，审批人对结果的补充说明或理由，驳回时建议填写具体原因", example = "经综合评估，该员工的技能水平与岗位要求高度匹配，建议安排面试")
    private String approvalRemark;
}
