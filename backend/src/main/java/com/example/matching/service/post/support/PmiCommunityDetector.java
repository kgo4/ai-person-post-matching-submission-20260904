package com.example.matching.service.post.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Small-data weighted PMI graph and deterministic community expansion without a new graph dependency. */
@Component
public class PmiCommunityDetector {

    public Result detect(List<Set<Long>> documents, double pmiThreshold, int minCommunitySize) {
        Map<Long, Integer> occurrences = new HashMap<>();
        Map<Pair, Integer> pairCounts = new HashMap<>();
        for (Set<Long> document : documents) {
            List<Long> ids = new ArrayList<>(document);
            ids.sort(Long::compareTo);
            ids.forEach(id -> occurrences.merge(id, 1, Integer::sum));
            for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++)
                pairCounts.merge(new Pair(ids.get(i), ids.get(j)), 1, Integer::sum);
        }
        int total = Math.max(1, documents.size());
        Map<Pair, Double> pmiWeights = new LinkedHashMap<>();
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        pairCounts.forEach((pair, count) -> {
            double pmi = Math.log((count * (double) total) / (occurrences.get(pair.left) * (double) occurrences.get(pair.right)));
            if (pmi >= pmiThreshold) {
                pmiWeights.put(pair, pmi);
                adjacency.computeIfAbsent(pair.left, ignored -> new LinkedHashSet<>()).add(pair.right);
                adjacency.computeIfAbsent(pair.right, ignored -> new LinkedHashSet<>()).add(pair.left);
            }
        });
        List<Set<Long>> communities = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (Long seed : adjacency.keySet()) {
            if (!visited.add(seed)) continue;
            Set<Long> community = new LinkedHashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>(); queue.add(seed);
            while (!queue.isEmpty()) {
                Long current = queue.removeFirst(); community.add(current);
                for (Long neighbour : adjacency.getOrDefault(current, Set.of())) if (visited.add(neighbour)) queue.addLast(neighbour);
            }
            if (community.size() >= minCommunitySize) communities.add(community);
        }
        return new Result(communities, occurrences, pmiWeights);
    }

    public record Result(List<Set<Long>> communities, Map<Long, Integer> occurrences, Map<Pair, Double> pmiWeights) {
        public double cohesion(Set<Long> community) {
            if (community.size() < 2) return 0d;
            long actual = pmiWeights.keySet().stream().filter(pair -> community.contains(pair.left) && community.contains(pair.right)).count();
            return actual / (double) (community.size() * (community.size() - 1) / 2);
        }
    }

    public record Pair(Long left, Long right) { }
}
