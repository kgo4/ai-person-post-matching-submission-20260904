package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.entity.learning.LearningProgressLog;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.learning.LearningProgressLogMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.service.kg.KnowledgeDomainService;
import com.example.matching.service.kg.support.KnowledgeNodeDependencyResolver;
import com.example.matching.service.learning.LearningPathEnhancedService;
import com.example.matching.service.learning.LearningQuizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强版学习路径服务实现
 * <p>
 * 基于知识图谱和掌握度的学习路径推荐
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathEnhancedServiceImpl implements LearningPathEnhancedService {

    private final KnowledgeDomainService domainService;
    private final LearningQuizService quizService;
    private final LearningResourceMapper resourceMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final LearningProgressLogMapper progressLogMapper;
    private final KnowledgeNodeDependencyResolver knowledgeNodeDependencyResolver;

    @Override
    public List<LearningPathItemDTO> generateLearningPathByKnowledgeGraph(LearningPathRequestDTO request) {
        List<LearningPathItemDTO> result = new ArrayList<>();

        if (request.getAbilityNames() == null || request.getAbilityNames().isEmpty()) {
            return result;
        }

        int currentLevel = request.getCurrentLevel() != null ? request.getCurrentLevel() : 1;
        int targetLevel = request.getTargetLevel() != null ? request.getTargetLevel() : 3;

        // 遍历每个能力名称
        for (String abilityName : request.getAbilityNames()) {
            String normalized = AbilityNameNormalizer.normalize(abilityName);

            // 查找对应的知识领域
            List<KnowledgeDomain> domains = domainService.getAllDomains();
            KnowledgeDomain targetDomain = null;

            // 简单匹配：根据能力名称查找相关领域
            for (KnowledgeDomain domain : domains) {
                if (domain.getDomainName().contains(abilityName) ||
                    abilityName.contains(domain.getDomainName())) {
                    targetDomain = domain;
                    break;
                }
            }

            if (targetDomain == null) {
                // 默认使用第一个领域
                targetDomain = domains.isEmpty() ? null : domains.get(0);
            }

            // 获取该领域的知识点
            if (targetDomain != null) {
                List<KnowledgeNode> nodes = domainService.getNodesByDomainId(targetDomain.getId());

                nodes = knowledgeNodeDependencyResolver.sortByPrerequisites(
                        nodes, KnowledgeNodeDependencyResolver.defaultOrder());

                // 为每个知识点生成学习路径项
                for (KnowledgeNode node : nodes) {
                    LearningPathItemDTO item = new LearningPathItemDTO();
                    item.setAbilityName(abilityName);
                    item.setTitle(node.getNodeName());
                    item.setDescription(node.getNodeDescription());
                    item.setResourceType("KNOWLEDGE_NODE");
                    item.setDifficultyLevel(node.getNodeLevel());

                    // 查找相关学习资源
                    List<LearningResource> resources = findRelatedResources(abilityName, node.getNodeName());
                    if (!resources.isEmpty()) {
                        LearningResource resource = resources.get(0);
                        item.setResourceId(resource.getId());
                        item.setResourceType(resource.getResourceType());
                        item.setUrl(resource.getUrl());
                        item.setDifficultyLevel(resource.getDifficultyLevel());
                    }

                    result.add(item);
                }
            }
        }

        return result;
    }

    @Override
    public List<LearningPathItemDTO> generateLearningPathByMastery(Long empId, Long postId) {
        List<LearningPathItemDTO> result = new ArrayList<>();

        // 获取岗位能力要求
        List<PostAbilityModel> postAbilities = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getIsDeleted, 0));

        if (postAbilities.isEmpty()) {
            return result;
        }

        // 获取员工能力
        List<EmpAbility> empAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getIsDeleted, 0));

        Map<Long, EmpAbility> empAbilityMap = empAbilities.stream()
                .filter(e -> e.getTagId() != null)
                .collect(Collectors.toMap(EmpAbility::getTagId, e -> e, (e1, e2) -> e1));
        Map<String, EmpAbility> empAbilityByName = empAbilities.stream()
                .filter(e -> e.getAbilityName() != null && !e.getAbilityName().isBlank())
                .collect(Collectors.toMap(e -> normalizeAbilityName(e.getAbilityName()), e -> e, (e1, e2) -> e1));

        // 计算能力差距并生成学习路径
        for (PostAbilityModel postAbility : postAbilities) {
            Long tagId = postAbility.getTagId();
            int requiredLevel = postAbility.getMinRequiredLevel() != null ? postAbility.getMinRequiredLevel() : 3;

            EmpAbility empAbility = tagId != null ? empAbilityMap.get(tagId) : null;
            if (empAbility == null && postAbility.getAbilityName() != null) {
                empAbility = empAbilityByName.get(normalizeAbilityName(postAbility.getAbilityName()));
            }
            int currentLevel = empAbility != null && empAbility.getAbilityLevel() != null ?
                    empAbility.getAbilityLevel() : 0;

            // 如果当前等级低于要求等级，需要学习
            if (currentLevel < requiredLevel) {
                // 岗位能力表名称是主数据；系统标签仅作为可选补充。
                String abilityName = resolveAbilityName(postAbility.getAbilityName(), tagId);

                // 计算掌握度
                double masteryScore = quizService.calculateMasteryScoreByTagId(empId, tagId);

                // 创建学习路径项
                LearningPathItemDTO item = new LearningPathItemDTO();
                item.setAbilityName(abilityName);
                item.setTitle("提升" + abilityName + "能力");
                item.setDescription("当前等级：" + currentLevel + "，目标等级：" + requiredLevel);
                item.setResourceType("ABILITY_GAP");
                item.setDifficultyLevel(requiredLevel - currentLevel);

                // 根据掌握度添加个性化建议
                if (masteryScore < 30) {
                    item.setDescription(item.getDescription() + " [基础薄弱，建议从基础开始]");
                } else if (masteryScore < 60) {
                    item.setDescription(item.getDescription() + " [有一定基础，建议加强练习]");
                } else {
                    item.setDescription(item.getDescription() + " [基础较好，建议深入学习]");
                }

                // 查找相关学习资源
                List<LearningResource> resources = findRelatedResources(abilityName, null);
                if (!resources.isEmpty()) {
                    LearningResource resource = resources.get(0);
                    item.setResourceId(resource.getId());
                    item.setResourceType(resource.getResourceType());
                    item.setUrl(resource.getUrl());
                }

                result.add(item);
            }
        }

        return result;
    }

    @Override
    public Map<Long, Double> getDomainMasteryScores(Long empId) {
        return quizService.getMasteryOverview(empId);
    }

    @Override
    public Map<Long, Double> getNodeMasteryScores(Long empId, Long domainId) {
        Map<Long, Double> scores = new HashMap<>();

        // 获取该领域的所有知识点
        List<KnowledgeNode> nodes = domainService.getNodesByDomainId(domainId);

        // 计算每个知识点的掌握度
        for (KnowledgeNode node : nodes) {
            double masteryScore = quizService.calculateMasteryScoreByNodeId(empId, node.getId());
            scores.put(node.getId(), masteryScore);
        }

        return scores;
    }

    @Override
    public List<Map<String, Object>> getWeakPoints(Long empId, int limit) {
        return quizService.getWeakPoints(empId, limit);
    }

    @Override
    public List<Map<String, Object>> getLearningPathRecommendations(Long empId, Long postId) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        // 获取岗位能力要求
        List<PostAbilityModel> postAbilities = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getIsDeleted, 0));

        if (postAbilities.isEmpty()) {
            return recommendations;
        }

        // 获取员工能力
        List<EmpAbility> empAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getIsDeleted, 0));

        Map<Long, EmpAbility> empAbilityMap = empAbilities.stream()
                .filter(e -> e.getTagId() != null)
                .collect(Collectors.toMap(EmpAbility::getTagId, e -> e, (e1, e2) -> e1));
        Map<String, EmpAbility> empAbilityByName = empAbilities.stream()
                .filter(e -> e.getAbilityName() != null && !e.getAbilityName().isBlank())
                .collect(Collectors.toMap(e -> normalizeAbilityName(e.getAbilityName()), e -> e, (e1, e2) -> e1));

        // 生成学习路径推荐
        for (PostAbilityModel postAbility : postAbilities) {
            Long tagId = postAbility.getTagId();
            int requiredLevel = postAbility.getMinRequiredLevel() != null ? postAbility.getMinRequiredLevel() : 3;

            EmpAbility empAbility = tagId != null ? empAbilityMap.get(tagId) : null;
            if (empAbility == null && postAbility.getAbilityName() != null) {
                empAbility = empAbilityByName.get(normalizeAbilityName(postAbility.getAbilityName()));
            }
            int currentLevel = empAbility != null && empAbility.getAbilityLevel() != null ?
                    empAbility.getAbilityLevel() : 0;

            // 如果当前等级低于要求等级，需要学习
            if (currentLevel < requiredLevel) {
                // 岗位能力表名称是主数据；系统标签仅作为可选补充。
                String abilityName = resolveAbilityName(postAbility.getAbilityName(), tagId);

                // 计算掌握度
                double masteryScore = quizService.calculateMasteryScoreByTagId(empId, tagId);

                // 创建推荐
                Map<String, Object> recommendation = new HashMap<>();
                recommendation.put("tagId", tagId);
                recommendation.put("abilityName", abilityName);
                recommendation.put("currentLevel", currentLevel);
                recommendation.put("requiredLevel", requiredLevel);
                recommendation.put("levelGap", requiredLevel - currentLevel);
                recommendation.put("masteryScore", masteryScore);
                recommendation.put("priority", calculatePriority(currentLevel, requiredLevel, masteryScore));

                // 查找相关学习资源
                List<LearningResource> resources = findRelatedResources(abilityName, null);
                if (!resources.isEmpty()) {
                    recommendation.put("resourceCount", resources.size());
                    recommendation.put("firstResourceId", resources.get(0).getId());
                    recommendation.put("firstResourceTitle", resources.get(0).getTitle());
                }

                recommendations.add(recommendation);
            }
        }

        // 按优先级排序（数值越大优先级越高，降序）
        recommendations.sort((a, b) -> {
            int priorityA = (int) a.get("priority");
            int priorityB = (int) b.get("priority");
            return Integer.compare(priorityB, priorityA);
        });

        return recommendations;
    }

    @Override
    public List<KnowledgeDomain> getDomainLearningOrder(Long empId, Long postId) {
        // 获取所有领域
        List<KnowledgeDomain> domains = domainService.getAllDomains();

        // 获取领域掌握度
        Map<Long, Double> masteryScores = getDomainMasteryScores(empId);

        // 按掌握度排序（薄弱环节优先）
        domains.sort((a, b) -> {
            double scoreA = masteryScores.getOrDefault(a.getId(), 0.0);
            double scoreB = masteryScores.getOrDefault(b.getId(), 0.0);
            return Double.compare(scoreA, scoreB);
        });

        return domains;
    }

    @Override
    public List<KnowledgeNode> getNodeLearningOrder(Long empId, Long domainId) {
        // 获取该领域的所有知识点
        List<KnowledgeNode> nodes = domainService.getNodesByDomainId(domainId);

        // 获取知识点掌握度
        Map<Long, Double> masteryScores = getNodeMasteryScores(empId, domainId);

        Comparator<KnowledgeNode> masteryOrder = Comparator
                .comparingDouble((KnowledgeNode node) -> masteryScores.getOrDefault(node.getId(), 0.0))
                .thenComparing(KnowledgeNodeDependencyResolver.defaultOrder());
        return knowledgeNodeDependencyResolver.sortByPrerequisites(nodes, masteryOrder);
    }

    @Override
    public void updateLearningProgress(Long empId, Long nodeId, String status) {
        // 修复：原实现为空壳（仅日志，返回成功但数据被丢弃）。
        // 落库到 learning_progress_log（node_id + progress_status），闭环学习进度回流。
        String normalizedStatus = status != null ? status.toUpperCase() : "IN_PROGRESS";
        if (!List.of("IN_PROGRESS", "COMPLETED", "ABANDONED").contains(normalizedStatus)) {
            log.warn("忽略非法的学习进度状态: empId={}, nodeId={}, status={}", empId, nodeId, status);
            normalizedStatus = "IN_PROGRESS";
        }
        try {
            LearningProgressLog progressEntry = new LearningProgressLog();
            progressEntry.setEmpId(empId);
            progressEntry.setNodeId(nodeId);
            progressEntry.setActionType("KNODE_PROGRESS");
            progressEntry.setProgressStatus(normalizedStatus);
            progressEntry.setActionDesc("知识节点学习进度更新: nodeId=" + nodeId + ", status=" + normalizedStatus);
            progressLogMapper.insert(progressEntry);
            log.info("更新学习进度已落库：empId={}, nodeId={}, status={}", empId, nodeId, normalizedStatus);
        } catch (Exception e) {
            log.error("更新学习进度落库失败: empId={}, nodeId={}, status={}, error={}",
                    empId, nodeId, normalizedStatus, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getLearningProgressOverview(Long empId) {
        Map<String, Object> overview = new HashMap<>();

        // 获取领域掌握度
        Map<Long, Double> domainScores = getDomainMasteryScores(empId);
        overview.put("domainScores", domainScores);

        // 计算平均掌握度
        double averageMastery = domainScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        overview.put("averageMastery", averageMastery);

        // 获取薄弱环节
        List<Map<String, Object>> weakPoints = getWeakPoints(empId, 5);
        overview.put("weakPoints", weakPoints);

        // 统计学习进度
        long totalDomains = domainScores.size();
        long masteredDomains = domainScores.values().stream()
                .filter(score -> score >= 80)
                .count();
        overview.put("totalDomains", totalDomains);
        overview.put("masteredDomains", masteredDomains);
        overview.put("masteryRate", totalDomains > 0 ? (double) masteredDomains / totalDomains * 100 : 0);

        return overview;
    }

    private List<LearningResource> findRelatedResources(String abilityName, String nodeName) {
        // 查找相关学习资源
        return resourceMapper.selectList(
                Wrappers.<LearningResource>lambdaQuery()
                        .eq(LearningResource::getStatus, 1)
                        .and(wrapper -> wrapper
                                .like(LearningResource::getAbilityName, abilityName)
                                .or()
                                .like(LearningResource::getTitle, abilityName)
                                .or()
                                .like(LearningResource::getTitle, nodeName))
                        .last("LIMIT 5"));
    }

    private Long getTagIdByName(String abilityName) {
        AbilityTag tag = abilityTagMapper.selectOne(
                Wrappers.<AbilityTag>lambdaQuery()
                        .like(AbilityTag::getTagName, abilityName)
                        .eq(AbilityTag::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return tag != null ? tag.getId() : null;
    }

    private String resolveAbilityName(String formalAbilityName, Long tagId) {
        if (formalAbilityName != null && !formalAbilityName.isBlank()) {
            return formalAbilityName.trim();
        }
        if (tagId != null) {
            AbilityTag tag = abilityTagMapper.selectById(tagId);
            if (tag != null && tag.getTagName() != null && !tag.getTagName().isBlank()) {
                return tag.getTagName().trim();
            }
        }
        return tagId == null ? "未命名能力" : "能力#" + tagId;
    }

    private String normalizeAbilityName(String abilityName) {
        return abilityName == null ? "" : abilityName.trim().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    private int calculatePriority(int currentLevel, int requiredLevel, double masteryScore) {
        int levelGap = requiredLevel - currentLevel;

        // 优先级计算规则：
        // 1. 等级差距越大，优先级越高
        // 2. 掌握度越低，优先级越高
        // 3. 综合考虑等级差距和掌握度

        int priority = 0;

        // 等级差距权重
        if (levelGap >= 3) {
            priority += 30;
        } else if (levelGap >= 2) {
            priority += 20;
        } else {
            priority += 10;
        }

        // 掌握度权重
        if (masteryScore < 30) {
            priority += 30;
        } else if (masteryScore < 60) {
            priority += 20;
        } else {
            priority += 10;
        }

        return priority;
    }
}
