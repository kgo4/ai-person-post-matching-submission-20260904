package com.example.matching.ai.context.controller;

import com.example.matching.ai.context.dto.*;
import com.example.matching.ai.context.entity.AiContextPackageSnapshot;
import com.example.matching.application.ai.AiContextApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "AI上下文包", description = "AI读取数据的标准上下文管理。")
@RestController
@RequestMapping("/api/ai-context")
@RequiredArgsConstructor
public class AiContextController {

    private final AiContextApiFacade aiContextApiFacade;

    @Operation(summary = "获取匹配上下文包")
    @GetMapping("/matching/{matchingRecordId}")
    public R<AiContextPackageDTO> buildMatchingContext(@PathVariable Long matchingRecordId) {
        return R.ok(aiContextApiFacade.buildForMatching(matchingRecordId));
    }

    @Operation(summary = "获取最近快照")
    @GetMapping("/matching/{matchingRecordId}/snapshot/latest")
    public R<AiContextPackageSnapshot> getLatestSnapshot(@PathVariable Long matchingRecordId) {
        String businessKey = "MATCHING_RECORD:" + matchingRecordId;
        return R.ok(aiContextApiFacade.findLatestSnapshot("MATCHING_ANALYSIS", businessKey));
    }

    @Operation(summary = "获取来源详情")
    @GetMapping("/source-ref/detail")
    public R<AiContextSourceRefDTO> getSourceRefDetail(@RequestParam String ref) {
        return R.ok(aiContextApiFacade.resolveSourceRef(ref));
    }

    @Operation(summary = "校验来源引用")
    @PostMapping("/source-ref/validate")
    public R<Map<String, Object>> validateRefs(@RequestBody ValidateSourceRefsDTO dto) {
        Map<String, Object> result = new HashMap<>();

        if (dto.getSourceRefs() == null || dto.getSourceRefs().isEmpty()) {
            result.put("validRefs", List.of());
            result.put("invalidRefs", List.of());
            return R.ok(result);
        }

        AiContextPackageDTO contextFromSnapshot = loadContextFromSnapshot(dto.getContextHash());

        if (contextFromSnapshot != null) {
            List<String> validRefs = new ArrayList<>();
            List<String> invalidRefs = new ArrayList<>();

            Set<String> allowedRefs = contextFromSnapshot.getSourceRefs() != null ?
                    contextFromSnapshot.getSourceRefs().stream()
                            .map(AiContextSourceRefDTO::getRef)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()) :
                    Set.of();

            for (String ref : dto.getSourceRefs()) {
                if (allowedRefs.contains(ref)) {
                    validRefs.add(ref);
                } else {
                    invalidRefs.add(ref);
                }
            }

            result.put("validRefs", validRefs);
            result.put("invalidRefs", invalidRefs);
            result.put("contextHash", dto.getContextHash());
            result.put("validatedFromSnapshot", true);
        } else {
            List<String> validRefs = dto.getSourceRefs().stream()
                    .filter(this::isValidSourceRefFormat)
                    .collect(Collectors.toList());
            List<String> invalidRefs = dto.getSourceRefs().stream()
                    .filter(ref -> !isValidSourceRefFormat(ref))
                    .collect(Collectors.toList());
            result.put("validRefs", validRefs);
            result.put("invalidRefs", invalidRefs);
            result.put("validatedFromSnapshot", false);
        }

        return R.ok(result);
    }

    private AiContextPackageDTO loadContextFromSnapshot(String contextHash) {
        if (contextHash == null || contextHash.isEmpty()) {
            return null;
        }

        try {
            AiContextPackageSnapshot snapshot = aiContextApiFacade.findByHash(contextHash);
            if (snapshot != null && snapshot.getPackageJson() != null) {
                return aiContextApiFacade.parsePackageJson(snapshot.getPackageJson());
            }
        } catch (Exception e) {
            log.warn("从快照加载上下文失败: contextHash={}", contextHash, e);
        }

        return null;
    }

    private boolean isValidSourceRefFormat(String ref) {
        if (ref == null || ref.isEmpty()) {
            return false;
        }
        String[] parts = ref.split(":");
        return parts.length >= 3;
    }
}
