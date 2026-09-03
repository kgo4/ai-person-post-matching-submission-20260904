package com.example.matching.service.kg.support;

import com.example.matching.entity.kg.KnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNodeDependencyResolver {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public List<Long> parsePrerequisiteIds(KnowledgeNode node) {
        if (node == null || node.getPrerequisitesJson() == null || node.getPrerequisitesJson().isBlank()) {
            return List.of();
        }
        try {
            List<Long> values = objectMapper.readValue(node.getPrerequisitesJson(), LONG_LIST_TYPE);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
            for (Long value : values) {
                if (value != null) {
                    distinctIds.add(value);
                }
            }
            return List.copyOf(distinctIds);
        } catch (Exception e) {
            log.warn("Ignore invalid knowledge node prerequisites: nodeId={}, value={}",
                    node.getId(), node.getPrerequisitesJson());
            return List.of();
        }
    }

    public List<KnowledgeNode> sortByPrerequisites(List<KnowledgeNode> nodes,
                                                   Comparator<KnowledgeNode> fallbackOrder) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        Comparator<KnowledgeNode> effectiveOrder = fallbackOrder != null ? fallbackOrder : defaultOrder();
        Map<Long, KnowledgeNode> nodesById = new LinkedHashMap<>();
        List<KnowledgeNode> nodesWithoutId = new ArrayList<>();
        for (KnowledgeNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.getId() == null) {
                nodesWithoutId.add(node);
            } else {
                nodesById.putIfAbsent(node.getId(), node);
            }
        }

        Map<Long, Integer> indegree = new HashMap<>();
        Map<Long, Set<Long>> dependentsByPrerequisite = new HashMap<>();
        nodesById.keySet().forEach(id -> indegree.put(id, 0));

        for (KnowledgeNode node : nodesById.values()) {
            for (Long prerequisiteId : parsePrerequisiteIds(node)) {
                if (!nodesById.containsKey(prerequisiteId) || prerequisiteId.equals(node.getId())) {
                    continue;
                }
                boolean added = dependentsByPrerequisite
                        .computeIfAbsent(prerequisiteId, ignored -> new HashSet<>())
                        .add(node.getId());
                if (added) {
                    indegree.merge(node.getId(), 1, Integer::sum);
                }
            }
        }

        PriorityQueue<KnowledgeNode> ready = new PriorityQueue<>(effectiveOrder);
        for (KnowledgeNode node : nodesById.values()) {
            if (indegree.get(node.getId()) == 0) {
                ready.add(node);
            }
        }

        List<KnowledgeNode> ordered = new ArrayList<>(nodes.size());
        Set<Long> processedIds = new HashSet<>();
        while (!ready.isEmpty()) {
            KnowledgeNode current = ready.poll();
            ordered.add(current);
            processedIds.add(current.getId());
            for (Long dependentId : dependentsByPrerequisite.getOrDefault(current.getId(), Set.of())) {
                int remaining = indegree.merge(dependentId, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(nodesById.get(dependentId));
                }
            }
        }

        nodesById.values().stream()
                .filter(node -> !processedIds.contains(node.getId()))
                .sorted(effectiveOrder)
                .forEach(ordered::add);
        nodesWithoutId.stream().sorted(effectiveOrder).forEach(ordered::add);
        return ordered;
    }

    public static Comparator<KnowledgeNode> defaultOrder() {
        return Comparator
                .comparing(KnowledgeNode::getNodeLevel, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(KnowledgeNode::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(KnowledgeNode::getId, Comparator.nullsLast(Long::compareTo));
    }
}
