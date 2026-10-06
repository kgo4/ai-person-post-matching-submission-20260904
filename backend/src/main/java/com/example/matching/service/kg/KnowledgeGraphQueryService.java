package com.example.matching.service.kg;

import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱查询服务接口
 *
 * @author system
 */
public interface KnowledgeGraphQueryService {

    /**
     * 获取全景图谱
     *
     * @param nodeTypes 节点类型过滤
     * @param keyword   关键词过滤
     * @param category  分类过滤
     * @param limit     限制数量
     * @return 图谱JSON
     */
    Map<String, Object> getPanorama(List<String> nodeTypes, String keyword, String category, Integer limit);

    /**
     * 获取岗位中心图谱
     *
     * @param postId 岗位ID
     * @return 图谱JSON
     */
    Map<String, Object> getPostCenteredGraph(Long postId);

    /**
     * 获取员工中心图谱
     *
     * @param empId 员工ID
     * @return 图谱JSON
     */
    Map<String, Object> getEmployeeCenteredGraph(Long empId);

    /**
     * 获取能力差距路径
     *
     * @param empId  员工ID
     * @param postId 岗位ID
     * @return 图谱JSON
     */
    Map<String, Object> getAbilityGapPath(Long empId, Long postId);

    /**
     * 获取治理记忆图谱
     * 展示 Agent 记忆节点、关联的治理事件、影响的标签、命中的来源
     *
     * @param limit 限制数量
     * @return 图谱JSON
     */
    Map<String, Object> getMemoryGraph(Integer limit);

    /**
     * 获取图谱变化时间线
     * 返回最近添加/修改的节点和边
     *
     * @param limit 限制数量
     * @return 时间线数据
     */
    Map<String, Object> getTimeline(Integer limit);

    /**
     * 获取人岗匹配图谱上下文（受限、类型化）
     */
    GraphMatchContext getMatchContext(Long employeeId, Long postId);

    /**
     * 获取能力的已审核证据上下文（受限、类型化）
     */
    GraphAbilityEvidenceContext getAbilityEvidenceContext(Long abilityId, Long employeeId);

    /**
     * 获取学习路径前置条件上下文（受限、类型化）
     */
    GraphLearningPrerequisiteContext getLearningPrerequisiteContext(List<Long> abilityIds);
}
