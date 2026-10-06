package com.example.matching.controller.employee;

import com.example.matching.application.employee.PmsAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.PmsAnalysisTaskResponse;
import com.example.matching.dto.employee.api.PmsRosterResponse;
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

    // ------------------------------------------------------------------
    // PMS 人员花名册（HR 独立功能：管理 PMS 上的人，与人员库解耦）
    // ------------------------------------------------------------------

    @Operation(summary = "PMS 人员花名册",
            description = "以 PMS 平台人员为主键，返回是否绑定本系统员工、绑定到谁、分析情况；"
                    + "同步过来但还没有本系统账号的人也在列表里（bound=false）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "403", description = "无权限（需 ASSESSMENT:MANAGE）")
    })
    @GetMapping("/roster")
    public R<PmsRosterResponse> roster() {
        return R.ok(pmsAbilityApiFacade.roster());
    }

    @Operation(summary = "绑定 PMS 人员到员工",
            description = "把 PMS 平台上的人与本系统员工绑成一个人（员工注册后再绑定是主场景）；"
                    + "任一方向已有绑定则返回 409，不静默抢绑")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "绑定成功"),
            @ApiResponse(responseCode = "404", description = "员工或 PMS 人员不存在"),
            @ApiResponse(responseCode = "409", description = "员工或 PMS 人员已有绑定")
    })
    @PostMapping("/roster/{pmsUserId}/bind")
    public R<PmsUserMappingResponse> bindRosterUser(
            @Parameter(description = "PMS 用户ID", required = true) @PathVariable Long pmsUserId,
            @Parameter(description = "本系统员工ID", required = true) @RequestParam Long empId) {
        PmsUserMappingResponse mapping = pmsAbilityApiFacade.bindRosterUser(pmsUserId, empId);
        return R.ok("绑定成功", mapping);
    }

    @Operation(summary = "解绑 PMS 人员",
            description = "解除 PMS 人员与员工的绑定关系；已导入的员工能力不会被删除")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解绑成功"),
            @ApiResponse(responseCode = "404", description = "该 PMS 人员尚未同步")
    })
    @DeleteMapping("/roster/{pmsUserId}/bind")
    public R<Void> unbindRosterUser(
            @Parameter(description = "PMS 用户ID", required = true) @PathVariable Long pmsUserId) {
        pmsAbilityApiFacade.unbindRosterUser(pmsUserId);
        return R.ok("已解绑", null);
    }

    @Operation(summary = "按 PMS 人员执行项目数据分析",
            description = "以 PMS 人员为入口发起分析；未绑定员工时也允许，此时任务 emp_id 为空")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分析完成"),
            @ApiResponse(responseCode = "404", description = "PMS 用户不存在"),
            @ApiResponse(responseCode = "500", description = "分析失败")
    })
    @PostMapping("/roster/{pmsUserId}/analyze")
    public R<PmsAnalysisTaskResponse> analyzeByPmsUser(
            @Parameter(description = "PMS 用户ID", required = true) @PathVariable Long pmsUserId,
            @Parameter(description = "分析时间范围（月）", example = "6") @RequestParam(defaultValue = "6") int months) {
        PmsAnalysisTaskResponse task = pmsAbilityApiFacade.analyzeByPmsUser(pmsUserId, months);
        return R.ok("分析完成", task);
    }

    @Operation(summary = "按 PMS 人员查分析历史", description = "不依赖绑定关系，未绑定的 PMS 人员同样可查")
    @GetMapping("/roster/{pmsUserId}/history")
    public R<List<PmsAnalysisTaskResponse>> historyByPmsUser(
            @Parameter(description = "PMS 用户ID", required = true) @PathVariable Long pmsUserId) {
        return R.ok(pmsAbilityApiFacade.getHistoryByPmsUser(pmsUserId));
    }

    // ------------------------------------------------------------------
    // 以本地员工为入口（保留兼容，页面主路径已迁到花名册）
    // ------------------------------------------------------------------

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

    @Operation(summary = "同步PMS人员",
            description = "把 PMS 平台的人登记进来（只写 pms_user_mapping，emp_id 为空表示「已同步未绑定」），"
                    + "**不会创建或修改人员档案**；仅当工号能唯一命中本系统员工时才顺带自动绑定。"
                    + "返回键：newSynced / totalPmsUsers / alreadySynced / autoBound")
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
