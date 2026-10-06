package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelImportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "岗位能力模型导入")
@RestController
@RequestMapping("/api/post/model-import")
@RequiredArgsConstructor
public class PostModelImportController {

    private final PostModelImportApiFacade postModelImportApiFacade;

    @Operation(summary = "解析Excel文件")
    @PostMapping("/parse")
    public R<List<PostModelExcelRowDTO>> parseExcel(
            @Parameter(description = "Excel文件") @RequestParam("file") MultipartFile file) {
        try (java.io.InputStream inputStream = file.getInputStream()) {
            return R.ok(postModelImportApiFacade.parseExcel(inputStream));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded Excel file", exception);
        }
    }

    @Operation(summary = "批量导入（模板B：直接导入权重）")
    @PostMapping("/import/template-b")
    public R<Map<Long, Integer>> importTemplateB(@RequestBody List<PostModelExcelRowDTO> rows) {
        return R.ok(postModelImportApiFacade.batchImportFromTemplateB(rows));
    }

    @Operation(summary = "批量导入（模板A：AI补齐）")
    @PostMapping("/import/template-a")
    public R<Map<Long, Integer>> importTemplateA(@RequestBody List<PostModelExcelRowDTO> rows) {
        return R.ok(postModelImportApiFacade.batchImportFromTemplateA(rows));
    }

    @Operation(summary = "一键归一化权重到100%")
    @PostMapping("/normalize/{postId}")
    public R<List<PostAbilityModelConfigDTO>> normalizeWeights(
            @Parameter(description = "岗位ID") @PathVariable Long postId) {
        return R.ok(postModelImportApiFacade.normalizeWeights(postId));
    }

    @Operation(summary = "复制岗位模型")
    @PostMapping("/copy")
    public R<Integer> copyPostModel(
            @Parameter(description = "源岗位ID") @RequestParam Long sourcePostId,
            @Parameter(description = "目标岗位ID") @RequestParam Long targetPostId) {
        return R.ok(postModelImportApiFacade.copyPostModel(sourcePostId, targetPostId));
    }
}
