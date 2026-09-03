package com.example.matching.controller.system;

import com.example.matching.application.system.RoleApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.RoleCreateRequest;
import com.example.matching.dto.system.api.RoleResponse;
import com.example.matching.vo.system.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理", description = "角色CRUD、启用角色列表、用户角色分配与查询")
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final RoleApiFacade facade;

    @Operation(summary = "分页查询角色", description = "按关键词模糊搜索角色名称或编码，支持分页返回角色列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<PageResponse<RoleVO>> page(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认10条", example = "10") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "搜索关键词，匹配角色名称或角色编码") @RequestParam(required = false) String keyword) {
        return R.ok(facade.page(current, size, keyword));
    }

    @Operation(summary = "全部启用角色列表", description = "获取所有状态为启用的角色，通常用于角色分配下拉选择")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/enabled")
    public R<List<RoleVO>> listEnabled() {
        return R.ok(facade.listEnabled());
    }

    @Operation(summary = "获取角色详情", description = "根据角色ID查询单个角色的完整信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "角色不存在")
    })
    @GetMapping("/{id}")
    public R<RoleResponse> getById(
            @Parameter(description = "角色ID", required = true, example = "1") @PathVariable Long id) {
        return R.ok(facade.get(id));
    }

    @Operation(summary = "新增角色", description = "创建新的系统角色，需填写角色编码、角色名称等基本信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "角色编码已存在或必填字段为空")
    })
    @PostMapping
    public R<Void> save(
            @Parameter(description = "角色创建请求") @Valid @RequestBody RoleCreateRequest request) {
        facade.create(request);
        return R.ok();
    }

    @Operation(summary = "更新角色", description = "根据角色ID修改角色的编码、名称、描述、数据权限范围等信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "角色不存在")
    })
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "角色ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "角色创建请求") @Valid @RequestBody RoleCreateRequest request) {
        facade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除角色", description = "根据角色ID删除指定角色，删除后不可恢复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "角色不存在")
    })
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "角色ID", required = true, example = "1") @PathVariable Long id) {
        facade.delete(id);
        return R.ok();
    }

    @Operation(summary = "为用户分配角色", description = "为指定用户批量设置角色，传入完整的角色ID列表，将覆盖用户原有的角色分配")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分配成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/assign/{userId}")
    public R<Void> assignRoles(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long userId,
            @Parameter(description = "角色ID列表，覆盖用户原有角色") @RequestBody List<Long> roleIds) {
        facade.assignRoles(userId, roleIds);
        return R.ok();
    }

    @Operation(summary = "查询用户角色ID", description = "查询指定用户当前已分配的所有角色ID列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @GetMapping("/user-roles/{userId}")
    public R<List<Long>> getUserRoles(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long userId) {
        return R.ok(facade.getUserRoles(userId));
    }
}
