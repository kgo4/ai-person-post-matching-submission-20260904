package com.example.matching.controller.system;

import com.example.matching.application.system.PromptAdminApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Prompt 管理 API —— 查看 / 热重载 / A/B 实验
 */
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@Tag(name = "Prompt 管理", description = "Prompt 文件查看和热重载")
public class PromptAdminController {

    private final PromptAdminApiFacade promptAdminApiFacade;

    @GetMapping
    @Operation(summary = "列出所有 Prompt 文件")
    public R<Map<String, Object>> listPrompts() {
        return R.ok(promptAdminApiFacade.listPrompts());
    }

    @PostMapping("/reload")
    @Operation(summary = "热重载 FTL Prompt（清除模板缓存）")
    public R<Map<String, Object>> reload() {
        return R.ok(promptAdminApiFacade.reload());
    }

    @GetMapping("/experiments")
    @Operation(summary = "查询 Prompt 实验效果数据")
    public R<Map<String, Object>> experimentResults(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String promptName) {
        return R.ok(promptAdminApiFacade.getExperimentResults(days, promptName));
    }
}
