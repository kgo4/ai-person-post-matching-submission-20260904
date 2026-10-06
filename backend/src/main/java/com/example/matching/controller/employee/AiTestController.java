package com.example.matching.controller.employee;

import com.example.matching.application.employee.AiTestApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.AiTestAnswerSubmitRequest;
import com.example.matching.dto.employee.api.AiTestResponse;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI能力测试", description = "AI能力测试管理接口，支持生成测试题目、提交答案、AI自动批阅和生成分析报告")
@RestController
@RequestMapping("/api/employee/ability/ai-test")
@RequiredArgsConstructor
public class AiTestController {

    private final AiTestApiFacade aiTestApiFacade;

    @Operation(summary = "基于岗位生成综合能力测试", description = "根据岗位的能力模型为员工生成覆盖多项能力的综合测试")
    @PostMapping("/generate-by-post")
    public R<AiTestResponse> generatePostTest(
            @Parameter(description = "员工ID") @RequestParam Long empId,
            @Parameter(description = "岗位ID") @RequestParam Long postId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiTestResponse test = aiTestApiFacade.generatePostTest(empId, postId, userId);
        return R.ok("综合测试题目已生成", test);
    }

    @Operation(summary = "生成AI测试", description = "为指定员工的某项能力生成AI测试题目")
    @PostMapping("/generate")
    public R<AiTestResponse> generateTest(
            @Parameter(description = "员工ID") @RequestParam Long empId,
            @Parameter(description = "能力标签ID") @RequestParam Long abilityTagId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AiTestResponse test = aiTestApiFacade.generateTest(empId, abilityTagId, userId);
        return R.ok("测试题目已生成", test);
    }

    @Operation(summary = "提交答案", description = "提交员工的测试答案，系统将自动调用AI进行批阅")
    @PostMapping("/{id}/submit")
    public R<AiTestResponse> submitAnswers(
            @Parameter(description = "测试ID") @PathVariable Long id,
            @Valid @RequestBody AiTestAnswerSubmitRequest request) {
        AiTestResponse result = aiTestApiFacade.submitAnswers(id, request.answers());
        return R.ok("答案已提交，AI批阅完成", result);
    }

    @Operation(summary = "获取测试结果", description = "获取测试的详细结果，包括AI批阅和分析报告")
    @GetMapping("/{id}/result")
    public R<AiTestResponse> getTestResult(
            @Parameter(description = "测试ID") @PathVariable Long id) {
        return R.ok(aiTestApiFacade.getTestResult(id));
    }

    @Operation(summary = "查询员工测试列表", description = "获取指定员工的所有AI测试记录")
    @GetMapping("/list/{empId}")
    public R<List<AiTestResponse>> listByEmpId(
            @Parameter(description = "员工ID") @PathVariable Long empId) {
        return R.ok(aiTestApiFacade.listByEmpId(empId));
    }

    @Operation(summary = "导入测试结果到能力档案", description = "将AI测试评估的能力等级导入到员工能力档案")
    @PostMapping("/{id}/import")
    public R<Boolean> importToAbilityProfile(
            @Parameter(description = "测试ID") @PathVariable Long id) {
        boolean result = aiTestApiFacade.importToAbilityProfile(id);
        return R.ok("测试结果已导入能力档案", result);
    }

    @Operation(summary = "重放失败任务", description = "仅允许 FAILED 状态的任务重放为 PENDING 并重新投递，操作写系统审计日志")
    @PostMapping("/{id}/redeliver")
    public R<Boolean> redeliverTask(
            @Parameter(description = "测试ID") @PathVariable Long id) {
        boolean result = aiTestApiFacade.redeliverTask(id);
        return R.ok("任务已重新投递", result);
    }
}
