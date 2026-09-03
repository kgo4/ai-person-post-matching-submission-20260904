package com.example.matching.controller.employee;

import com.example.matching.application.employee.EmpEmployeeApiFacade;
import com.example.matching.application.common.FileContent;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.dto.employee.api.EmployeeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

    @Operation(summary = "删除人员")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        empEmployeeApiFacade.delete(id);
        return R.ok();
    }

    @Operation(summary = "员工统计", description = "返回员工总数、启用数、锁定数")
    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(empEmployeeApiFacade.stats());
    }
}
