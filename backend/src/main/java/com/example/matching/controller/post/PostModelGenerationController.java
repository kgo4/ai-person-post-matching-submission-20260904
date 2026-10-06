package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelGenerationApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostModelVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "岗位模型生成中心", description = "从原型、JD、复制等方式生成岗位能力模型草稿")
@RestController
@RequestMapping("/api/post/model-generation")
@RequiredArgsConstructor
public class PostModelGenerationController {

    private final PostModelGenerationApiFacade modelGenerationApiFacade;

    @Operation(summary = "从岗位原型生成", description = "选择岗位原型，一键生成能力模型草稿")
    @PostMapping("/from-prototype")
    public R<PostModelVersionResponse> generateFromPrototype(
            @Parameter(description = "岗位ID", required = true) @RequestParam Long postId,
            @Parameter(description = "原型ID", required = true) @RequestParam Long prototypeId,
            @Parameter(description = "版本说明") @RequestParam(required = false) String description) {
        return R.ok(modelGenerationApiFacade.generateFromPrototype(postId, prototypeId, description));
    }

    @Operation(summary = "从JD智能生成", description = "粘贴岗位JD，AI自动提取能力要求生成草稿")
    @PostMapping("/from-jd")
    public R<PostModelVersionResponse> generateFromJD(
            @Parameter(description = "岗位ID", required = true) @RequestParam Long postId,
            @Parameter(description = "JD文本", required = true)
            @Valid @RequestBody com.example.matching.dto.post.PostModelGenerationFromJdDTO dto,
            @Parameter(description = "版本说明") @RequestParam(required = false) String description) {
        return R.ok(modelGenerationApiFacade.generateFromJD(postId, dto.jdText(), description));
    }

    @Operation(summary = "从已有岗位复制", description = "复制源岗位的能力模型配置到目标岗位")
    @PostMapping("/from-copy")
    public R<PostModelVersionResponse> generateFromCopy(
            @Parameter(description = "源岗位ID", required = true) @RequestParam Long sourcePostId,
            @Parameter(description = "目标岗位ID", required = true) @RequestParam Long targetPostId,
            @Parameter(description = "版本说明") @RequestParam(required = false) String description) {
        return R.ok(modelGenerationApiFacade.generateFromCopy(sourcePostId, targetPostId, description));
    }
}
