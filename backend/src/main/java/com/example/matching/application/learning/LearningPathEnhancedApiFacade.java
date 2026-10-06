package com.example.matching.application.learning;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.dto.learning.api.*;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.learning.LearningQuiz;
import com.example.matching.entity.learning.LearningQuizRecord;
import com.example.matching.service.kg.KnowledgeDomainService;
import com.example.matching.service.learning.LearningPathEnhancedService;
import com.example.matching.service.learning.LearningQuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LearningPathEnhancedApiFacade {

    private final LearningPathEnhancedService learningPathEnhancedService;
    private final KnowledgeDomainService domainService;
    private final LearningQuizService quizService;

    public List<LearningPathItemDTO> generateLearningPathByKnowledgeGraph(LearningPathRequestDTO request) {
        return learningPathEnhancedService.generateLearningPathByKnowledgeGraph(request);
    }

    public List<LearningPathItemDTO> generateLearningPathByMastery(Long empId, Long postId) {
        return learningPathEnhancedService.generateLearningPathByMastery(empId, postId);
    }

    public List<Map<String, Object>> getLearningPathRecommendations(Long empId, Long postId) {
        return learningPathEnhancedService.getLearningPathRecommendations(empId, postId);
    }

    public Map<Long, Double> getDomainMasteryScores(Long empId) {
        return learningPathEnhancedService.getDomainMasteryScores(empId);
    }

    public Map<Long, Double> getNodeMasteryScores(Long empId, Long domainId) {
        return learningPathEnhancedService.getNodeMasteryScores(empId, domainId);
    }

    public List<Map<String, Object>> getWeakPoints(Long empId, int limit) {
        return learningPathEnhancedService.getWeakPoints(empId, limit);
    }

    public List<KnowledgeDomainResponse> getDomainLearningOrder(Long empId, Long postId) {
        List<KnowledgeDomain> entities = learningPathEnhancedService.getDomainLearningOrder(empId, postId);
        return entities.stream().map(this::toResponse).toList();
    }

    public List<KnowledgeNodeResponse> getNodeLearningOrder(Long empId, Long domainId) {
        List<KnowledgeNode> entities = learningPathEnhancedService.getNodeLearningOrder(empId, domainId);
        return entities.stream().map(this::toResponse).toList();
    }

    public void updateLearningProgress(Long empId, Long nodeId, String status) {
        learningPathEnhancedService.updateLearningProgress(empId, nodeId, status);
    }

    public Map<String, Object> getLearningProgressOverview(Long empId) {
        return learningPathEnhancedService.getLearningProgressOverview(empId);
    }

    public List<KnowledgeDomainResponse> getAllDomains() {
        List<KnowledgeDomain> entities = domainService.getAllDomains();
        return entities.stream().map(this::toResponse).toList();
    }

    public KnowledgeDomainResponse getDomainById(Long id) {
        KnowledgeDomain entity = domainService.getDomainById(id);
        return toResponse(entity);
    }

    public List<KnowledgeNodeResponse> getNodesByDomainId(Long domainId) {
        List<KnowledgeNode> entities = domainService.getNodesByDomainId(domainId);
        return entities.stream().map(this::toResponse).toList();
    }

    public List<LearningQuizResponse> getAllQuizzes() {
        List<LearningQuiz> entities = quizService.getAllQuizzes();
        return entities.stream().map(this::toResponse).toList();
    }

    public List<LearningQuizResponse> getQuizzesByDomainId(Long domainId) {
        List<LearningQuiz> entities = quizService.getQuizzesByDomainId(domainId);
        return entities.stream().map(this::toResponse).toList();
    }

    public List<LearningQuizResponse> getQuizzesByNodeId(Long nodeId) {
        List<LearningQuiz> entities = quizService.getQuizzesByNodeId(nodeId);
        return entities.stream().map(this::toResponse).toList();
    }

    public LearningQuizRecordResponse submitQuizRecord(LearningQuizRecord record) {
        LearningQuizRecord entity = quizService.submitQuizRecord(record);
        return toResponse(entity);
    }

    public LearningQuizRecordResponse submitQuizRecord(LearningQuizRecordRequest request) {
        LearningQuizRecord record = new LearningQuizRecord();
        record.setEmpId(request.empId());
        record.setQuizId(request.quizId());
        record.setPlanId(request.planId());
        record.setStepId(request.stepId());
        record.setUserAnswer(request.userAnswer());
        record.setIsCorrect(request.isCorrect());
        record.setAnswerTime(request.answerTime());
        record.setAnswerScore(request.answerScore());
        return submitQuizRecord(record);
    }

    public List<LearningQuizRecordResponse> getQuizRecordsByEmpId(Long empId) {
        List<LearningQuizRecord> entities = quizService.getQuizRecordsByEmpId(empId);
        return entities.stream().map(this::toResponse).toList();
    }

    private KnowledgeDomainResponse toResponse(KnowledgeDomain e) {
        if (e == null) return null;
        return new KnowledgeDomainResponse(
                e.getId(), e.getDomainCode(), e.getDomainName(), e.getDomainIcon(),
                e.getDomainColor(), e.getDomainWeight(), e.getDomainDescription(),
                e.getParentId(), e.getSortOrder(), e.getStatus(),
                e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime(),
                e.getVersion()
        );
    }

    private KnowledgeNodeResponse toResponse(KnowledgeNode e) {
        if (e == null) return null;
        return new KnowledgeNodeResponse(
                e.getId(), e.getNodeCode(), e.getNodeName(), e.getDomainId(),
                e.getParentId(), e.getNodeLevel(), e.getNodeDescription(),
                e.getLearningObjectives(), e.getPrerequisitesJson(), e.getSortOrder(),
                e.getStatus(), e.getCreatedBy(), e.getCreatedTime(),
                e.getUpdatedBy(), e.getUpdatedTime(), e.getVersion()
        );
    }

    private LearningQuizResponse toResponse(LearningQuiz e) {
        if (e == null) return null;
        return new LearningQuizResponse(
                e.getId(), e.getQuizCode(), e.getQuestionText(), e.getQuestionType(),
                e.getOptionsJson(), e.getReferenceAnswer(), e.getAnswerExplanation(),
                e.getDifficultyLevel(), e.getDomainId(), e.getNodeId(), e.getTagId(),
                e.getEstimatedTime(), e.getScore(), e.getUsageCount(), e.getCorrectRate(),
                e.getStatus(), e.getCreatedBy(), e.getCreatedTime(),
                e.getUpdatedBy(), e.getUpdatedTime(), e.getVersion()
        );
    }

    private LearningQuizRecordResponse toResponse(LearningQuizRecord e) {
        if (e == null) return null;
        return new LearningQuizRecordResponse(
                e.getId(), e.getEmpId(), e.getQuizId(), e.getPlanId(), e.getStepId(),
                e.getUserAnswer(), e.getIsCorrect(), e.getAnswerTime(), e.getAnswerScore(),
                e.getAttemptCount(), e.getFirstAttemptTime(), e.getLastAttemptTime(),
                e.getCorrectCount(), e.getIsMastered(), e.getMasteredTime(),
                e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime(),
                e.getVersion()
        );
    }
}
