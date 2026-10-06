package com.example.matching.controller.workbench;

import com.example.matching.application.workbench.WorkbenchMetricsApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.workbench.WorkbenchMetricsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台指标接口。
 *
 * <p>一次性返回 KPI 卡环比与团队成员，避免前端为每张卡单独发请求。
 * 返回内容按当前登录角色收口：员工本人拿不到团队成员区块。</p>
 */
@Tag(name = "工作台", description = "角色工作台 KPI 环比与团队成员摘要")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchMetricsController {

    private final WorkbenchMetricsApiFacade workbenchMetricsApiFacade;

    @Operation(summary = "获取工作台指标", description = "返回 KPI 卡环比与团队成员；员工角色不返回团队成员")
    @GetMapping("/metrics")
    public R<WorkbenchMetricsVO> metrics() {
        return R.ok(workbenchMetricsApiFacade.metrics());
    }
}
