package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingScoringConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.ScoringWeightUpdateRequest;
import com.example.matching.dto.matching.ScoringWeightVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 匹配评分权重配置（业务评分规则，与模型训练无关）。
 * <p>
 * 从训练中心迁出：不再存在训练版本、自动训练、模型发布等概念。
 */
@Tag(name = "匹配评分配置", description = "人工配置匹配评分权重（业务规则，非模型训练）。")
@RestController
@RequestMapping("/api/matching/scoring-config")
@RequiredArgsConstructor
public class MatchingScoringConfigController {

    private final MatchingScoringConfigApiFacade facade;

    @Operation(summary = "获取当前评分权重配置")
    @GetMapping
    public R<ScoringWeightVO> getConfig() {
        return R.ok(facade.getConfig());
    }

    @Operation(summary = "保存评分权重配置（手工权重）")
    @PutMapping
    public R<Void> saveConfig(@Valid @RequestBody ScoringWeightUpdateRequest request) {
        facade.saveConfig(request);
        return R.ok();
    }
}
