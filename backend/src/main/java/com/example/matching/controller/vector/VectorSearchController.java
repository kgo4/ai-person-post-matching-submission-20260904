package com.example.matching.controller.vector;

import com.example.matching.application.vectorsearch.VectorSearchApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "向量搜索", description = "基于Milvus向量数据库的语义相似度搜索模块")
@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
public class VectorSearchController {

    private final VectorSearchApiFacade vectorSearchApiFacade;

    @Operation(summary = "为岗位搜索最匹配的员工")
    @GetMapping("/search-employees")
    public R<List<Map<String, Object>>> searchEmployees(
            @Parameter(description = "岗位描述文本") @RequestParam String postText,
            @Parameter(description = "返回结果数量上限") @RequestParam(defaultValue = "10") int topK) {
        return R.ok(vectorSearchApiFacade.searchEmployeesForPost(postText, topK));
    }

    @Operation(summary = "为员工搜索最匹配的岗位")
    @GetMapping("/search-posts")
    public R<List<Map<String, Object>>> searchPosts(
            @Parameter(description = "员工画像描述文本") @RequestParam String empText,
            @Parameter(description = "返回结果数量上限") @RequestParam(defaultValue = "10") int topK) {
        return R.ok(vectorSearchApiFacade.searchPostsForEmployee(empText, topK));
    }
}
