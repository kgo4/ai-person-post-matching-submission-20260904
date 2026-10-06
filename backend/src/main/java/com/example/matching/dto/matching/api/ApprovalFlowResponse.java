package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "审批流程响应")
public record ApprovalFlowResponse(
    @Schema(description = "审批流程主键ID") Long id,
    @Schema(description = "匹配记录ID") Long matchingRecordId,
    @Schema(description = "审批人ID") Long approverId,
    @Schema(description = "审批节点顺序") Integer nodeOrder,
    @Schema(description = "审批节点名称") String nodeName,
    @Schema(description = "审批状态：0待审批，1审批通过，2审批驳回") Integer approvalStatus,
    @Schema(description = "审批意见") String approvalRemark,
    @Schema(description = "审批时间") LocalDateTime approvalTime,
    @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
