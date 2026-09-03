package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.ChangeTypeEnum;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位演化仪表盘：时间线、统计、趋势与图谱视图。
 * <p>
 * 从 PostEvolutionServiceImpl（1100+ 行）中拆分的只读查询组件，
 * 不产生任何业务写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEvolutionDashboardService {

    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;

    public List<Map<String, Object>> getTimelineEvents(Long postId, String range, int limit) {
        LocalDateTime startTime = calculateStartTime(range);

        LambdaQueryWrapper<PostEvolutionChangeItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(PostEvolutionChangeItem::getCreatedTime, startTime);
        wrapper.orderByDesc(PostEvolutionChangeItem::getCreatedTime);
        wrapper.last("LIMIT " + limit);
        List<PostEvolutionChangeItem> items = changeItemMapper.selectList(wrapper);

        Set<Long> taskIds = items.stream().map(PostEvolutionChangeItem::getTaskId).collect(Collectors.toSet());
        Map<Long, PostEvolutionTask> taskMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<PostEvolutionTask> tasks = taskMapper.selectBatchIds(taskIds);
            taskMap = tasks.stream().collect(Collectors.toMap(PostEvolutionTask::getId, t -> t, (a, b) -> a));
        }

        if (postId != null) {
            taskMap.entrySet().removeIf(entry -> !postId.equals(entry.getValue().getPostId()));
            Set<Long> filteredTaskIds = taskMap.keySet();
            items.removeIf(item -> !filteredTaskIds.contains(item.getTaskId()));
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (PostEvolutionChangeItem item : items) {
            PostEvolutionTask task = taskMap.get(item.getTaskId());
            if (task == null) continue;

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", String.valueOf(item.getId()));
            event.put("time", item.getCreatedTime() != null ? item.getCreatedTime().toString() : "");
            event.put("title", getChangeTitle(item.getChangeType()));
            event.put("description", buildChangeDescription(item, task));
            event.put("type", getChangeTypeClass(item.getChangeType()));
            event.put("icon", getChangeIcon(item.getChangeType()));
            event.put("taskId", task.getId());
            event.put("taskCode", task.getTaskCode());
            event.put("postId", task.getPostId());
            event.put("abilityName", item.getAbilityName());
            event.put("changeType", item.getChangeType());
            event.put("confidence", item.getConfidenceScore());

            List<String> abilities = new ArrayList<>();
            if (item.getAbilityName() != null) {
                abilities.add(item.getAbilityName());
            }
            event.put("abilities", abilities);

            events.add(event);
        }

        return events;
    }

    public Map<String, Object> getDashboardStats(String range) {
        LocalDateTime startTime = calculateStartTime(range);
        Map<String, Object> stats = new LinkedHashMap<>();

        Long totalTasks = taskMapper.selectCount(
                new LambdaQueryWrapper<PostEvolutionTask>()
                        .ge(PostEvolutionTask::getCreatedTime, startTime));

        Long completedTasks = taskMapper.selectCount(
                new LambdaQueryWrapper<PostEvolutionTask>()
                        .ge(PostEvolutionTask::getCreatedTime, startTime)
                        .eq(PostEvolutionTask::getTaskStatus, TaskStatusEnum.APPLIED.getCode()));

        Long pendingChanges = changeItemMapper.selectCount(
                new LambdaQueryWrapper<PostEvolutionChangeItem>()
                        .ge(PostEvolutionChangeItem::getCreatedTime, startTime)
                        .eq(PostEvolutionChangeItem::getConfirmStatus, "PENDING"));

        Long highRiskChanges = changeItemMapper.selectCount(
                new LambdaQueryWrapper<PostEvolutionChangeItem>()
                        .ge(PostEvolutionChangeItem::getCreatedTime, startTime)
                        .lt(PostEvolutionChangeItem::getConfidenceScore, 50));

        stats.put("totalTasks", totalTasks != null ? totalTasks : 0);
        stats.put("completedTasks", completedTasks != null ? completedTasks : 0);
        stats.put("pendingChanges", pendingChanges != null ? pendingChanges : 0);
        stats.put("highRiskChanges", highRiskChanges != null ? highRiskChanges : 0);

        return stats;
    }

    public Map<String, Object> getEvolutionTrends(String range) {
        LocalDateTime startTime = calculateStartTime(range);
        Map<String, Object> trends = new LinkedHashMap<>();

        List<PostEvolutionChangeItem> allItems = changeItemMapper.selectList(
                new LambdaQueryWrapper<PostEvolutionChangeItem>()
                        .ge(PostEvolutionChangeItem::getCreatedTime, startTime));

        long addedCount = allItems.stream().filter(i -> ChangeTypeEnum.ADDED.getCode().equals(i.getChangeType())).count();
        long removedCount = allItems.stream().filter(i -> ChangeTypeEnum.REMOVED.getCode().equals(i.getChangeType())).count();
        long updatedCount = allItems.stream().filter(i -> i.getChangeType() != null && i.getChangeType().startsWith("UPDATED_")).count();

        trends.put("added", addedCount);
        trends.put("removed", removedCount);
        trends.put("updated", updatedCount);
        trends.put("total", allItems.size());

        Map<String, Map<String, Long>> monthlyTrends = new LinkedHashMap<>();
        for (PostEvolutionChangeItem item : allItems) {
            if (item.getCreatedTime() == null) continue;
            String month = item.getCreatedTime().getYear() + "-" + String.format("%02d", item.getCreatedTime().getMonthValue());
            monthlyTrends.computeIfAbsent(month, k -> new LinkedHashMap<>());
            Map<String, Long> monthData = monthlyTrends.get(month);
            String type = getChangeTypeClass(item.getChangeType());
            monthData.merge(type, 1L, Long::sum);
        }
        trends.put("monthly", monthlyTrends);

        return trends;
    }

    public Map<String, Object> getEvolutionGraph(Long postId, String timePoint) {
        Map<String, Object> graph = new LinkedHashMap<>();

        LambdaQueryWrapper<PostAbilityModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAbilityModel::getPostId, postId);
        wrapper.eq(PostAbilityModel::getIsDeleted, 0);
        List<PostAbilityModel> currentAbilities = postAbilityModelMapper.selectList(wrapper);

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>();

        Map<String, Object> postNode = new LinkedHashMap<>();
        postNode.put("id", "post_" + postId);
        postNode.put("label", "岗位 #" + postId);
        postNode.put("type", "post");
        postNode.put("size", 40);
        nodes.add(postNode);

        for (PostAbilityModel ability : currentAbilities) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "ability_" + ability.getTagId());
            node.put("label", "能力 #" + ability.getTagId());
            node.put("type", ability.getIsCore() != null && ability.getIsCore() == 1 ? "core" : "normal");
            node.put("level", ability.getMinRequiredLevel());
            node.put("weight", ability.getWeight());
            node.put("size", 20 + (ability.getWeight() != null ? ability.getWeight().intValue() / 5 : 0));
            nodes.add(node);
            nodeMap.put(ability.getTagId(), node);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (PostAbilityModel ability : currentAbilities) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", "post_" + postId);
            edge.put("target", "ability_" + ability.getTagId());
            edge.put("weight", ability.getWeight() != null ? ability.getWeight().doubleValue() : 1.0);
            edges.add(edge);
        }

        List<PostEvolutionChangeItem> recentChanges = changeItemMapper.selectList(
                new LambdaQueryWrapper<PostEvolutionChangeItem>()
                        .in(PostEvolutionChangeItem::getTaskId,
                                taskMapper.selectList(
                                        new LambdaQueryWrapper<PostEvolutionTask>()
                                                .eq(PostEvolutionTask::getPostId, postId))
                                        .stream()
                                        .map(PostEvolutionTask::getId)
                                        .collect(Collectors.toList()))
                        .orderByDesc(PostEvolutionChangeItem::getCreatedTime)
                        .last("LIMIT 50"));

        Set<Long> changeNodeIds = new HashSet<>();
        for (PostEvolutionChangeItem change : recentChanges) {
            if (change.getTagId() == null) continue;
            if (changeNodeIds.contains(change.getTagId())) continue;
            changeNodeIds.add(change.getTagId());

            Map<String, Object> changeNode = new LinkedHashMap<>();
            changeNode.put("id", "change_" + change.getTagId() + "_" + change.getId());
            changeNode.put("label", change.getAbilityName());
            changeNode.put("type", "change");
            changeNode.put("changeType", change.getChangeType());
            changeNode.put("size", 15);
            nodes.add(changeNode);

            Map<String, Object> changeEdge = new LinkedHashMap<>();
            changeEdge.put("source", "change_" + change.getTagId() + "_" + change.getId());
            changeEdge.put("target", "ability_" + change.getTagId());
            changeEdge.put("type", "change");
            changeEdge.put("changeType", change.getChangeType());
            edges.add(changeEdge);
        }

        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("postId", postId);

        return graph;
    }

    // ===== 辅助方法 =====

    private LocalDateTime calculateStartTime(String range) {
        LocalDateTime now = LocalDateTime.now();
        if ("7d".equals(range)) {
            return now.minusDays(7);
        } else if ("90d".equals(range)) {
            return now.minusDays(90);
        } else {
            return now.minusDays(30);
        }
    }

    private String getChangeTitle(String changeType) {
        if (changeType == null) return "能力变更";
        switch (changeType) {
            case "ADDED": return "新增能力项";
            case "REMOVED": return "能力项移除";
            case "UPDATED_LEVEL": return "能力等级调整";
            case "UPDATED_WEIGHT": return "能力权重调整";
            case "UPDATED_CORE": return "核心标志调整";
            default: return "能力变更";
        }
    }

    private String buildChangeDescription(PostEvolutionChangeItem item, PostEvolutionTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位#").append(task.getPostId()).append(" ");
        sb.append(item.getAbilityName()).append(" ");

        switch (item.getChangeType()) {
            case "ADDED":
                sb.append("已添加到能力模型");
                break;
            case "REMOVED":
                sb.append("已从能力模型移除");
                break;
            case "UPDATED_LEVEL":
                sb.append("等级从L").append(item.getOldLevel()).append("调整为L").append(item.getNewLevel());
                break;
            case "UPDATED_WEIGHT":
                sb.append("权重从").append(item.getOldWeight()).append("调整为").append(item.getNewWeight());
                break;
            case "UPDATED_CORE":
                sb.append(item.getNewIsCore() == 1 ? "标记为核心能力" : "取消核心能力标记");
                break;
            default:
                break;
        }

        return sb.toString();
    }

    private String getChangeTypeClass(String changeType) {
        if (changeType == null) return "updated";
        switch (changeType) {
            case "ADDED": return "added";
            case "REMOVED": return "removed";
            default: return "updated";
        }
    }

    private String getChangeIcon(String changeType) {
        if (changeType == null) return "Edit";
        switch (changeType) {
            case "ADDED": return "Plus";
            case "REMOVED": return "Delete";
            default: return "Edit";
        }
    }
}
