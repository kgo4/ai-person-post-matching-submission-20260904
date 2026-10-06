package com.example.matching.controller.employee;

import com.example.matching.application.employee.ResumeParseApiFacade;
import com.example.matching.application.common.FileContent;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.ResumeParseResponse;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "简历解析", description = "简历解析管理接口，支持上传PDF/DOC/DOCX格式简历，通过AI解析提取员工能力信息")
@RestController
@RequestMapping("/api/employee/ability/resume-parse")
@RequiredArgsConstructor
public class ResumeParseController {

    private final ResumeParseApiFacade resumeParseApiFacade;

    @Operation(summary = "上传并解析简历", description = "上传员工简历文件（支持PDF/DOC/DOCX格式），系统将自动解析简历内容并通过AI分析提取能力信息")
    @PostMapping("/upload")
    public R<ResumeParseResponse> uploadAndParse(
            @Parameter(description = "员工ID") @RequestParam Long empId,
            @Parameter(description = "简历文件") @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        try {
            ResumeParseResponse result = resumeParseApiFacade.uploadAndParse(
                    empId, file.getOriginalFilename(), file.getBytes(), userId);
            return R.ok("简历上传成功，正在解析", result);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded resume", exception);
        }
    }

    @Operation(summary = "查询员工简历解析记录", description = "获取指定员工的所有简历解析记录列表")
    @GetMapping("/list/{empId}")
    public R<List<ResumeParseResponse>> listByEmpId(
            @Parameter(description = "员工ID") @PathVariable Long empId) {
        return R.ok(resumeParseApiFacade.listByEmpId(empId));
    }

    @Operation(summary = "查询解析详情", description = "根据解析记录ID获取详细的解析结果")
    @GetMapping("/{id}")
    public R<ResumeParseResponse> getById(
            @Parameter(description = "解析记录ID") @PathVariable Long id) {
        return R.ok(resumeParseApiFacade.getById(id));
    }

    /**
     * @deprecated 已废弃。简历能力正式入库统一走能力评估工作流证据路径，
     * 此「直接导入正式能力」旧端点保留仅供兼容，勿新增调用。
     */
    @Deprecated
    @Operation(summary = "导入解析结果到能力档案（已废弃）", description = "将简历解析结果中的能力信息导入到员工的能力档案中，返回详细导入统计。此端点已废弃，能力正式入库统一走能力评估工作流证据路径")
    @PostMapping("/{id}/import")
    public R<AbilityImportResultDTO> importToAbilityProfile(
            @Parameter(description = "解析记录ID") @PathVariable Long id) {
        AbilityImportResultDTO result = resumeParseApiFacade.importToAbilityProfile(id);
        return R.ok(result.getMessage(), result);
    }

    @Operation(summary = "查看原始简历文件", description = "根据解析记录ID下载/预览原始简历文件")
    @GetMapping("/{id}/file")
    public void viewFile(
            @Parameter(description = "解析记录ID") @PathVariable Long id,
            HttpServletResponse response) throws IOException {
        FileContent content = resumeParseApiFacade.viewFile(id);
        String encodedName = java.net.URLEncoder.encode(content.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedName);
        response.getOutputStream().write(content.content());
    }

    @Operation(summary = "重新解析简历", description = "用最新的AI Prompt重新解析已有简历，更新分析结果")
    @PostMapping("/{id}/reparse")
    public R<ResumeParseResponse> reparse(
            @Parameter(description = "解析记录ID") @PathVariable Long id) {
        ResumeParseResponse result = resumeParseApiFacade.reparse(id);
        return R.ok("重新解析任务已提交", result);
    }

    @Operation(summary = "人工重试失败任务", description = "仅允许失败(3)或等待重试(4)状态的记录重新投递到主队列，区别于 reparse（用户主动重新执行），retry 表示系统失败恢复")
    @PostMapping("/{id}/retry")
    public R<ResumeParseResponse> retryFailedTask(
            @Parameter(description = "解析记录ID") @PathVariable Long id) {
        ResumeParseResponse result = resumeParseApiFacade.retryFailedTask(id);
        return R.ok("任务已重新投递", result);
    }
}
