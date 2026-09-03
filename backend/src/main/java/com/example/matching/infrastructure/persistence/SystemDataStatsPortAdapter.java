package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.system.SystemDataStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDataStatsPortAdapter implements SystemDataStatsPort {

    private final PostPostMapper postPostMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final ContestEvidenceItemMapper contestEvidenceItemMapper;
    private final RagKnowledgeDocumentMapper ragKnowledgeDocumentMapper;
    private final KgGraphNodeMapper kgGraphNodeMapper;
    private final KgGraphEdgeMapper kgGraphEdgeMapper;
    private final PostEvolutionTaskMapper postEvolutionTaskMapper;
    private final LearningResourceMapper learningResourceMapper;

    @Override
    public DataStatsSnapshot countSnapshot() {
        return new DataStatsSnapshot(
                count("岗位", () -> postPostMapper.selectCount(Wrappers.<PostPost>lambdaQuery())),
                count("人员", () -> empEmployeeMapper.selectCount(Wrappers.<EmpEmployee>lambdaQuery())),
                count("能力标签", () -> abilityTagMapper.selectCount(Wrappers.<AbilityTag>lambdaQuery())),
                count("匹配记录", () -> matchingRecordMapper.selectCount(Wrappers.<MatchingRecord>lambdaQuery())),
                count("来源证据", () -> contestEvidenceItemMapper.selectCount(Wrappers.<ContestEvidenceItem>lambdaQuery())),
                count("RAG知识文档", () -> ragKnowledgeDocumentMapper.selectCount(Wrappers.<RagKnowledgeDocument>lambdaQuery())),
                count("图谱节点", () -> kgGraphNodeMapper.selectCount(Wrappers.<KgGraphNode>lambdaQuery())),
                count("图谱关系", () -> kgGraphEdgeMapper.selectCount(Wrappers.<KgGraphEdge>lambdaQuery())),
                count("岗位演化任务", () -> postEvolutionTaskMapper.selectCount(Wrappers.<PostEvolutionTask>lambdaQuery())),
                count("学习资源", () -> learningResourceMapper.selectCount(Wrappers.<LearningResource>lambdaQuery()))
        );
    }

    private Long count(String domain, Supplier<Long> supplier) {
        try {
            Long value = supplier.get();
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("{}数据统计失败，按0处理", domain, e);
            return null;
        }
    }
}
