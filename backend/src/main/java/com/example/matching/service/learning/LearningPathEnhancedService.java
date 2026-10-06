package com.example.matching.service.learning;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;

import java.util.List;
import java.util.Map;

/**
 * 增强版学习路径服务接口
 * <p>
 * 基于知识图谱和掌握度的学习路径推荐
 *
 * @author system
 */
public interface LearningPathEnhancedService {

    /**
     * 基于知识图谱生成学习路径
     *
     * @param request 请求DTO
     * @return 学习路径项列表，按推荐顺序排列
     */
    List<LearningPathItemDTO> generateLearningPathByKnowledgeGraph(LearningPathRequestDTO request);

    /**
     * 基于掌握度生成学习路径
     *
     * @param empId   员工ID
     * @param postId  岗位ID
     * @return 学习路径项列表，按推荐顺序排列
     */
    List<LearningPathItemDTO> generateLearningPathByMastery(Long empId, Long postId);

    /**
     * 获取员工的知识领域掌握度
     *
     * @param empId 员工ID
     * @return 知识领域掌握度映射（领域ID -> 掌握度评分）
     */
    Map<Long, Double> getDomainMasteryScores(Long empId);

    /**
     * 获取员工的知识点掌握度
     *
     * @param empId    员工ID
     * @param domainId 领域ID
     * @return 知识点掌握度映射（知识点ID -> 掌握度评分）
     */
    Map<Long, Double> getNodeMasteryScores(Long empId, Long domainId);

    /**
     * 获取员工的薄弱环节
     *
     * @param empId 员工ID
     * @param limit 限制数量
     * @return 薄弱知识点列表
     */
    List<Map<String, Object>> getWeakPoints(Long empId, int limit);

    /**
     * 获取学习路径推荐
     *
     * @param empId  员工ID
     * @param postId 岗位ID
     * @return 学习路径推荐列表
     */
    List<Map<String, Object>> getLearningPathRecommendations(Long empId, Long postId);

    /**
     * 获取知识领域学习顺序
     *
     * @param empId  员工ID
     * @param postId 岗位ID
     * @return 知识领域学习顺序列表
     */
    List<KnowledgeDomain> getDomainLearningOrder(Long empId, Long postId);

    /**
     * 获取知识点学习顺序
     *
     * @param empId    员工ID
     * @param domainId 领域ID
     * @return 知识点学习顺序列表
     */
    List<KnowledgeNode> getNodeLearningOrder(Long empId, Long domainId);

    /**
     * 更新学习进度
     *
     * @param empId  员工ID
     * @param nodeId 知识点ID
     * @param status 状态
     */
    void updateLearningProgress(Long empId, Long nodeId, String status);

    /**
     * 获取学习进度概览
     *
     * @param empId 员工ID
     * @return 学习进度概览
     */
    Map<String, Object> getLearningProgressOverview(Long empId);
}
