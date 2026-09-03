package com.example.matching.controller.system;

import com.example.matching.application.system.DlqAdminApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import com.example.matching.service.common.DlqReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLQ 管理接口（仅限管理员，由 SecurityConfig 强制 hasRole("ADMIN")）
 */
@Tag(name = "DLQ管理", description = "死信队列查看、重放、丢弃，操作全部审计")
@RestController
@RequestMapping("/api/system/dlq")
@RequiredArgsConstructor
public class DlqAdminController {

    private final DlqAdminApiFacade dlqAdminApiFacade;

    @Operation(summary = "DLQ摘要", description = "队列消息数、最近检查时间、阈值状态")
    @GetMapping("/summary")
    public R<DlqReplayService.DlqSummary> summary(
            @Parameter(description = "告警阈值（消息数），不传则不判断") @RequestParam(required = false) Long alertThreshold) {
        DlqReplayService.DlqSummary summary = dlqAdminApiFacade.summary();
        if (alertThreshold != null) {
            summary = summary.withThreshold(alertThreshold);
        }
        return R.ok(summary);
    }

    @Operation(summary = "重放DLQ消息", description = "顺序重放最早消息，发布确认后 ACK，失败退回 DLQ")
    @PostMapping("/replay")
    public R<Integer> replay(
            @Parameter(description = "重放条数 1..100") @RequestParam(defaultValue = "1") int count) {
        validateCount(count);
        int replayed = dlqAdminApiFacade.replay(count);
        return R.ok("已重放 " + replayed + " 条消息", replayed);
    }

    @Operation(summary = "丢弃DLQ消息", description = "显式丢弃并审计，丢弃原因必填")
    @PostMapping("/discard")
    public R<Integer> discard(
            @Parameter(description = "丢弃条数 1..100") @RequestParam(defaultValue = "1") int count,
            @Parameter(description = "丢弃原因（必填，用于审计）") @RequestParam String reason) {
        validateCount(count);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "丢弃原因不能为空");
        }
        int discarded = dlqAdminApiFacade.discard(count, reason);
        return R.ok("已丢弃 " + discarded + " 条消息", discarded);
    }

    private void validateCount(int count) {
        if (count < 1 || count > 100) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "条数必须在 1..100 之间");
        }
    }
}
