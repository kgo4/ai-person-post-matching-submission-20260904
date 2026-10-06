package com.example.matching.service.learning;

import com.example.matching.dto.learning.LearningAssessmentGenerateRequest;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.entity.learning.LearningAssessmentItem;

import java.util.List;

/**
 * 学习评估服务接口
 *
 * @author system
 */
public interface LearningAssessmentService {

    /**
     * 生成评估题目
     *
     * @param request 生成请求
     * @return 生成的题目列表
     */
    List<LearningAssessmentItem> generateAssessments(LearningAssessmentGenerateRequest request);

    /**
     * 获取计划下的所有评估题目
     *
     * @param planId 计划ID
     * @return 题目列表
     */
    List<LearningAssessmentItem> getAssessmentsByPlan(Long planId);

    LearningAssessmentItem answer(Long assessmentId, String answerText);

    CapabilityClosureResult confirmAbilityImprovement(Long planId, Long stepId);
}
