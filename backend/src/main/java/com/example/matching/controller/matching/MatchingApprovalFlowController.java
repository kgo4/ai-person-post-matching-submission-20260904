package com.example.matching.controller.matching;

import com.example.matching.application.matching.ApprovalFlowApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.dto.matching.api.ApprovalFlowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "匹配审批", description = "匹配审批流程管理模块。HR 发起审批 → 管理员审核 → 通过/驳回。")
@RestController
@RequestMapping("/api/matching/approval-flow")
@RequiredArgsConstructor
public class MatchingApprovalFlowController {

    private final ApprovalFlowApiFacade approvalFlowApiFacade;

    @Operation(summary = "发起审批流程", description = "HR 为指定匹配记录发起审批，指定一位管理员进行审核。")
    @PostMapping("/initiate/{matchingRecordId}")
    public R<Void> initiate(
            @Parameter(description = "匹配记录主键ID") @PathVariable Long matchingRecordId,
            @Parameter(description = "管理员审核人ID") @RequestParam Long adminApproverId) {
        approvalFlowApiFacade.initiate(matchingRecordId, adminApproverId);
        return R.ok();
    }

    @Operation(summary = "执行审批操作（通过/驳回）", description = "对审批任务执行通过或驳回操作。传入审批DTO，包含匹配记录ID、审批结果、审批意见。")
    @PostMapping("/approve")
    public R<Void> approve(@Parameter(description = "审批操作DTO") @Valid @RequestBody MatchingApprovalDTO dto) {
        approvalFlowApiFacade.approve(dto);
        return R.ok();
    }

    @Operation(summary = "查询指定匹配记录的审批流程", description = "根据匹配记录ID查询该记录关联的所有审批节点，包括审批人、结果、意见、时间。")
    @GetMapping("/{matchingRecordId}")
    public R<List<ApprovalFlowResponse>> listByRecordId(@Parameter(description = "匹配记录主键ID") @PathVariable Long matchingRecordId) {
        return R.ok(approvalFlowApiFacade.listByRecordId(matchingRecordId));
    }

    @Operation(summary = "查询我的待办任务", description = "根据用户ID查询该用户的待办审批任务列表。")
    @GetMapping("/pending/{userId}")
    public R<List<Map<String, Object>>> pendingTasks(@Parameter(description = "用户ID（审批人ID）") @PathVariable Long userId) {
        return R.ok(approvalFlowApiFacade.pendingTasks(userId));
    }
}
