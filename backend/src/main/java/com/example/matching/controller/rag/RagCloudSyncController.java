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

    /**
     * 更新云端知识库配置。
     *
     * <p>⚠️ **已停用运行期修改**：连接信息（服务地址、AK/SK、资源 ID、集合名称）只支持
     * **系统启动时由服务端配置提供**。原先的实现只改内存不落库，界面提示「保存成功」
     * 但重启即回滚 —— 属于静默失效；且该端点不在
     * {@code OperationLogRedactor.SENSITIVE_ENDPOINTS} 中，请求体里的密钥会明文进操作日志。
     * 故改为显式拒绝并说明原因。
     */
    @Operation(summary = "更新云端知识库配置（已停用运行期修改）",
            description = "连接信息只在系统启动时由服务端配置提供，运行期不支持修改；调用本接口会返回业务异常说明。")
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
