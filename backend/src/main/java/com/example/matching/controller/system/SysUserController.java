package com.example.matching.controller.system;

import com.example.matching.application.system.SysUserApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.result.PageResultVO;
import com.example.matching.common.result.R;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.LoginDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户CRUD、登录认证、密码管理、状态变更")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserApiFacade sysUserApiFacade;

    @Operation(summary = "用户登录", description = "使用用户名和密码登录系统，验证成功后返回JWT Token及用户基本信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功，返回Token和用户信息"),
            @ApiResponse(responseCode = "400", description = "用户名或密码错误"),
            @ApiResponse(responseCode = "403", description = "账号已被禁用")
    })
    @PostMapping("/login")
    public R<LoginVO> login(
            @Parameter(description = "登录请求，包含用户名和密码") @Valid @RequestBody LoginDTO dto) {
        String clientIp = currentClientIp();
        sysUserApiFacade.checkLoginAllowed(clientIp, dto.getUsername());
        try {
            LoginVO vo = sysUserApiFacade.login(dto.getUsername(), dto.getPassword());
            sysUserApiFacade.clearLoginFailures(clientIp, dto.getUsername());
            return R.ok(vo);
        } catch (BusinessException exception) {
            sysUserApiFacade.recordLoginFailure(clientIp, dto.getUsername());
            throw exception;
        }
    }

    @Operation(summary = "用户注册", description = "开放注册入口，创建普通用户账号")
    @ApiResponses({ @ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "400") })
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody UserSaveDTO dto) {
        sysUserApiFacade.checkRegistrationAllowed(currentClientIp());
        return R.ok(sysUserApiFacade.register(dto));
    }

    @Operation(summary = "分页查询用户", description = "按关键词模糊搜索用户名/真实姓名、按状态筛选，支持分页返回用户列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<PageResultVO<UserVO>> page(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认10条", example = "10") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "搜索关键词，匹配用户名或真实姓名") @RequestParam(required = false) String keyword,
            @Parameter(description = "用户状态：0-禁用，1-启用", example = "1") @RequestParam(required = false) Integer status) {
        PageResponse<UserVO> page = sysUserApiFacade.pageUsers(current, size, keyword, status);
        PageResultVO<UserVO> result = new PageResultVO<>();
        result.setRecords(page.records());
        result.setTotal(page.total());
        result.setSize(page.size());
        result.setCurrent(page.current());
        result.setPages(page.pages());
        return R.ok(result);
    }

    @Operation(summary = "获取用户详情", description = "根据用户ID查询单个用户的完整信息，包括角色、扩展字段等")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @GetMapping("/{id}")
    public R<UserVO> getById(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long id) {
        return R.ok(sysUserApiFacade.getUserVOById(id));
    }

    @Operation(summary = "获取当前用户信息", description = "根据当前登录用户的Token获取其完整个人信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token已过期")
    })
    @GetMapping("/current")
    public R<UserVO> current() {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(sysUserApiFacade.getUserVOById(userId));
    }

    @Operation(summary = "新增用户", description = "创建新的系统用户，需填写用户名、密码、真实姓名等基本信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "用户名已存在或必填字段为空")
    })
    @PostMapping
    public R<Void> save(
            @Parameter(description = "用户保存请求，包含用户名、密码、真实姓名等字段") @Valid @RequestBody UserSaveDTO dto) {
        sysUserApiFacade.saveUser(dto);
        return R.ok();
    }

    @Operation(summary = "更新用户", description = "根据用户ID修改用户的基本信息，如真实姓名、手机号、邮箱、部门等")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "用户保存请求，包含需更新的字段") @Valid @RequestBody UserSaveDTO dto) {
        dto.setId(id);
        sysUserApiFacade.saveUser(dto);
        return R.ok();
    }

    @Operation(summary = "修改密码", description = "当前登录用户修改自己的密码，需提供旧密码和新密码")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "密码修改成功"),
            @ApiResponse(responseCode = "400", description = "旧密码错误或新密码不符合规则"),
            @ApiResponse(responseCode = "401", description = "未登录或Token已过期")
    })
    @PutMapping("/change-password")
    public R<Void> changePassword(
            @Parameter(description = "修改密码请求，包含旧密码和新密码") @Valid @RequestBody ChangePasswordDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        sysUserApiFacade.changePassword(userId, dto);
        return R.ok();
    }

    @Operation(summary = "重置密码", description = "管理员根据用户ID强制重置指定用户的密码为系统默认密码")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "密码重置成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long id) {
        sysUserApiFacade.resetPassword(id);
        return R.ok();
    }

    @Operation(summary = "修改用户状态", description = "管理员启用或禁用指定用户账号，禁用后用户将无法登录")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "状态修改成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "目标状态：0-禁用，1-启用", required = true, example = "1") @RequestParam Integer status) {
        sysUserApiFacade.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除用户", description = "根据用户ID删除指定用户，删除后不可恢复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable Long id) {
        sysUserApiFacade.removeById(id);
        return R.ok();
    }

    @Operation(summary = "用户登出", description = "使当前Token失效并登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            sysUserApiFacade.invalidateUserTokens(userId);
        }
        return R.ok();
    }

    private String currentClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? "unknown" : attributes.getRequest().getRemoteAddr();
    }
}
