package com.example.matching.controller.ability;

import com.example.matching.application.ability.PersonAbilityGovernanceApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.ability.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "人员能力治理", description = "人工修改最终入库能力标签、等级，记录治理事件，生成Agent记忆")
@RestController
@RequestMapping("/api/ability/governance")
@RequiredArgsConstructor
public class PersonAbilityGovernanceController {

    private final PersonAbilityGovernanceApiFacade facade;

    @Operation(summary = "替换能力标签", description = "将员工的某个能力标签替换为另一个标签")
    @PostMapping("/replace-tag")
    public R<GovernanceEventResponse> replaceTag(@RequestBody ReplaceTagRequest request) {
        GovernanceEventResponse event = facade.replaceTag(request);
        return R.ok("标签替换成功", event);
    }

    @Operation(summary = "修改能力等级", description = "修改员工某个能力的等级")
    @PostMapping("/change-level")
    public R<GovernanceEventResponse> changeLevel(@RequestBody ChangeLevelRequest request) {
        GovernanceEventResponse event = facade.changeLevel(request);
        return R.ok("等级修改成功", event);
    }

    @Operation(summary = "删除能力标签", description = "删除员工的某个能力标签")
    @PostMapping("/remove-tag")
    public R<GovernanceEventResponse> removeTag(
            @Parameter(description = "员工ID") @RequestParam Long empId,
            @Parameter(description = "标签ID") @RequestParam Long tagId,
            @Parameter(description = "删除原因") @RequestParam String reason,
            @Parameter(description = "是否泛化为全局拒绝规则") @RequestParam(defaultValue = "false") boolean generalizeRule) {
        GovernanceEventResponse event = facade.removeTag(empId, tagId, reason, generalizeRule);
        return R.ok("标签删除成功", event);
    }

    @Operation(summary = "重命名标签", description = "重命名标签（影响所有引用该标签的员工能力）")
    @PostMapping("/rename-tag")
    public R<List<GovernanceEventResponse>> renameTag(@RequestBody RenameTagRequest request) {
        List<GovernanceEventResponse> events = facade.renameTag(request);
        return R.ok("标签重命名成功，影响" + events.size() + "条能力记录", events);
    }

    @Operation(summary = "获取员工治理历史", description = "获取员工的能力治理事件历史")
    @GetMapping("/history/{empId}")
    public R<List<GovernanceEventResponse>> getGovernanceHistory(
            @Parameter(description = "员工ID") @PathVariable Long empId) {
        return R.ok(facade.getGovernanceHistory(empId));
    }

    @Operation(summary = "获取标签治理历史", description = "获取标签相关的治理事件")
    @GetMapping("/tag-history/{tagId}")
    public R<List<GovernanceEventResponse>> getGovernanceByTag(
            @Parameter(description = "标签ID") @PathVariable Long tagId) {
        return R.ok(facade.getGovernanceByTag(tagId));
    }

    @Operation(summary = "获取Agent记忆", description = "按适用范围获取Agent记忆")
    @GetMapping("/memories")
    public R<List<AgentMemoryResponse>> getMemories(
            @Parameter(description = "适用范围") @RequestParam(defaultValue = "ALL") String scope) {
        return R.ok(facade.getMemories(scope));
    }

    @Operation(summary = "搜索Agent记忆", description = "按文本搜索相关Agent记忆")
    @GetMapping("/memories/search")
    public R<List<AgentMemoryResponse>> searchMemories(
            @Parameter(description = "搜索文本") @RequestParam String text,
            @Parameter(description = "适用范围") @RequestParam(defaultValue = "ALL") String scope) {
        return R.ok(facade.searchMemories(text, scope));
    }
}
