package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.PostEvolutionApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.AgentProgressVO;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.dto.evolution.PostEvolutionReviewDTO;
import com.example.matching.dto.evolution.api.EvolutionTaskRequest;
import com.example.matching.dto.evolution.api.PostEvolutionChangeItemResponse;
import com.example.matching.dto.evolution.api.PostEvolutionEvidenceResponse;
import com.example.matching.dto.evolution.api.PostEvolutionScheduleConfigResponse;
import com.example.matching.dto.evolution.api.PostEvolutionTaskResponse;
import com.example.matching.dto.evolution.api.ScheduleConfigRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "岗位演化", description = "岗位能力演化分析和变更管理。")
@RestController
@RequestMapping("/api/post/evolution")
@RequiredArgsConstructor
public class PostEvolutionController {

    private final PostEvolutionApiFacade facade;

    @Operation(summary = "上传行业白皮书", description = "上传行业白皮书到演化知识源。")
    @PostMapping("/sources/industry-whitepaper")
    public R<Map<String, Object>> uploadIndustryWhitepaper(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "industry", required = false) String industry,
            @RequestParam(value = "businessDomain", required = false) String businessDomain,
            @RequestParam(value = "trustLevel", defaultValue = "HIGH") String trustLevel,
            @RequestParam(value = "evolutionEnabled", defaultValue = "true") Boolean evolutionEnabled,
            @Parameter(description = "操作人ID") @RequestParam(required = false, defaultValue = "0") Long operatorId) {
        EvolutionSourceUploadDTO dto = new EvolutionSourceUploadDTO();
        dto.setTitle(title);
        dto.setIndustry(industry);
        dto.setBusinessDomain(businessDomain);
        dto.setTrustLevel(trustLevel);
        dto.setEvolutionEnabled(evolutionEnabled);
        try {
            return R.ok(facade.uploadIndustryWhitepaper(file.getOriginalFilename(), file.getBytes(), dto, operatorId));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded document", exception);
        }
    }

    @Operation(summary = "上传内部资料", description = "上传公司内部资料到演化知识源。")
    @PostMapping("/sources/internal-document")
    public R<Map<String, Object>> uploadInternalDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "sourceCategory", defaultValue = "INTERNAL_BUSINESS_UPDATE") String sourceCategory,
            @RequestParam(value = "businessDomain", required = false) String businessDomain,
            @RequestParam(value = "industry", required = false) String industry,
            @RequestParam(value = "trustLevel", defaultValue = "MEDIUM") String trustLevel,
            @RequestParam(value = "evolutionEnabled", defaultValue = "true") Boolean evolutionEnabled,
            @Parameter(description = "操作人ID") @RequestParam(required = false, defaultValue = "0") Long operatorId) {
        EvolutionSourceUploadDTO dto = new EvolutionSourceUploadDTO();
        dto.setTitle(title);
        dto.setSourceCategory(sourceCategory);
        dto.setBusinessDomain(businessDomain);
        dto.setIndustry(industry);
        dto.setTrustLevel(trustLevel);
        dto.setEvolutionEnabled(evolutionEnabled);
        try {
            return R.ok(facade.uploadInternalDocument(file.getOriginalFilename(), file.getBytes(), dto, operatorId));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded document", exception);
        }
    }

    @Operation(summary = "同步云知识库", description = "同步云知识库中的资料到演化知识源。")
    @PostMapping("/sources/cloud-sync")
    public R<Map<String, Object>> syncCloudKnowledge(@RequestBody CloudSyncRequest request) {
        return R.ok(facade.syncCloudKnowledge(request));
    }

    @Operation(summary = "索引知识源文档", description = "对知识源文档进行切片和向量化索引。")
    @PostMapping("/sources/{documentId}/index")
    public R<Map<String, Object>> indexKnowledgeSource(@PathVariable Long documentId) {
        return R.ok(facade.indexKnowledgeSource(documentId));
    }

    @Operation(summary = "运行岗位演化 Agent", description = "运行岗位演化 Agent 生成变更建议。")
    @PostMapping("/agent/run")
    public R<Map<String, Object>> runAgent(@RequestBody PostEvolutionAgentRequest request) {
        return R.ok(facade.runAgent(request));
    }

    @Operation(summary = "获取 Agent 执行进度", description = "获取演化 Agent 的执行进度。")
    @GetMapping("/tasks/{id}/progress")
    public R<AgentProgressVO> getAgentProgress(@PathVariable Long id) {
        return R.ok(facade.getAgentProgress(id));
    }

    @Operation(summary = "创建定时配置", description = "创建岗位演化定时配置。")
    @PostMapping("/schedules")
    public R<PostEvolutionScheduleConfigResponse> createSchedule(
            @RequestBody ScheduleConfigRequest req,
            @Parameter(description = "操作人ID") @RequestParam(required = false, defaultValue = "0") Long operatorId) {
        return R.ok(facade.createSchedule(req, operatorId));
    }

    @Operation(summary = "更新定时配置", description = "更新岗位演化定时配置。")
    @PutMapping("/schedules/{id}")
    public R<PostEvolutionScheduleConfigResponse> updateSchedule(
            @PathVariable Long id,
            @RequestBody ScheduleConfigRequest req) {
        return R.ok(facade.updateSchedule(id, req));
    }

    @Operation(summary = "分页查询定时配置", description = "分页查询岗位演化定时配置。")
    @GetMapping("/schedules/page")
    public R<PageResponse<PostEvolutionScheduleConfigResponse>> pageSchedules(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "岗位ID") @RequestParam(required = false) Long postId) {
        return R.ok(facade.pageSchedules(current, size, postId));
    }

    @Operation(summary = "获取定时配置详情", description = "获取岗位演化定时配置详情。")
    @GetMapping("/schedules/{id}")
    public R<PostEvolutionScheduleConfigResponse> getSchedule(@PathVariable Long id) {
        return R.ok(facade.getSchedule(id));
    }

    @Operation(summary = "删除定时配置", description = "删除岗位演化定时配置。")
    @DeleteMapping("/schedules/{id}")
    public R<Void> deleteSchedule(@PathVariable Long id) {
        facade.deleteSchedule(id);
        return R.ok();
    }

    @Operation(summary = "立即执行定时任务", description = "立即执行指定的定时演化任务。")
    @PostMapping("/schedules/{id}/run-now")
    public R<Map<String, Object>> runScheduleNow(@PathVariable Long id) {
        return R.ok(facade.runScheduleNow(id));
    }

    @Operation(summary = "创建演化任务", description = "创建一个新的岗位演化分析任务。")
    @PostMapping("/tasks")
    public R<PostEvolutionTaskResponse> createTask(
            @RequestBody EvolutionTaskRequest req,
            @Parameter(description = "创建人ID") @RequestParam(required = false, defaultValue = "0") Long userId) {
        return R.ok(facade.createTask(req, userId));
    }

    @Operation(summary = "执行演化分析", description = "对指定任务执行RAG增强的演化分析。")
    @PostMapping("/tasks/{id}/analyze")
    public R<PostEvolutionTaskResponse> analyzeTask(@PathVariable Long id) {
        return R.ok(facade.analyzeTask(id));
    }

    @Operation(summary = "分页查询任务", description = "按岗位ID和状态分页查询演化任务。")
    @GetMapping("/tasks/page")
    public R<PageResponse<PostEvolutionTaskResponse>> pageTasks(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "岗位ID") @RequestParam(required = false) Long postId,
            @Parameter(description = "任务状态") @RequestParam(required = false) String taskStatus) {
        return R.ok(facade.pageTasks(current, size, postId, taskStatus));
    }

    @Operation(summary = "获取任务详情", description = "根据ID获取演化任务详情。")
    @GetMapping("/tasks/{id}")
    public R<PostEvolutionTaskResponse> getTask(@PathVariable Long id) {
        return R.ok(facade.getTask(id));
    }

    @Operation(summary = "删除演化任务", description = "删除演化任务及其关联证据、变更项，不影响岗位能力模型。")
    @DeleteMapping("/tasks/{id}")
    public R<Void> deleteTask(@PathVariable Long id) {
        facade.deleteTask(id);
        return R.ok();
    }

    @Operation(summary = "查询变更项", description = "分页查询任务的变更项列表。")
    @GetMapping("/tasks/{id}/items")
    public R<PageResponse<PostEvolutionChangeItemResponse>> pageChangeItems(
            @PathVariable Long id,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "20") long size) {
        return R.ok(facade.pageChangeItems(id, current, size));
    }

    @Operation(summary = "审核变更项", description = "对变更项进行人工审核。")
    @PostMapping("/tasks/{id}/items/{itemId}/review")
    public R<Void> reviewChangeItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestBody PostEvolutionReviewDTO dto) {
        facade.reviewChangeItem(id, itemId, dto);
        return R.ok();
    }

    @Operation(summary = "应用已审核变更", description = "应用所有已审核通过的变更到岗位能力模型。")
    @PostMapping("/tasks/{id}/apply")
    public R<Map<String, Object>> applyApprovedChanges(@PathVariable Long id) {
        return R.ok(facade.applyApprovedChanges(id));
    }

    @Operation(summary = "查询任务证据", description = "获取演化任务的所有证据列表。")
    @GetMapping("/tasks/{id}/evidence")
    public R<List<PostEvolutionEvidenceResponse>> getTaskEvidence(@PathVariable Long id) {
        return R.ok(facade.getTaskEvidence(id));
    }

    @Operation(summary = "查询变更项证据", description = "获取某个变更项的证据链。")
    @GetMapping("/items/{itemId}/evidence")
    public R<List<PostEvolutionEvidenceResponse>> getItemEvidence(@PathVariable Long itemId) {
        return R.ok(facade.getItemEvidence(itemId));
    }

    @Operation(summary = "获取演化时间线", description = "获取岗位能力演化的时序事件列表。")
    @GetMapping("/timeline")
    public R<List<Map<String, Object>>> getTimeline(
            @Parameter(description = "岗位ID") @RequestParam(required = false) Long postId,
            @Parameter(description = "时间范围：7d/30d/90d") @RequestParam(defaultValue = "30d") String range,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "20") int limit) {
        return R.ok(facade.getTimeline(postId, range, limit));
    }

    @Operation(summary = "获取演化统计概览", description = "获取演化任务的统计数据。")
    @GetMapping("/dashboard/stats")
    public R<Map<String, Object>> getDashboardStats(
            @Parameter(description = "时间范围：7d/30d/90d") @RequestParam(defaultValue = "30d") String range) {
        return R.ok(facade.getDashboardStats(range));
    }

    @Operation(summary = "获取演化趋势数据", description = "获取按时间聚合的演化趋势数据。")
    @GetMapping("/dashboard/trends")
    public R<Map<String, Object>> getDashboardTrends(
            @Parameter(description = "时间范围：7d/30d/90d") @RequestParam(defaultValue = "30d") String range) {
        return R.ok(facade.getDashboardTrends(range));
    }

    @Operation(summary = "获取岗位能力演化图谱", description = "获取指定岗位的能力演化关系图数据。")
    @GetMapping("/graph/{postId}")
    public R<Map<String, Object>> getEvolutionGraph(
            @PathVariable Long postId,
            @Parameter(description = "时间点（ISO格式）") @RequestParam(required = false) String timePoint) {
        return R.ok(facade.getEvolutionGraph(postId, timePoint));
    }
}
