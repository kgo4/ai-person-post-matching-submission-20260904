package com.example.matching.controller.matching;

import com.example.matching.application.matching.BlackWhiteListApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.api.BlackWhiteListEntryRequest;
import com.example.matching.dto.matching.api.BlackWhiteListEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "黑白名单", description = "匹配黑白名单管理模块，用于控制特定员工与岗位之间的匹配限制。白名单确保指定匹配必须通过，黑名单禁止指定匹配发生。")
@RestController
@RequestMapping("/api/matching/black-white-list")
@RequiredArgsConstructor
public class MatchingBlackWhiteListController {

    private final BlackWhiteListApiFacade blackWhiteListApiFacade;

    @Operation(summary = "分页查询黑白名单", description = "分页查询黑白名单记录列表，支持按员工ID和岗位ID进行筛选，返回分页的黑白名单数据。")
    @GetMapping("/page")
    public R<PageResponse<BlackWhiteListEntryResponse>> page(
            @Parameter(description = "当前页码，从1开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "员工ID，用于筛选指定员工的黑白名单记录") @RequestParam(required = false) Long empId,
            @Parameter(description = "岗位ID，用于筛选指定岗位的黑白名单记录") @RequestParam(required = false) Long postId) {
        return R.ok(blackWhiteListApiFacade.page(current, size, empId, postId));
    }

    @Operation(summary = "新增黑白名单记录", description = "创建一条新的黑白名单记录，需指定员工ID、岗位ID以及名单类型（白名单或黑名单）。新增后该规则立即生效，影响后续匹配流程。")
    @PostMapping
    public R<Void> save(@Parameter(description = "黑白名单请求对象") @Valid @RequestBody BlackWhiteListEntryRequest request) {
        blackWhiteListApiFacade.create(request);
        return R.ok();
    }

    @Operation(summary = "更新黑白名单记录", description = "根据ID更新已有的黑白名单记录，可修改名单类型、备注等信息，更新后规则立即生效。")
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "黑白名单记录主键ID") @PathVariable Long id,
            @Parameter(description = "黑白名单请求对象") @Valid @RequestBody BlackWhiteListEntryRequest request) {
        blackWhiteListApiFacade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除黑白名单记录", description = "根据ID删除指定的黑白名单记录，删除后该员工与岗位之间的匹配限制将被移除，不再影响后续匹配流程。")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "黑白名单记录主键ID") @PathVariable Long id) {
        blackWhiteListApiFacade.delete(id);
        return R.ok();
    }
}
