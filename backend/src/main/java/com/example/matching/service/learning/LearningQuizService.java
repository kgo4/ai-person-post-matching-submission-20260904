package com.example.matching.service.learning;

import com.example.matching.entity.learning.LearningQuiz;
import com.example.matching.entity.learning.LearningQuizRecord;

import java.util.List;
import java.util.Map;

/**
 * 测验题目服务接口
 *
 * @author system
 */
public interface LearningQuizService {

    /**
     * 获取所有测验题目
     *
     * @return 测验题目列表
     */
    List<LearningQuiz> getAllQuizzes();

    /**
     * 根据ID获取测验题目
     *
     * @param quizId 题目ID
     * @return 测验题目
     */
    LearningQuiz getQuizById(Long quizId);

    /**
     * 根据编码获取测验题目
     *
     * @param quizCode 题目编码
     * @return 测验题目
     */
    LearningQuiz getQuizByCode(String quizCode);

    /**
     * 根据领域ID获取测验题目
     *
     * @param domainId 领域ID
     * @return 测验题目列表
     */
    List<LearningQuiz> getQuizzesByDomainId(Long domainId);

    /**
     * 根据知识点ID获取测验题目
     *
     * @param nodeId 知识点ID
     * @return 测验题目列表
     */
    List<LearningQuiz> getQuizzesByNodeId(Long nodeId);

    /**
     * 根据能力标签ID获取测验题目
     *
     * @param tagId 能力标签ID
     * @return 测验题目列表
     */
    List<LearningQuiz> getQuizzesByTagId(Long tagId);

    /**
     * 根据难度级别获取测验题目
     *
     * @param difficultyLevel 难度级别
     * @return 测验题目列表
     */
    List<LearningQuiz> getQuizzesByDifficultyLevel(String difficultyLevel);

    /**
     * 创建测验题目
     *
     * @param quiz 测验题目
     * @return 创建后的测验题目
     */
    LearningQuiz createQuiz(LearningQuiz quiz);

    /**
     * 更新测验题目
     *
     * @param quiz 测验题目
     * @return 更新后的测验题目
     */
    LearningQuiz updateQuiz(LearningQuiz quiz);

    /**
     * 删除测验题目
     *
     * @param quizId 题目ID
     */
    void deleteQuiz(Long quizId);

    /**
     * 提交答题记录
     *
     * @param record 答题记录
     * @return 创建后的答题记录
     */
    LearningQuizRecord submitQuizRecord(LearningQuizRecord record);

    /**
     * 获取员工的答题记录
     *
     * @param empId 员工ID
     * @return 答题记录列表
     */
    List<LearningQuizRecord> getQuizRecordsByEmpId(Long empId);

    /**
     * 获取员工在特定题目的答题记录
     *
     * @param empId  员工ID
     * @param quizId 题目ID
     * @return 答题记录
     */
    LearningQuizRecord getQuizRecordByEmpIdAndQuizId(Long empId, Long quizId);

    /**
     * 获取员工在特定计划的答题记录
     *
     * @param empId  员工ID
     * @param planId 计划ID
     * @return 答题记录列表
     */
    List<LearningQuizRecord> getQuizRecordsByEmpIdAndPlanId(Long empId, Long planId);

    /**
     * 计算员工在特定领域的掌握度
     *
     * @param empId    员工ID
     * @param domainId 领域ID
     * @return 掌握度评分（0-100）
     */
    double calculateMasteryScore(Long empId, Long domainId);

    /**
     * 计算员工在特定知识点的掌握度
     *
     * @param empId  员工ID
     * @param nodeId 知识点ID
     * @return 掌握度评分（0-100）
     */
    double calculateMasteryScoreByNodeId(Long empId, Long nodeId);

    /**
     * 计算员工在特定能力标签的掌握度
     *
     * @param empId 员工ID
     * @param tagId 能力标签ID
     * @return 掌握度评分（0-100）
     */
    double calculateMasteryScoreByTagId(Long empId, Long tagId);

    /**
     * 获取员工的掌握度概览
     *
     * @param empId 员工ID
     * @return 掌握度概览（领域ID -> 掌握度评分）
     */
    Map<Long, Double> getMasteryOverview(Long empId);

    /**
     * 获取员工的薄弱环节
     *
     * @param empId 员工ID
     * @param limit 限制数量
     * @return 薄弱知识点列表
     */
    List<Map<String, Object>> getWeakPoints(Long empId, int limit);

    /**
     * 初始化默认测验题目
     */
    void initDefaultQuizzes();
}
