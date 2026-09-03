package com.example.matching.controller.employee;

import com.example.matching.application.employee.PmsAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.PmsAnalysisTaskResponse;
import com.example.matching.dto.employee.api.PmsUserMappingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "PMS项目数据分析", description = "从PMS项目管理系统采集员工工作数据，通过AI分析提取能力标签")
@RestController
@RequestMapping("/api/employee/ability/pms")
@RequiredArgsConstructor
public class PmsAbilityController {

    private final PmsAbilityApiFacade pmsAbilityApiFacade;

    @Operation(summary = "自动映射PMS用户", description = "通过工号或姓名自动匹配PMS系统用户")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "映射成功"),
            @ApiResponse(responseCode = "400", description = "未找到匹配的PMS用户")
    })
    @PostMapping("/auto-map")
    public R<PmsUserMappingResponse> autoMap(
            @Parameter(description = "本地员工ID", required = true) @RequestParam Long empId) {
        PmsUserMappingResponse mapping = pmsAbilityApiFacade.autoMapUser(empId);
        if (mapping == null) {
            return R.fail("未找到匹配的PMS用户，请手动映射");
        }
        return R.ok("映射成功", mapping);
    }

    @Operation(summary = "手动映射PMS用户", description = "手动指定本地员工与PMS用户的对应关系")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "映射成功"),
            @ApiResponse(responseCode = "404", description = "员工或PMS用户不存在")
    })
    @PostMapping("/manual-map")
    public R<PmsUserMappingResponse> manualMap(
            @Parameter(description = "本地员工ID", required = true) @RequestParam Long empId,
            @Parameter(description = "PMS用户ID", required = true) @RequestParam Long pmsUserId) {
        PmsUserMappingResponse mapping = pmsAbilityApiFacade.manualMapUser(empId, pmsUserId);
        return R.ok("映射成功", mapping);
    }

    @Operation(summary = "获取映射信息", description = "获取本地员工的PMS用户映射")
    @GetMapping("/mapping/{empId}")
    public R<PmsUserMappingResponse> getMapping(
            @Parameter(description = "本地员工ID", required = true) @PathVariable Long empId) {
        PmsUserMappingResponse mapping = pmsAbilityApiFacade.getMapping(empId);
        return R.ok(mapping);
    }

    @Operation(summary = "执行PMS数据分析", description = "从PMS系统采集员工项目工作数据，通过AI分析提取能力标签")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分析成功"),
            @ApiResponse(responseCode = "400", description = "未找到PMS用户映射"),
            @ApiResponse(responseCode = "500", description = "分析失败")
    })
    @PostMapping("/analyze")
    public R<PmsAnalysisTaskResponse> analyze(
            @Parameter(description = "本地员工ID", required = true) @RequestParam Long empId,
            @Parameter(description = "分析时间范围（月）", example = "6") @RequestParam(defaultValue = "6") int months) {
        PmsAnalysisTaskResponse task = pmsAbilityApiFacade.analyze(empId, months);
        return R.ok("分析完成", task);
    }

    @Operation(summary = "获取分析历史", description = "获取员工的PMS数据分析历史记录")
    @GetMapping("/history/{empId}")
    public R<List<PmsAnalysisTaskResponse>> getHistory(
            @Parameter(description = "本地员工ID", required = true) @PathVariable Long empId) {
        List<PmsAnalysisTaskResponse> history = pmsAbilityApiFacade.getHistory(empId);
        return R.ok(history);
    }

    @Operation(summary = "获取PMS用户列表", description = "获取PMS系统所有用户，用于手动映射选择")
    @GetMapping("/pms-users")
    public R<List<Map<String, Object>>> listPmsUsers() {
        List<Map<String, Object>> users = pmsAbilityApiFacade.listPmsUsers();
        return R.ok(users);
    }

    @Operation(summary = "测试PMS连接", description = "测试PMS数据库连接是否正常")
    @GetMapping("/test-connection")
    public R<Boolean> testConnection() {
        boolean connected = pmsAbilityApiFacade.testConnection();
        return R.ok(connected);
    }

    @Operation(summary = "同步PMS用户", description = "通过工号自动匹配，为匹配系统中的员工建立PMS用户映射")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "同步完成")
    })
    @PostMapping("/sync")
    public R<Map<String, Object>> syncPmsUsers() {
        Map<String, Object> data = pmsAbilityApiFacade.syncPmsUsers();
        return R.ok("同步完成", data);
    }

    @Operation(summary = "获取分析结果详情", description = "获取PMS分析任务的详细结果，包含AI提取的能力列表")
    @GetMapping("/detail/{taskId}")
    public R<Map<String, Object>> getDetail(
            @Parameter(description = "分析任务ID", required = true) @PathVariable Long taskId) {
        Map<String, Object> detail = pmsAbilityApiFacade.getDetail(taskId);
        return R.ok(detail);
    }

    @Operation(summary = "导入能力到档案", description = "将PMS分析提取的能力导入到员工能力档案")
    @PostMapping("/import")
    public R<Map<String, Object>> importAbilities(
            @Parameter(description = "员工ID", required = true) @RequestParam Long empId,
            @Parameter(description = "分析任务ID", required = true) @RequestParam Long taskId,
            @Parameter(description = "选中的能力索引列表（不传则导入全部）") @RequestBody(required = false) List<Integer> indexes) {
        int count = pmsAbilityApiFacade.importAbilities(empId, taskId, indexes);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("importedCount", count);
        return R.ok("导入成功", data);
    }
}
