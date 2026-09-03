package com.example.matching.service.kg.impl;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.service.kg.PostGraphEvolutionService;
import com.example.matching.service.post.PostModelVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 岗位图谱演化服务实现
 * <p>
 * 在岗位模型版本发布后更新知识图谱。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostGraphEvolutionServiceImpl implements PostGraphEvolutionService {

    private final KnowledgeGraphQueryService knowledgeGraphQueryService;
    private final PostModelVersionService postModelVersionService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public GraphUpdateResult updateGraphAfterVersionPublish(Long postId, Long versionId) {
        log.info("更新岗位图谱: postId={}, versionId={}", postId, versionId);

        int nodesCreated = 0;
        int nodesUpdated = 0;
        int edgesCreated = 0;
        int edgesUpdated = 0;
        List<String> warnings = new ArrayList<>();

        try {
            // 1. 获取版本详情
            PostModelVersion version = postModelVersionService.getById(versionId);
            if (version == null) {
                warnings.add("版本不存在: " + versionId);
                return new GraphUpdateResult(0, 0, 0, 0, warnings);
            }

            // 2. 获取版本明细
            List<PostModelVersionItem> items = postModelVersionService.getVersionItems(versionId);

            // 3. 构建节点和边列表
            List<KgGraphNode> nodes = new ArrayList<>();
            List<KgGraphEdge> edges = new ArrayList<>();

            // 添加岗位节点
            KgGraphNode postNode = new KgGraphNode();
            postNode.setNodeKey("POST:" + postId);
            postNode.setNodeType("POST");
            postNode.setLabel("岗位-" + postId);
            postNode.setMetadataJson(toJson(Map.of("postId", postId, "versionId", versionId)));
            nodes.add(postNode);
            nodesCreated++;

            // 4. 处理每个能力项
            for (PostModelVersionItem item : items) {
                try {
                    // 添加能力节点
                    KgGraphNode abilityNode = new KgGraphNode();
                    abilityNode.setNodeKey("ABILITY:" + item.getTagId());
                    abilityNode.setNodeType("ABILITY");
                    abilityNode.setLabel("能力-" + item.getTagId());
                    abilityNode.setMetadataJson(toJson(Map.of(
                            "tagId", item.getTagId(),
                            "level", item.getMinRequiredLevel() != null ? item.getMinRequiredLevel() : 0,
                            "weight", item.getWeight() != null ? item.getWeight().doubleValue() : 0.0,
                            "isCore", item.getIsCore() != null ? item.getIsCore() : 0
                    )));
                    nodes.add(abilityNode);
                    nodesCreated++;

                    // 添加岗位-能力关系边
                    KgGraphEdge edge = new KgGraphEdge();
                    edge.setSourceNodeKey("POST:" + postId);
                    edge.setTargetNodeKey("ABILITY:" + item.getTagId());
                    edge.setEdgeType("REQUIRES");
                    edge.setMetadataJson(toJson(Map.of(
                            "supportScore", item.getWeight() != null ? item.getWeight().doubleValue() : 0.0,
                            "sourceRefs", SourceRefConstants.factRef(SourceRefConstants.ENTITY_POST_ABILITY_MODEL, item.getId()),
                            "versionId", versionId,
                            "firstSeenTime", System.currentTimeMillis(),
                            "lastConfirmedTime", System.currentTimeMillis()
                    )));
                    edges.add(edge);
                    edgesCreated++;

                } catch (Exception e) {
                    warnings.add("处理能力项失败: tagId=" + item.getTagId() + ", 错误: " + e.getMessage());
                    log.warn("处理能力项失败: tagId={}", item.getTagId(), e);
                }
            }

            // 5. 同步到图数据库
            if (!nodes.isEmpty() || !edges.isEmpty()) {
                requestGraphRebuild();
            }

            log.info("岗位图谱更新完成: nodesCreated={}, edgesCreated={}", nodesCreated, edgesCreated);

        } catch (Exception e) {
            log.error("更新岗位图谱失败: {}", e.getMessage(), e);
            warnings.add("更新失败: " + e.getMessage());
        }

        return new GraphUpdateResult(nodesCreated, nodesUpdated, edgesCreated, edgesUpdated, warnings);
    }

    @Override
    public boolean updatePostAbilityRelation(Long postId, Long abilityTagId, double supportScore,
                                              List<String> sourceRefs, Long versionId) {
        log.debug("更新岗位-能力关系: postId={}, abilityTagId={}, supportScore={}",
                postId, abilityTagId, supportScore);

        try {
            List<KgGraphNode> nodes = new ArrayList<>();
            List<KgGraphEdge> edges = new ArrayList<>();

            // 添加岗位节点
            KgGraphNode postNode = new KgGraphNode();
            postNode.setNodeKey("POST:" + postId);
            postNode.setNodeType("POST");
            postNode.setLabel("岗位-" + postId);
            nodes.add(postNode);

            // 添加能力节点
            KgGraphNode abilityNode = new KgGraphNode();
            abilityNode.setNodeKey("ABILITY:" + abilityTagId);
            abilityNode.setNodeType("ABILITY");
            abilityNode.setLabel("能力-" + abilityTagId);
            nodes.add(abilityNode);

            // 添加关系边
            KgGraphEdge edge = new KgGraphEdge();
            edge.setSourceNodeKey("POST:" + postId);
            edge.setTargetNodeKey("ABILITY:" + abilityTagId);
            edge.setEdgeType("REQUIRES");
            edge.setMetadataJson(toJson(Map.of(
                    "supportScore", supportScore,
                    "sourceRefs", sourceRefs != null ? String.join(",", sourceRefs) : "",
                    "versionId", versionId != null ? versionId : 0L,
                    "firstSeenTime", System.currentTimeMillis(),
                    "lastConfirmedTime", System.currentTimeMillis()
            )));
            edges.add(edge);

            // 同步到图数据库
            requestGraphRebuild();

            return true;
        } catch (Exception e) {
            log.warn("更新岗位-能力关系失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean addEvidenceSupportRelation(Long evidenceId, Long abilityTagId, double supportScore, String sourceRef) {
        log.debug("添加证据-能力支持关系: evidenceId={}, abilityTagId={}", evidenceId, abilityTagId);

        try {
            List<KgGraphNode> nodes = new ArrayList<>();
            List<KgGraphEdge> edges = new ArrayList<>();

            // 添加证据节点
            KgGraphNode evidenceNode = new KgGraphNode();
            evidenceNode.setNodeKey("EVIDENCE:" + evidenceId);
            evidenceNode.setNodeType("EVIDENCE");
            evidenceNode.setLabel("证据-" + evidenceId);
            nodes.add(evidenceNode);

            // 添加能力节点
            KgGraphNode abilityNode = new KgGraphNode();
            abilityNode.setNodeKey("ABILITY:" + abilityTagId);
            abilityNode.setNodeType("ABILITY");
            abilityNode.setLabel("能力-" + abilityTagId);
            nodes.add(abilityNode);

            // 添加关系边
            KgGraphEdge edge = new KgGraphEdge();
            edge.setSourceNodeKey("EVIDENCE:" + evidenceId);
            edge.setTargetNodeKey("ABILITY:" + abilityTagId);
            edge.setEdgeType("SUPPORTS");
            edge.setMetadataJson(toJson(Map.of(
                    "supportScore", supportScore,
                    "sourceRef", sourceRef != null ? sourceRef : "",
                    "firstSeenTime", System.currentTimeMillis()
            )));
            edges.add(edge);

            // 同步到图数据库
            requestGraphRebuild();

            return true;
        } catch (Exception e) {
            log.warn("添加证据-能力支持关系失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getPostAbilityGraph(Long postId) {
        log.debug("获取岗位能力图谱: postId={}", postId);

        try {
            // 使用 KnowledgeGraphQueryService 查询
            Map<String, Object> result = knowledgeGraphQueryService.getPostCenteredGraph(postId);
            return toJson(result);
        } catch (Exception e) {
            log.warn("获取岗位能力图谱失败: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public String getAbilityEvidenceChain(Long abilityTagId) {
        log.debug("获取能力证据链: abilityTagId={}", abilityTagId);

        try {
            // 使用全景图谱查询能力相关节点
            Map<String, Object> result = knowledgeGraphQueryService.getPanorama(
                    List.of("ABILITY", "EVIDENCE"),
                    String.valueOf(abilityTagId),
                    null,
                    100
            );
            return toJson(result);
        } catch (Exception e) {
            log.warn("获取能力证据链失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 序列化为JSON
     */
    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void requestGraphRebuild() {
        eventPublisher.publishEvent(new KnowledgeGraphRebuildRequestedEvent());
    }
}
