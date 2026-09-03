package com.example.matching.controller.learning;

import com.example.matching.application.learning.LearningApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.learning.*;
import com.example.matching.dto.learning.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "学习资源", description = "学习资源管理和学习路径推荐。")
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningApiFacade learningApiFacade;

    @Operation(summary = "保存学习资源", description = "创建或更新学习资源。")
    @PostMapping("/resources")
    public R<LearningResourceResponse> saveResource(@RequestBody LearningResourceSaveDTO dto) {
        return R.ok(learningApiFacade.saveResource(dto));
    }

    @Operation(summary = "分页查询资源", description = "按能力名称、标签、类型、平台、关键词分页查询学习资源。")
    @GetMapping("/resources/page")
    public R<PageResponse<LearningResourceResponse>> pageResources(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "能力名称") @RequestParam(required = false) String abilityName,
            @Parameter(description = "标签ID") @RequestParam(required = false) Long tagId,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "资源平台") @RequestParam(required = false) String platform,
            @Parameter(description = "关键词（标题/描述/能力名）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0禁用，1启用") @RequestParam(required = false) Integer status) {
        return R.ok(learningApiFacade.pageResources(current, size, abilityName, tagId, resourceType, platform, keyword, status));
    }

    @Operation(summary = "获取资源详情", description = "根据ID获取学习资源详情。")
    @GetMapping("/resources/{id}")
    public R<LearningResourceResponse> getResource(@PathVariable Long id) {
        return R.ok(learningApiFacade.getResource(id));
    }

    @Operation(summary = "删除学习资源", description = "根据ID删除学习资源。")
    @PostMapping("/resources/delete/{id}")
    public R<Void> deleteResource(@PathVariable Long id) {
        learningApiFacade.deleteResource(id);
        return R.ok();
    }

    @Operation(summary = "上传资源封面", description = "上传资源封面图片（JPG/PNG/GIF/WebP），返回可访问的封面URL，可填入保存接口的 coverImageUrl。")
    @PostMapping("/resources/cover-upload")
    public R<String> uploadResourceCover(@RequestParam("file") MultipartFile file) {
        try {
            return R.ok(learningApiFacade.uploadCover(
                    new CoverImageUploadRequest(file.getBytes(), file.getContentType())));
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "读取上传文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新资源状态", description = "启用或禁用学习资源。")
    @PostMapping("/resources/{id}/status")
    public R<Void> updateResourceStatus(@PathVariable Long id,
                                        @Parameter(description = "状态：0禁用，1启用") @RequestParam Integer status) {
        learningApiFacade.updateResourceStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "批量更新资源状态", description = "批量启用或禁用学习资源。")
    @PostMapping("/resources/batch-status")
    public R<Void> batchUpdateResourceStatus(@RequestBody List<Long> ids,
                                             @Parameter(description = "状态：0禁用，1启用") @RequestParam Integer status) {
        learningApiFacade.batchUpdateResourceStatus(ids, status);
        return R.ok();
    }

    @Operation(summary = "批量删除学习资源", description = "根据ID列表批量删除学习资源。")
    @PostMapping("/resources/batch-delete")
    public R<Void> batchDeleteResources(@RequestBody List<Long> ids) {
        learningApiFacade.batchDeleteResources(ids);
        return R.ok();
    }

    @Operation(summary = "生成学习路径", description = "根据能力名称列表生成推荐学习路径。")
    @GetMapping("/path")
    public R<List<LearningPathItemDTO>> getLearningPath(
            @Parameter(description = "能力名称列表") @RequestParam List<String> abilityNames,
            @Parameter(description = "当前等级") @RequestParam(required = false) Integer currentLevel,
            @Parameter(description = "目标等级") @RequestParam(required = false) Integer targetLevel) {
        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(abilityNames);
        request.setCurrentLevel(currentLevel);
        request.setTargetLevel(targetLevel);
        return R.ok(learningApiFacade.generateLearningPath(request));
    }

    @Operation(summary = "生成AI学习建议",
            description = "基于能力差距诊断和系统资源库，调用AI生成个性化学习建议。AI只能基于系统检索到的资源生成建议，不能凭空编造。")
    @PostMapping("/ai-suggestions")
    public R<AiLearningSuggestionDTO.Response> generateAiSuggestions(
            @RequestBody AiLearningSuggestionDTO.Request request) {
        return R.ok(learningApiFacade.generateAiSuggestions(request));
    }

    @Operation(summary = "获取已缓存的AI学习建议",
            description = "获取指定匹配记录已生成的AI学习建议。")
    @GetMapping("/ai-suggestions/{matchingRecordId}")
    public R<List<AiLearningSuggestionDTO.Response>> getCachedAiSuggestions(
            @PathVariable Long matchingRecordId) {
        return R.ok(learningApiFacade.getCachedAiSuggestions(matchingRecordId));
    }

    @Operation(summary = "生成学习路径计划", description = "从匹配记录生成学习路径计划。")
    @PostMapping("/path/generate")
    public R<LearningPathPlanVO> generatePath(@RequestBody LearningPathGenerateRequest request) {
        return R.ok(learningApiFacade.generatePath(request));
    }

    @Operation(summary = "获取学习路径计划详情", description = "根据ID获取学习路径计划详情。")
    @GetMapping("/path/{id}")
    public R<LearningPathPlanVO> getPath(@PathVariable Long id) {
        return R.ok(learningApiFacade.getPath(id));
    }

    @Operation(summary = "根据匹配记录获取学习路径", description = "根据匹配记录ID获取已生成的学习路径计划。")
    @GetMapping("/path/by-match/{matchingRecordId}")
    public R<LearningPathPlanVO> getPathByMatch(@PathVariable Long matchingRecordId) {
        return R.ok(learningApiFacade.getPathByMatch(matchingRecordId));
    }

    @Operation(summary = "分页查询学习路径计划", description = "分页查询学习路径计划列表。")
    @GetMapping("/path/page")
    public R<PageResponse<LearningPathPlanVO>> pagePaths(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String status) {
        return R.ok(learningApiFacade.pagePaths(current, size, empId, postId, status));
    }

    @Operation(summary = "更新步骤状态", description = "更新学习步骤的状态。")
    @PostMapping("/path/step/{stepId}/status")
    public R<Void> updateStepStatus(@PathVariable Long stepId,
                                    @Valid @RequestBody LearningStepStatusUpdateRequest request) {
        learningApiFacade.updateStepStatus(stepId, request.status());
        return R.ok();
    }

    @Operation(summary = "学习路径资源回填（指定计划）",
            description = "按 abilityName 重新为指定计划的每个步骤绑定真实资源，保留原记录、完成状态与排序。")
    @PostMapping("/path/{planId}/refresh-resources")
    public R<Integer> refreshResourceBindings(@PathVariable Long planId) {
        return R.ok(learningApiFacade.refreshResourceBindings(planId));
    }

    @Operation(summary = "学习路径资源回填（全部计划）",
            description = "对所有学习路径计划执行一次轻量资源回填，保留原记录、完成状态与排序。")
    @PostMapping("/path/refresh-resources")
    public R<Integer> refreshAllResourceBindings() {
        return R.ok(learningApiFacade.refreshAllResourceBindings());
    }

    @Operation(summary = "分页查询项目任务", description = "分页查询学习项目任务列表。")
    @GetMapping("/project-task/page")
    public R<PageResponse<LearningProjectTaskVO>> pageProjectTasks(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String status) {
        return R.ok(learningApiFacade.pageProjectTasks(current, size, planId, empId, status));
    }

    @Operation(summary = "获取项目任务详情", description = "根据ID获取项目任务详情。")
    @GetMapping("/project-task/{id}")
    public R<LearningProjectTaskVO> getProjectTask(@PathVariable Long id) {
        return R.ok(learningApiFacade.getProjectTask(id));
    }

    @Operation(summary = "提交项目任务", description = "提交项目任务成果。")
    @PostMapping("/project-task/{id}/submit")
    public R<LearningProjectSubmissionResponse> submitProjectTask(
            @PathVariable Long id,
            @RequestBody LearningProjectSubmitDTO dto) {
        return R.ok(learningApiFacade.submitProjectTask(id, dto));
    }

    @Operation(summary = "审核项目提交", description = "审核项目任务提交。")
    @PostMapping("/project-submission/{id}/review")
    public R<LearningProjectSubmissionResponse> reviewProjectSubmission(
            @PathVariable Long id,
            @RequestBody LearningProjectReviewDTO dto) {
        return R.ok(learningApiFacade.reviewProjectSubmission(id, dto));
    }

    @Operation(summary = "生成评估题目", description = "为学习路径计划生成评估题目。")
    @PostMapping("/assessment/generate")
    public R<List<LearningAssessmentItemResponse>> generateAssessments(
            @RequestBody LearningAssessmentGenerateRequest request) {
        return R.ok(learningApiFacade.generateAssessments(request));
    }

    @Operation(summary = "获取计划的评估题目", description = "获取指定学习路径计划的所有评估题目。")
    @GetMapping("/assessment/by-plan/{planId}")
    public R<List<LearningAssessmentItemResponse>> getAssessmentsByPlan(@PathVariable Long planId) {
        return R.ok(learningApiFacade.getAssessmentsByPlan(planId));
    }

    @Operation(summary = "提交学习测评答案并评分")
    @PostMapping("/assessment/{id}/answer")
    public R<LearningAssessmentItemResponse> answerAssessment(@PathVariable Long id,
            @Valid @RequestBody LearningAssessmentAnswerRequest request) {
        return R.ok(learningApiFacade.answerAssessment(id, request.answerText()));
    }

    @Operation(summary = "确认学习测评通过后的能力提升")
    @PostMapping("/assessment/confirm-improvement")
    public R<CapabilityClosureResult> confirmAbilityImprovement(
            @Valid @RequestBody LearningAbilityImprovementConfirmRequest request) {
        return R.ok(learningApiFacade.confirmAbilityImprovement(request.planId(), request.stepId()));
    }
}
