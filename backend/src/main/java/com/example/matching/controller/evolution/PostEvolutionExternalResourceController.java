package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.PostEvolutionExternalResourceService;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "岗位演化外部资源")
@RestController
@RequestMapping("/api/post/evolution/external-resources")
@RequiredArgsConstructor
public class PostEvolutionExternalResourceController {
    private static final int MAX_QUERY_LENGTH = 200;
    private final PostEvolutionExternalResourceService service;

    @Operation(summary = "查询岗位演化外部趋势资源")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PostEvolutionExternalResourceService.Result> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int count) {
        if (query == null || query.isBlank()) return R.fail("query_required");
        if (query.trim().length() > MAX_QUERY_LENGTH) return R.fail("query_too_long");
        return R.ok(service.search(query, Math.max(1, Math.min(10, count))));
    }
}
