package com.example.matching.controller.system;

import com.example.matching.application.system.SysUserApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.PageResultVO;
import com.example.matching.common.result.R;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.LoginDTO;
import com.example.matching.dto.system.MyProfileUpdateDTO;
import java.util.Map;

import com.example.matching.dto.system.RegisterEmailCodeRequest;
import com.example.matching.dto.system.RegisterRequestDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.MyProfileVO;
import com.example.matching.vo.system.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "用户管理", description = "用户CRUD、登录认证、密码管理、状态变更")
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserApiFacade sysUserApiFacade;
    private final com.example.matching.service.system.RegisterEmailCodeService registerEmailCodeService;

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

    @Operation(summary = "用户注册（不带验证码，仅员工）",
            description = "兼容旧调用方。新前端请走 /register/v2（带邮箱验证码与角色选择）。")
    @ApiResponses({ @ApiResponse(responseCode = "200"), @ApiResponse(responseCode = "400") })
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody UserSaveDTO dto) {
        sysUserApiFacade.checkRegistrationAllowed(currentClientIp());
        return R.ok(sysUserApiFacade.register(dto));
    }

    @Operation(summary = "获取注册邮箱验证码",
            description = "向指定邮箱发送 6 位验证码。同一邮箱 60 秒内只能获取一次，验证码 5 分钟有效，"
                    + "连续输错 5 次即作废。")
    @PostMapping("/register/email-code")
    public R<Map<String, Object>> sendRegisterEmailCode(@Valid @RequestBody RegisterEmailCodeRequest request) {
        sysUserApiFacade.checkRegistrationAllowed(currentClientIp());
        String code = registerEmailCodeService.send(request.getEmail());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("cooldownSeconds", 60);
        data.put("expireSeconds", 300);
        // 仅联调开关打开时回传（生产必须关闭），见 RegisterProperties 注释
        if (registerEmailCodeService.isCodeExposedInResponse()) {
            data.put("code", code);
        }
        return R.ok(data);
    }

    @Operation(summary = "自助注册（支持选择角色）",
            description = "需要邮箱验证码；除员工外还需角色授权码（由服务端环境变量配置），"
                    + "超级管理员不支持自助注册。")
    @PostMapping("/register/v2")
    public R<LoginVO> registerV2(@Valid @RequestBody RegisterRequestDTO dto) {
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

    // ===================== 个人中心（自助） =====================
    // 这三个端点必须声明在管理端规则之后也保持不变：它们只操作**当前登录人**，
    // 人员范围由 SecurityUtils + Service 双重固定，URL 里不接受 userId 参数。

    @Operation(summary = "获取本人资料（个人中心）",
            description = "返回当前登录账号的资料，手机号**不脱敏**（本人需要编辑它）。"
                    + "与 /current 的区别：/current 是全局身份载荷（手机号打码），本接口专供个人中心。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录或Token已过期")
    })
    @GetMapping("/profile")
    public R<MyProfileVO> myProfile() {
        return R.ok(sysUserApiFacade.getMyProfile(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "更新本人资料（个人中心）",
            description = "修改头像/手机号/邮箱。字段为 null 表示不修改，空串表示清空；"
                    + "用户名与真实姓名不可自助修改（属登录凭据与人员档案映射）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "手机号或邮箱格式不正确"),
            @ApiResponse(responseCode = "401", description = "未登录或Token已过期")
    })
    @PutMapping("/profile")
    public R<Void> updateMyProfile(
            @Parameter(description = "个人资料更新请求") @Valid @RequestBody MyProfileUpdateDTO dto) {
        sysUserApiFacade.updateMyProfile(SecurityUtils.getCurrentUserId(), dto);
        return R.ok();
    }

    @Operation(summary = "上传本人头像",
            description = "返回可访问的头像路径，再把该路径提交到「更新本人资料」接口。"
                    + "支持 JPG/PNG/GIF/WebP，单张不超过 2MB。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功，返回头像访问路径"),
            @ApiResponse(responseCode = "400", description = "文件为空、超限或格式不支持"),
            @ApiResponse(responseCode = "401", description = "未登录或Token已过期")
    })
    @PostMapping("/profile/avatar")
    public R<String> uploadMyAvatar(
            @Parameter(description = "头像图片文件") @RequestParam("file") MultipartFile file) {
        try {
            return R.ok(sysUserApiFacade.uploadAvatar(file.getBytes(), file.getContentType()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "读取上传文件失败: " + e.getMessage());
        }
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
