package com.example.matching.service.capability.impl;

import com.example.matching.dto.capability.CapabilityBrainSummaryDTO;
import com.example.matching.port.system.SystemDataStatsPort;
import com.example.matching.port.system.SystemDataStatsPort.DataStatsSnapshot;
import com.example.matching.service.capability.CapabilityBrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapabilityBrainServiceImpl implements CapabilityBrainService {

    private final SystemDataStatsPort systemDataStatsPort;

    @Override
    public CapabilityBrainSummaryDTO getSummary() {
        CapabilityBrainSummaryDTO summary = new CapabilityBrainSummaryDTO();
        summary.setTitle("岗位能力大脑");
        summary.setMission("多源数据采集、RAG证据检索、岗位能力图谱、人岗差距诊断和学习路径规划的统一闭环。");

        List<String> warnings = new ArrayList<>();
        CapabilityBrainSummaryDTO.Health health = buildHealth(warnings);
        summary.setHealth(health);

        List<CapabilityBrainSummaryDTO.Stage> stages = buildStages(health);
        summary.setStages(stages);
        summary.setLoopScore(calculateScore(stages));
        summary.setModuleLinks(buildModuleLinks());

        addBusinessWarnings(health, warnings);
        summary.setWarnings(warnings);
        return summary;
    }

    private CapabilityBrainSummaryDTO.Health buildHealth(List<String> warnings) {
        DataStatsSnapshot snapshot = systemDataStatsPort.countSnapshot();
        CapabilityBrainSummaryDTO.Health health = new CapabilityBrainSummaryDTO.Health();
        health.setPostCount(trim(snapshot.postCount(), "岗位数据查询失败", warnings));
        health.setEmployeeCount(trim(snapshot.employeeCount(), "人员数据查询失败", warnings));
        health.setAbilityTagCount(trim(snapshot.abilityTagCount(), "能力标签查询失败", warnings));
        health.setMatchingRecordCount(trim(snapshot.matchingRecordCount(), "匹配记录查询失败", warnings));
        health.setEvidenceCount(trim(snapshot.evidenceCount(), "来源证据查询失败", warnings));
        health.setRagDocumentCount(trim(snapshot.ragDocumentCount(), "RAG知识文档查询失败", warnings));
        health.setGraphNodeCount(trim(snapshot.graphNodeCount(), "图谱节点查询失败", warnings));
        health.setGraphEdgeCount(trim(snapshot.graphEdgeCount(), "图谱关系查询失败", warnings));
        health.setEvolutionTaskCount(trim(snapshot.evolutionTaskCount(), "岗位演化任务查询失败", warnings));
        health.setLearningResourceCount(trim(snapshot.learningResourceCount(), "学习资源查询失败", warnings));
        return health;
    }

    private long trim(Long value, String warning, List<String> warnings) {
        if (value == null) {
            warnings.add(warning);
            return 0L;
        }
        return value;
    }

    private List<CapabilityBrainSummaryDTO.Stage> buildStages(CapabilityBrainSummaryDTO.Health health) {
        return List.of(
                stage("market-sensing", "多源采集与新岗位发现",
                        "汇聚JD、岗位原型、员工能力和人工证据，支撑新兴岗位识别与岗位定义。",
                        health.getEvidenceCount() > 0 && health.getPostCount() > 0,
                        "/capability-brain/evidence", "可追溯来源证据"),
                stage("position-evolution", "既有岗位能力动态更新",
                        "对比新旧JD和岗位能力模型，生成新增、删除、等级与权重变化，人工确认后入库。",
                        health.getEvolutionTaskCount() > 0 && health.getAbilityTagCount() > 0,
                        "/capability-brain/evolution", "岗位能力变更项"),
                stage("knowledge-guard", "RAG与幻觉防控",
                        "把岗位、能力、证据和学习资源沉淀为知识文档，作为AI抽取和变更建议的证据约束。",
                        health.getRagDocumentCount() > 0 && health.getEvidenceCount() > 0,
                        "/capability-brain/rag/knowledge", "带来源的检索上下文"),
                stage("panorama-graph", "新一代信息技术岗位全景图谱",
                        "把岗位、能力、人员、证据、RAG文档、演化事件和学习资源连接成技能级图谱。",
                        health.getGraphNodeCount() > 0 && health.getGraphEdgeCount() > 0,
                        "/post/panorama", "技能级岗位能力关系"),
                stage("gap-learning", "匹配诊断与学习路径",
                        "基于简历解析、能力画像和目标岗位图谱输出差距分析、改进建议和学习路径。",
                        health.getMatchingRecordCount() > 0 && health.getLearningResourceCount() > 0,
                        "/matching/result", "差距诊断与成长建议")
        );
    }

    private CapabilityBrainSummaryDTO.Stage stage(String key, String title, String description,
                                                   boolean ready, String route, String output) {
        CapabilityBrainSummaryDTO.Stage stage = new CapabilityBrainSummaryDTO.Stage();
        stage.setKey(key);
        stage.setTitle(title);
        stage.setDescription(description);
        stage.setStatus(ready ? "ready" : "warning");
        stage.setRoute(route);
        stage.setOutput(output);
        return stage;
    }

    private int calculateScore(List<CapabilityBrainSummaryDTO.Stage> stages) {
        long readyCount = stages.stream().filter(stage -> "ready".equals(stage.getStatus())).count();
        return (int) (readyCount * 100 / stages.size());
    }

    private List<CapabilityBrainSummaryDTO.ModuleLink> buildModuleLinks() {
        return List.of(
                module("evidence", "来源证据中心", "多源异构数据清洗、可信度和人工复核入口。", "/capability-brain/evidence", "source"),
                module("rag", "知识RAG", "为AI抽取、岗位演化和幻觉防控提供可引用上下文。", "/rag/knowledge", "guard"),
                module("evolution", "岗位能力演化", "识别既有岗位新增、删除、修改的能力项。", "/post/evolution", "evolution"),
                module("graph", "岗位全景图谱", "技能级展示岗位、人员、能力、证据和学习资源关系。", "/post/panorama", "graph"),
                module("learning", "差距学习路径", "把匹配差距转化为学习资源和成长建议。", "/learning/path", "growth")
        );
    }

    private CapabilityBrainSummaryDTO.ModuleLink module(String key, String title, String description,
                                                        String route, String role) {
        CapabilityBrainSummaryDTO.ModuleLink link = new CapabilityBrainSummaryDTO.ModuleLink();
        link.setKey(key);
        link.setTitle(title);
        link.setDescription(description);
        link.setRoute(route);
        link.setRole(role);
        return link;
    }

    private void addBusinessWarnings(CapabilityBrainSummaryDTO.Health health, List<String> warnings) {
        if (health.getEvidenceCount() == 0) {
            warnings.add("缺少来源证据，无法证明多源异构数据清洗与交叉验证");
        }
        if (health.getRagDocumentCount() == 0) {
            warnings.add("缺少RAG知识文档，幻觉防控和来源追溯不可用");
        }
        if (health.getGraphNodeCount() == 0 || health.getGraphEdgeCount() == 0) {
            warnings.add("图谱尚未重建，岗位、能力、证据之间没有形成可视化关系");
        }
        if (health.getEvolutionTaskCount() == 0) {
            warnings.add("缺少岗位演化任务，无法体现既有岗位能力动态更新");
        }
        if (health.getLearningResourceCount() == 0) {
            warnings.add("缺少学习资源，匹配差距难以转化为成长路径");
        }
    }
}
