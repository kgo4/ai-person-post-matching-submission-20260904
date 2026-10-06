package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagKnowledgeApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.api.KnowledgeDocumentCreateRequest;
import com.example.matching.dto.rag.api.KnowledgeDocumentResponse;
import com.example.matching.dto.rag.api.KnowledgeDocumentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "RAG知识库", description = "RAG知识文档管理、分块索引和检索。")
@RestController
@RequestMapping("/api/rag/knowledge")
@RequiredArgsConstructor
public class RagKnowledgeController {

    private final RagKnowledgeApiFacade facade;

    @Operation(summary = "创建知识文档", description = "手动创建一条知识文档。")
    @PostMapping("/documents")
    public R<KnowledgeDocumentResponse> createDocument(@RequestBody KnowledgeDocumentCreateRequest req) {
        return R.ok(facade.createDocument(req));
    }

    @Operation(summary = "更新知识文档", description = "更新一条知识文档。")
    @PutMapping("/documents/{id}")
    public R<KnowledgeDocumentResponse> updateDocument(@PathVariable Long id, @RequestBody KnowledgeDocumentUpdateRequest req) {
        return R.ok(facade.updateDocument(id, req));
    }

    @Operation(summary = "分页查询知识文档", description = "按来源类型、状态、标题分页查询知识文档。")
    @GetMapping("/documents/page")
    public R<PageResponse<KnowledgeDocumentResponse>> pageDocuments(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "来源类型") @RequestParam(required = false) String sourceType,
            @Parameter(description = "文档状态") @RequestParam(required = false) String docStatus,
            @Parameter(description = "标题模糊搜索") @RequestParam(required = false) String title) {
        return R.ok(facade.pageDocuments(current, size, sourceType, docStatus, title));
    }

    @Operation(summary = "获取文档详情", description = "根据ID获取知识文档详情。")
    @GetMapping("/documents/{id}")
    public R<KnowledgeDocumentResponse> getDocument(@PathVariable Long id) {
        return R.ok(facade.getDocument(id));
    }

    @Operation(summary = "索引文档", description = "对文档进行分块和向量化索引。")
    @PostMapping("/documents/{id}/index")
    public R<Map<String, Object>> indexDocument(@PathVariable Long id) {
        return R.ok(facade.indexDocument(id));
    }

    @Operation(summary = "批量索引文档", description = "按来源批量索引知识文档。")
    @PostMapping("/documents/index")
    public R<Map<String, Object>> indexDocuments(
            @Parameter(description = "来源类型") @RequestParam(required = false) String sourceType,
            @Parameter(description = "是否只索引未索引文档") @RequestParam(defaultValue = "true") boolean onlyUnindexed,
            @Parameter(description = "最大处理数量") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(facade.indexDocuments(sourceType, onlyUnindexed, limit));
    }

    @Operation(summary = "回填知识文档", description = "从现有数据源回填知识文档。")
    @PostMapping("/documents/backfill")
    public R<Map<String, Object>> backfill(
            @Parameter(description = "来源类型") @RequestParam String sourceType,
            @Parameter(description = "最大回填数量") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(facade.backfill(sourceType, limit));
    }

    @Operation(summary = "搜索知识分块", description = "根据查询文本检索最相关的知识分块。")
    @GetMapping("/chunks/search")
    public R<List<KnowledgeChunkResultDTO>> searchChunks(
            @Parameter(description = "查询文本") @RequestParam String queryText,
            @Parameter(description = "RAG场景") @RequestParam(required = false) String scenario,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") Integer topK,
            @Parameter(description = "来源类型过滤") @RequestParam(required = false) List<String> sourceTypes) {
        return R.ok(facade.searchChunks(queryText, scenario, topK, sourceTypes));
    }
}
