package com.example.matching.controller.system;

import com.example.matching.application.system.SystemAiModelConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.SystemAiModelConfigDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "企业 AI 模型配置", description = "全局唯一企业模型配置，所有文本 AI 业务统一使用。")
@RestController
@RequestMapping("/api/system/ai-model-config")
@RequiredArgsConstructor
public class SystemAiModelConfigController {

    private final SystemAiModelConfigApiFacade facade;

    @Operation(summary = "获取企业模型配置（永不返回 apiKey）")
    @GetMapping
    public R<SystemAiModelConfigDTO> getConfig() {
        return R.ok(facade.getConfig());
    }

    @Operation(summary = "保存企业模型配置（未传 apiKey 时保留旧密钥）")
    @PutMapping
    public R<SystemAiModelConfigDTO> saveConfig(@RequestBody SystemAiModelConfigDTO dto) {
        return R.ok(facade.saveConfig(dto));
    }

    @Operation(summary = "健康检查（只检查连通性与模型响应，不发送真实业务文本）")
    @PostMapping("/health-check")
    public R<Map<String, Object>> healthCheck() {
        return R.ok(facade.healthCheck());
    }
}
