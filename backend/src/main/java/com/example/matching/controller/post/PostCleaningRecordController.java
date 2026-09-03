package com.example.matching.controller.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.post.PostCleaningApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostCleaningRecordPageQuery;
import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.dto.post.PostCleaningResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "岗位清洗记录")
@RestController
@RequestMapping("/api/post/cleaning-records")
@RequiredArgsConstructor
public class PostCleaningRecordController {

    private final PostCleaningApiFacade postCleaningApiFacade;

    @Operation(summary = "分页查询清洗记录")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<Page<PostCleaningRecordVO>> pageRecords(
            @Parameter(description = "查询参数") @Valid PostCleaningRecordPageQuery query) {
        Page<PostCleaningRecordVO> result = postCleaningApiFacade.pageRecords(query);
        return R.ok(result);
    }

    @Operation(summary = "查询清洗记录详情")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    @GetMapping("/{id}")
    public R<PostCleaningRecordVO> getRecordDetail(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        PostCleaningRecordVO record = postCleaningApiFacade.getRecordDetail(id);
        if (record == null) {
            return R.fail("清洗记录不存在");
        }
        return R.ok(record);
    }

    @Operation(summary = "重新解析")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "重新解析成功"),
            @ApiResponse(responseCode = "404", description = "记录不存在")
    })
    @PostMapping("/{id}/reparse")
    public R<PostCleaningResult> reparse(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        PostCleaningResult result = postCleaningApiFacade.reparse(id);
        return R.ok(result);
    }
}
