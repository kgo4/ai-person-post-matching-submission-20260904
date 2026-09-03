package com.example.matching.application.learning;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.learning.*;
import com.example.matching.dto.learning.api.*;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.service.learning.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LearningApiFacade {

    private final LearningResourceService learningResourceService;
    private final LearningPathService learningPathService;
    private final AiLearningSuggestionService aiLearningSuggestionService;
    private final LearningPathPlanService learningPathPlanService;
    private final LearningProjectTaskService learningProjectTaskService;
    private final LearningAssessmentService learningAssessmentService;

    public LearningResourceResponse saveResource(LearningResourceSaveDTO dto) {
        LearningResource entity = learningResourceService.saveResource(dto);
        return toResponse(entity);
    }

    public PageResponse<LearningResourceResponse> pageResources(long current, long size, String abilityName, Long tagId, String resourceType, String platform, String keyword, Integer status) {
        LearningResourceQueryDTO query = new LearningResourceQueryDTO();
        query.setAbilityName(abilityName);
        query.setTagId(tagId);
        query.setResourceType(resourceType);
        query.setPlatform(platform);
        query.setKeyword(keyword);
        query.setStatus(status);
        IPage<LearningResource> page = learningResourceService.pageResources(new Page<>(current, size), query);
        return PageResponse.from(page, this::toResponse);
    }

    public LearningResourceResponse getResource(Long id) {
        LearningResource entity = learningResourceService.getResourceById(id);
        return toResponse(entity);
    }

    public void deleteResource(Long id) {
        learningResourceService.deleteResource(id);
    }

    public void updateResourceStatus(Long id, Integer status) {
        learningResourceService.updateStatus(id, status);
    }

    public void batchUpdateResourceStatus(List<Long> ids, Integer status) {
        learningResourceService.batchUpdateStatus(ids, status);
    }

    public void batchDeleteResources(List<Long> ids) {
        learningResourceService.batchDelete(ids);
    }

    public String uploadCover(CoverImageUploadRequest request) {
        return learningResourceService.uploadCover(request);
    }

    public List<LearningPathItemDTO> generateLearningPath(LearningPathRequestDTO request) {
        return learningPathService.generateLearningPath(request);
    }

    public AiLearningSuggestionDTO.Response generateAiSuggestions(AiLearningSuggestionDTO.Request request) {
        return aiLearningSuggestionService.generateSuggestions(request);
    }

    public List<AiLearningSuggestionDTO.Response> getCachedAiSuggestions(Long matchingRecordId) {
        return aiLearningSuggestionService.getCachedSuggestions(matchingRecordId);
    }

    public LearningPathPlanVO generatePath(LearningPathGenerateRequest request) {
        return learningPathPlanService.generateFromMatchingRecord(request);
    }

    public LearningPathPlanVO getPath(Long id) {
        return learningPathPlanService.getPlan(id);
    }

    public LearningPathPlanVO getPathByMatch(Long matchingRecordId) {
        return learningPathPlanService.getByMatchingRecord(matchingRecordId);
    }

    public PageResponse<LearningPathPlanVO> pagePaths(long current, long size, Long empId, Long postId, String status) {
        IPage<LearningPathPlanVO> page = learningPathPlanService.pagePlans(new Page<>(current, size), empId, postId, status);
        return PageResponse.from(page, v -> v);
    }

    public void updateStepStatus(Long stepId, String status) {
        learningPathPlanService.updateStepStatus(stepId, status);
    }

    public int refreshResourceBindings(Long planId) {
        return learningPathPlanService.refreshResourceBindings(planId);
    }

    public int refreshAllResourceBindings() {
        return learningPathPlanService.refreshAllResourceBindings();
    }

    public PageResponse<LearningProjectTaskVO> pageProjectTasks(long current, long size, Long planId, Long empId, String status) {
        IPage<LearningProjectTaskVO> page = learningProjectTaskService.pageTasks(new Page<>(current, size), planId, empId, status);
        return PageResponse.from(page, v -> v);
    }

    public LearningProjectTaskVO getProjectTask(Long id) {
        return learningProjectTaskService.getTask(id);
    }

    public LearningProjectSubmissionResponse submitProjectTask(Long id, LearningProjectSubmitDTO dto) {
        LearningProjectSubmission entity = learningProjectTaskService.submit(id, dto, null);
        return toSubmissionResponse(entity);
    }

    public LearningProjectSubmissionResponse reviewProjectSubmission(Long id, LearningProjectReviewDTO dto) {
        LearningProjectSubmission entity = learningProjectTaskService.review(id, dto, null);
        return toSubmissionResponse(entity);
    }

    public List<LearningAssessmentItemResponse> generateAssessments(LearningAssessmentGenerateRequest request) {
        List<LearningAssessmentItem> entities = learningAssessmentService.generateAssessments(request);
        return entities.stream().map(this::toResponse).toList();
    }

    public List<LearningAssessmentItemResponse> getAssessmentsByPlan(Long planId) {
        List<LearningAssessmentItem> entities = learningAssessmentService.getAssessmentsByPlan(planId);
        return entities.stream().map(this::toResponse).toList();
    }

    public LearningAssessmentItemResponse answerAssessment(Long assessmentId, String answerText) {
        return toResponse(learningAssessmentService.answer(assessmentId, answerText));
    }

    public CapabilityClosureResult confirmAbilityImprovement(Long planId, Long stepId) {
        return learningAssessmentService.confirmAbilityImprovement(planId, stepId);
    }

    private LearningResourceResponse toResponse(LearningResource e) {
        if (e == null) return null;
        return new LearningResourceResponse(
                e.getId(), e.getResourceCode(), e.getAbilityName(), e.getTagId(),
                e.getTitle(), e.getResourceType(), e.getDifficultyLevel(), e.getUrl(),
                e.getDescription(), e.getPlatform(), e.getPlatformIcon(), e.getCoverImageUrl(),
                e.getDuration(), e.getSortOrder(), e.getStatus(),
                e.getCreatedTime(), e.getUpdatedTime()
        );
    }

    private LearningProjectSubmissionResponse toSubmissionResponse(LearningProjectSubmission e) {
        if (e == null) return null;
        return new LearningProjectSubmissionResponse(
                e.getId(), e.getTaskId(), e.getPlanId(), e.getStepId(), e.getEmpId(),
                e.getRepoUrl(), e.getDemoUrl(), e.getReportUrl(), e.getSubmissionText(),
                e.getAiReviewResult(), e.getReviewStatus(), e.getReviewComment(), e.getEvidenceId(),
                e.getReviewedBy(), e.getReviewedTime(), e.getCreatedTime(), e.getUpdatedTime()
        );
    }

    private LearningAssessmentItemResponse toResponse(LearningAssessmentItem e) {
        if (e == null) return null;
        return new LearningAssessmentItemResponse(
                e.getId(), e.getPlanId(), e.getStepId(), e.getAbilityTagId(),
                e.getQuestionType(), e.getQuestionText(), e.getReferenceAnswer(),
                e.getDifficultyLevel(), e.getSource(), e.getAnswerText(), e.getScore(),
                e.getAssessmentStatus(), e.getScoringFeedback(), e.getAnsweredTime(), e.getScoredTime(),
                e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
