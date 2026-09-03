package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelUnmatchedAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostModelUnmatchedBindRequest;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "岗位模型未匹配能力标签", description = "AI 提取但未匹配已有标签的能力：查询、绑定、忽略（M-07）")
@RestController
@RequestMapping("/api/post/model-version/{versionId}/unmatched-abilities")
@RequiredArgsConstructor
public class PostModelUnmatchedAbilityController {

    private final PostModelUnmatchedAbilityApiFacade unmatchedAbilityApiFacade;

    @Operation(summary = "查询未匹配能力列表", description = "查询版本下 AI 提取但未匹配已有标签的能力列表")
    @GetMapping
    public R<List<UnmatchedAbilityDTO>> list(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        return R.ok(unmatchedAbilityApiFacade.listByVersionId(versionId));
    }

    @Operation(summary = "绑定未匹配能力到已有标签", description = "校验标签存在且启用后，生成版本明细并更新状态")
    @PostMapping("/{id}/bind")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> bind(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId,
            @Parameter(description = "未匹配能力记录ID", required = true) @PathVariable Long id,
            @RequestBody PostModelUnmatchedBindRequest request) {
        unmatchedAbilityApiFacade.bind(versionId, id, request);
        return R.ok();
    }

    @Operation(summary = "忽略未匹配能力", description = "将未匹配能力状态置为 IGNORED")
    @PostMapping("/{id}/ignore")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> ignore(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId,
            @Parameter(description = "未匹配能力记录ID", required = true) @PathVariable Long id) {
        unmatchedAbilityApiFacade.ignore(versionId, id);
        return R.ok();
    }
}
