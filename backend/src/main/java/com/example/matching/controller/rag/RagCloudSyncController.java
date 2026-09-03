package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagCloudSyncApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "云端知识库", description = "云端知识库状态、检索和同步。")
@RestController
@RequestMapping("/api/rag/cloud")
@RequiredArgsConstructor
public class RagCloudSyncController {

    private final RagCloudSyncApiFacade ragCloudSyncApiFacade;

    @Operation(summary = "获取云端知识库状态")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(ragCloudSyncApiFacade.getStatus());
    }

    @PutMapping("/config")
    public R<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> request) {
        return R.ok(ragCloudSyncApiFacade.updateConfig(request));
    }

    @Operation(summary = "同步本地知识到云端")
    @PostMapping("/sync")
    public R<Map<String, Object>> sync(
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return R.ok(ragCloudSyncApiFacade.sync(sourceType, limit, dryRun));
    }

    @Operation(summary = "云端检索测试")
    @GetMapping("/search")
    public R<Map<String, Object>> search(
            @RequestParam String queryText,
            @RequestParam(defaultValue = "JD_ABILITY_EXTRACT") String scenario) {
        return R.ok(ragCloudSyncApiFacade.search(queryText, scenario));
    }
}
