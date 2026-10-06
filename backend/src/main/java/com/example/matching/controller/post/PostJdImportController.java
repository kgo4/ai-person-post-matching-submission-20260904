package com.example.matching.controller.post;

import com.example.matching.application.post.PostJdImportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.JdAnalyzeRequestDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;
import com.example.matching.dto.post.JdConfirmRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "JD智能导入")
@RestController
@RequestMapping("/api/post/jd-import")
@RequiredArgsConstructor
public class PostJdImportController {

    private final PostJdImportApiFacade postJdImportApiFacade;

    @Operation(summary = "AI分析JD提取能力项")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分析成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "404", description = "岗位不存在")
    })
    @PostMapping("/analyze")
    public R<JdAnalyzeResponseDTO> analyze(
            @Parameter(description = "JD分析请求") @Valid @RequestBody JdAnalyzeRequestDTO request) {
        JdAnalyzeResponseDTO result = postJdImportApiFacade.analyzeJd(request.getPostId(), request.getJdText());
        return R.ok(result);
    }

    @Operation(summary = "确认并应用分析结果")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "应用成功"),
            @ApiResponse(responseCode = "400", description = "参数错误")
    })
    @PostMapping("/confirm")
    public R<Void> confirm(
            @Parameter(description = "确认请求") @Valid @RequestBody JdConfirmRequestDTO request) {
        postJdImportApiFacade.applyAnalysisResult(request.getPostId(), request.getItems());
        return R.ok();
    }
}
