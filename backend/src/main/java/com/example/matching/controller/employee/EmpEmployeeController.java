package com.example.matching.controller.employee;

import com.example.matching.application.employee.EmpEmployeeApiFacade;
import com.example.matching.application.common.FileContent;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.dto.employee.api.EmployeeStatusRequest;
import com.example.matching.dto.employee.api.EmployeeUpdateRequest;
import com.example.matching.dto.employee.api.PassedPostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "人员画像", description = "匹配用人员基础信息、导入导出、锁定解锁")
@RestController
@RequestMapping("/api/employee")
@RequiredArgsConstructor
public class EmpEmployeeController {

    private final EmpEmployeeApiFacade empEmployeeApiFacade;

    @Operation(summary = "分页查询人员")
    @GetMapping("/page")
    public R<PageResponse<EmployeeResponse>> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "姓名/编号关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1启用，0停用") @RequestParam(required = false) Integer status) {
        PageResponse<EmployeeResponse> page = empEmployeeApiFacade.page(current, size, keyword, status);
        return R.ok(page);
    }

    @Operation(summary = "获取当前登录账号关联的人员档案",
            description = "员工本人身份入口：按 Token 解析当前账号绑定的人员档案，用于“仅本人”数据链路。"
                    + "账号尚无人员档案时按需自动建档（管理端账号，如超级管理员，由后台建号不带档案，"
                    + "但其被授予了员工侧功能，需要一份本人档案才能使用评估/匹配/学习路径）")
    @GetMapping("/me")
    public R<EmployeeResponse> currentEmployee() {
        // ensure* 而不是 get*：管理端账号首次进入本人链路时补建档案，否则前端拿不到 empId，
        // 员工侧页面会静默无响应（典型：超级管理员点「开始评估」没反应）。
        return R.ok(empEmployeeApiFacade.ensureProfileForUser(
                com.example.matching.utils.SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "获取人员详情")
    @GetMapping("/{id}")
    public R<EmployeeResponse> getById(@PathVariable Long id) {
        return R.ok(empEmployeeApiFacade.getById(id));
    }

    @Operation(summary = "新增人员")
    @PostMapping
    public R<Void> save(@RequestBody EmployeeCreateRequest req) {
        empEmployeeApiFacade.save(req);
        return R.ok();
    }

    @Operation(summary = "更新人员")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody EmployeeUpdateRequest req) {
        empEmployeeApiFacade.update(id, req);
        return R.ok();
    }

    @Operation(summary = "批量导入人员(JSON)")
    @PostMapping("/batch-import")
    public R<Integer> batchImport(@RequestBody List<EmployeeCreateRequest> list) {
        int count = empEmployeeApiFacade.batchImport(list);
        return R.ok("成功导入" + count + "条记录", count);
    }

    @Operation(summary = "Excel导入人员")
    @PostMapping("/import-excel")
    public R<Integer> importExcel(@RequestParam MultipartFile file) throws IOException {
        try (java.io.InputStream inputStream = file.getInputStream()) {
            int count = empEmployeeApiFacade.importExcel(file.getOriginalFilename(), inputStream);
            return R.ok("成功导入" + count + "条记录", count);
        }
    }

    @Operation(summary = "导出人员Excel")
    @GetMapping("/export-excel")
    public void exportExcel(HttpServletResponse response) throws IOException {
        writeExcel(response, empEmployeeApiFacade.exportExcel());
    }

    @Operation(summary = "下载人员导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        writeExcel(response, empEmployeeApiFacade.downloadTemplate());
    }

    private void writeExcel(HttpServletResponse response, FileContent content) throws IOException {
        String filename = java.net.URLEncoder.encode(content.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(content.content());
    }

    @Operation(summary = "锁定人员")
    @PutMapping("/{id}/lock")
    public R<Void> lock(@PathVariable Long id) {
        empEmployeeApiFacade.lock(id);
        return R.ok();
    }

    @Operation(summary = "解锁人员")
    @PutMapping("/{id}/unlock")
    public R<Void> unlock(@PathVariable Long id) {
        empEmployeeApiFacade.unlock(id);
        return R.ok();
    }

    @Operation(summary = "启用/禁用人员",
            description = "HR 对员工档案唯一的“下线”手段：禁用后该员工不再作为在册人员参与匹配与评估运营，"
                    + "但档案与全部历史数据保留，可随时重新启用。")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody EmployeeStatusRequest req) {
        empEmployeeApiFacade.updateStatus(id, req.status());
        return R.ok();
    }

    @Operation(summary = "作废人员档案（逻辑删除）",
            description = "仅用于误建档案纠错，在职人员必须先『禁用』。作废后档案从列表隐藏，"
                    + "但历史业务数据（能力、匹配、学习记录）全部保留，可在数据库层面恢复。"
                    + "此接口不再级联物理删除任何业务数据。")
    @DeleteMapping("/{id}")
    public R<Void> archive(@PathVariable Long id,
                           @Parameter(description = "作废原因，必填") @RequestParam(required = false) String reason) {
        empEmployeeApiFacade.archive(id, reason);
        return R.ok();
    }

    @Operation(summary = "查询本人的「已通过岗位」",
            description = "员工视角：当前登录账号绑定档案的已通过岗位（HR 审核通过 + 已推送 + 匹配状态为强适配/适配），"
                    + "按岗位去重取最新一条。人员范围由登录身份固定为本人。")
    @GetMapping("/me/passed-posts")
    public R<List<PassedPostResponse>> myPassedPosts() {
        return R.ok(empEmployeeApiFacade.listMyPassedPosts());
    }

    @Operation(summary = "查询指定人员的「已通过岗位」",
            description = "HR 视角。员工侧传他人 empId 会被拒绝（仅本人数据范围）。")
    @GetMapping("/{empId}/passed-posts")
    public R<List<PassedPostResponse>> passedPosts(@PathVariable Long empId) {
        return R.ok(empEmployeeApiFacade.listPassedPosts(empId));
    }

    @Operation(summary = "员工统计", description = "返回员工总数、启用数、锁定数")
    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(empEmployeeApiFacade.stats());
    }
}
