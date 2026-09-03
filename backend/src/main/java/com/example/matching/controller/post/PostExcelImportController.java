package com.example.matching.controller.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.post.PostExcelApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.dto.post.PostImportConfirmDTO;
import com.example.matching.dto.post.PostImportPreviewDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
@Tag(name = "Excel岗位批量导入", description = "通过AI智能解析Excel文件，批量导入岗位并自动生成能力模型")
@RestController
@RequestMapping("/api/post/excel-import")
@RequiredArgsConstructor
public class PostExcelImportController {

    private final PostExcelApiFacade postExcelApiFacade;

    @Operation(summary = "下载岗位JD导入模板", description = "下载使用固定列名的Excel模板，避免表格结构无法识别")
    @GetMapping("/template")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        String fileName = URLEncoder.encode("岗位JD批量导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(postExcelApiFacade.downloadTemplate());
    }

    @Operation(summary = "上传并解析Excel", description = "上传Excel文件，AI自动识别结构并分析每个岗位的能力要求")
    @PostMapping("/upload")
    public R<PostImportPreviewDTO> uploadAndAnalyze(
            @Parameter(description = "Excel文件（xls/xlsx）") @RequestParam("file") MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            return R.ok(postExcelApiFacade.uploadAndAnalyze(file.getOriginalFilename(), inputStream));
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read uploaded Excel file", e);
        }
    }

    @Operation(summary = "触发AI能力分析", description = "对已上传批次中的岗位触发AI能力分析，前端可轮询preview获取进度")
    @PostMapping("/analyze/{batchId}")
    public R<Void> analyzeBatch(@PathVariable Long batchId) {
        postExcelApiFacade.analyzeBatch(batchId);
        return R.ok();
    }

    @Operation(summary = "获取导入预览", description = "获取已上传批次的解析预览结果（含分析进度）")
    @GetMapping("/preview/{batchId}")
    public R<PostImportPreviewDTO> getPreview(@PathVariable Long batchId) {
        PostImportPreviewDTO result = postExcelApiFacade.getPreview(batchId);
        return R.ok(result);
    }

    @Operation(summary = "确认并批量导入", description = "用户编辑确认后，批量创建岗位和能力模型")
    @PostMapping("/confirm")
    public R<Void> confirmAndImport(@Valid @RequestBody PostImportConfirmDTO confirmDTO) {
        postExcelApiFacade.confirmAndImport(confirmDTO);
        return R.ok();
    }

    @Operation(summary = "将已完成批次纳入市场发现", description = "复用已保存的岗位能力模型，不重新调用AI分析")
    @PostMapping("/{batchId}/include-market-jd")
    public R<Integer> includeBatchInMarketDiscovery(@PathVariable Long batchId) {
        return R.ok(postExcelApiFacade.includeBatchInMarketDiscovery(batchId));
    }

    @Operation(summary = "取消分析任务", description = "取消正在进行的AI能力分析")
    @PostMapping("/cancel/{batchId}")
    public R<Void> cancelBatch(@PathVariable Long batchId) {
        postExcelApiFacade.cancelBatch(batchId);
        return R.ok();
    }

    @Operation(summary = "分页查询导入批次", description = "查询导入记录列表，支持按状态筛选")
    @GetMapping("/page")
    public R<Page<PostImportBatchVO>> pageBatches(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "导入状态筛选") @RequestParam(required = false) Integer importStatus) {
        Page<PostImportBatchVO> result = postExcelApiFacade.pageBatches(current, size, importStatus);
        return R.ok(result);
    }

    @Operation(summary = "重试批次分析", description = "重新触发失败或已取消批次的AI分析")
    @PostMapping("/retry/{batchId}")
    public R<Void> retryBatch(@PathVariable Long batchId) {
        postExcelApiFacade.retryBatch(batchId);
        return R.ok();
    }

    @Operation(summary = "删除导入批次", description = "删除批次记录和临时明细，不影响已导入岗位")
    @DeleteMapping("/{batchId}")
    public R<Void> deleteBatch(@PathVariable Long batchId) {
        postExcelApiFacade.deleteBatch(batchId);
        return R.ok();
    }
}
