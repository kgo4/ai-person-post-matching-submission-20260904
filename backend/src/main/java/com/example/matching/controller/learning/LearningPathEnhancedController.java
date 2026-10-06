package com.example.matching.controller.learning;

import com.example.matching.application.learning.LearningPathEnhancedApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.dto.learning.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "增强版学习路径", description = "基于知识图谱和掌握度的学习路径推荐")
@RestController
@RequestMapping("/api/learning/path-enhanced")
@RequiredArgsConstructor
public class LearningPathEnhancedController {

    private final LearningPathEnhancedApiFacade facade;

    @Operation(summary = "基于知识图谱生成学习路径")
    @PostMapping("/generate-by-knowledge-graph")
    public R<List<LearningPathItemDTO>> generateLearningPathByKnowledgeGraph(@RequestBody LearningPathRequestDTO request) {
        List<LearningPathItemDTO> result = facade.generateLearningPathByKnowledgeGraph(request);
        return R.ok(result);
    }

    @Operation(summary = "基于掌握度生成学习路径")
    @PostMapping("/generate-by-mastery")
    public R<List<LearningPathItemDTO>> generateLearningPathByMastery(@RequestParam Long empId, @RequestParam Long postId) {
        List<LearningPathItemDTO> result = facade.generateLearningPathByMastery(empId, postId);
        return R.ok(result);
    }

    @Operation(summary = "获取学习路径推荐")
    @GetMapping("/recommendations")
    public R<List<Map<String, Object>>> getLearningPathRecommendations(@RequestParam Long empId, @RequestParam Long postId) {
        List<Map<String, Object>> result = facade.getLearningPathRecommendations(empId, postId);
        return R.ok(result);
    }

    @Operation(summary = "获取领域掌握度")
    @GetMapping("/mastery/domains")
    public R<Map<Long, Double>> getDomainMasteryScores(@RequestParam Long empId) {
        Map<Long, Double> result = facade.getDomainMasteryScores(empId);
        return R.ok(result);
    }

    @Operation(summary = "获取知识点掌握度")
    @GetMapping("/mastery/nodes")
    public R<Map<Long, Double>> getNodeMasteryScores(@RequestParam Long empId, @RequestParam Long domainId) {
        Map<Long, Double> result = facade.getNodeMasteryScores(empId, domainId);
        return R.ok(result);
    }

    @Operation(summary = "获取薄弱环节")
    @GetMapping("/weak-points")
    public R<List<Map<String, Object>>> getWeakPoints(@RequestParam Long empId, @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> result = facade.getWeakPoints(empId, limit);
        return R.ok(result);
    }

    @Operation(summary = "获取领域学习顺序")
    @GetMapping("/order/domains")
    public R<List<KnowledgeDomainResponse>> getDomainLearningOrder(@RequestParam Long empId, @RequestParam Long postId) {
        List<KnowledgeDomainResponse> result = facade.getDomainLearningOrder(empId, postId);
        return R.ok(result);
    }

    @Operation(summary = "获取知识点学习顺序")
    @GetMapping("/order/nodes")
    public R<List<KnowledgeNodeResponse>> getNodeLearningOrder(@RequestParam Long empId, @RequestParam Long domainId) {
        List<KnowledgeNodeResponse> result = facade.getNodeLearningOrder(empId, domainId);
        return R.ok(result);
    }

    @Operation(summary = "更新学习进度")
    @PostMapping("/progress/update")
    public R<Void> updateLearningProgress(@RequestParam Long empId, @RequestParam Long nodeId, @RequestParam String status) {
        facade.updateLearningProgress(empId, nodeId, status);
        return R.ok();
    }

    @Operation(summary = "获取学习进度概览")
    @GetMapping("/progress/overview")
    public R<Map<String, Object>> getLearningProgressOverview(@RequestParam Long empId) {
        Map<String, Object> result = facade.getLearningProgressOverview(empId);
        return R.ok(result);
    }

    @Operation(summary = "获取所有知识领域")
    @GetMapping("/domains")
    public R<List<KnowledgeDomainResponse>> getAllDomains() {
        List<KnowledgeDomainResponse> result = facade.getAllDomains();
        return R.ok(result);
    }

    @Operation(summary = "根据ID获取知识领域")
    @GetMapping("/domains/{id}")
    public R<KnowledgeDomainResponse> getDomainById(@PathVariable Long id) {
        KnowledgeDomainResponse result = facade.getDomainById(id);
        return R.ok(result);
    }

    @Operation(summary = "获取领域下的知识点")
    @GetMapping("/domains/{domainId}/nodes")
    public R<List<KnowledgeNodeResponse>> getNodesByDomainId(@PathVariable Long domainId) {
        List<KnowledgeNodeResponse> result = facade.getNodesByDomainId(domainId);
        return R.ok(result);
    }

    @Operation(summary = "获取所有测验题目")
    @GetMapping("/quizzes")
    public R<List<LearningQuizResponse>> getAllQuizzes() {
        List<LearningQuizResponse> result = facade.getAllQuizzes();
        return R.ok(result);
    }

    @Operation(summary = "根据领域ID获取测验题目")
    @GetMapping("/quizzes/domain/{domainId}")
    public R<List<LearningQuizResponse>> getQuizzesByDomainId(@PathVariable Long domainId) {
        List<LearningQuizResponse> result = facade.getQuizzesByDomainId(domainId);
        return R.ok(result);
    }

    @Operation(summary = "根据知识点ID获取测验题目")
    @GetMapping("/quizzes/node/{nodeId}")
    public R<List<LearningQuizResponse>> getQuizzesByNodeId(@PathVariable Long nodeId) {
        List<LearningQuizResponse> result = facade.getQuizzesByNodeId(nodeId);
        return R.ok(result);
    }

    @Operation(summary = "提交答题记录")
    @PostMapping("/quizzes/submit")
    public R<LearningQuizRecordResponse> submitQuizRecord(@RequestBody LearningQuizRecordRequest request) {
        LearningQuizRecordResponse result = facade.submitQuizRecord(request);
        return R.ok(result);
    }

    @Operation(summary = "获取员工答题记录")
    @GetMapping("/quizzes/records/{empId}")
    public R<List<LearningQuizRecordResponse>> getQuizRecordsByEmpId(@PathVariable Long empId) {
        List<LearningQuizRecordResponse> result = facade.getQuizRecordsByEmpId(empId);
        return R.ok(result);
    }
}
