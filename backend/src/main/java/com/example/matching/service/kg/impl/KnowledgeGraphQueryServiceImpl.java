package com.example.matching.service.kg.impl;

import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
/**
 * 知识图谱查询服务：委托视图查询与 Agent 上下文查询两个组件。
 * <p>
 * 从 860+ 行精简为聚合入口，查询逻辑已拆分为
 * {@link KnowledgeGraphViewQueryService} 与 {@link KnowledgeGraphContextQueryService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphQueryServiceImpl implements KnowledgeGraphQueryService {

    private final KnowledgeGraphViewQueryService viewQueryService;
    private final KnowledgeGraphContextQueryService contextQueryService;

    @Override
    public Map<String, Object> getPanorama(List<String> nodeTypes, String keyword, String category, Integer limit) {
        return viewQueryService.getPanorama(nodeTypes, keyword, category, limit);
    }

    @Override
    public Map<String, Object> getPostCenteredGraph(Long postId) {
        return viewQueryService.getPostCenteredGraph(postId);
    }

    @Override
    public Map<String, Object> getEmployeeCenteredGraph(Long empId) {
        return viewQueryService.getEmployeeCenteredGraph(empId);
    }

    @Override
    public Map<String, Object> getAbilityGapPath(Long empId, Long postId) {
        return viewQueryService.getAbilityGapPath(empId, postId);
    }

    @Override
    public Map<String, Object> getMemoryGraph(Integer limit) {
        return viewQueryService.getMemoryGraph(limit);
    }

    @Override
    public Map<String, Object> getTimeline(Integer limit) {
        return viewQueryService.getTimeline(limit);
    }

    @Override
    public GraphMatchContext getMatchContext(Long employeeId, Long postId) {
        return contextQueryService.getMatchContext(employeeId, postId);
    }

    @Override
    public GraphAbilityEvidenceContext getAbilityEvidenceContext(Long abilityId, Long employeeId) {
        return contextQueryService.getAbilityEvidenceContext(abilityId, employeeId);
    }

    @Override
    public GraphLearningPrerequisiteContext getLearningPrerequisiteContext(List<Long> abilityIds) {
        return contextQueryService.getLearningPrerequisiteContext(abilityIds);
    }
}
