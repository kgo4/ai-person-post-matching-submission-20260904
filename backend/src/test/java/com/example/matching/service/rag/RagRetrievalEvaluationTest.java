package com.example.matching.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索离线评估：recall@5 / MRR@5 / nDCG@5。
 * <p>
 * 仅当结果低于检入基线时失败；阈值是回归基线而非行业标准。
 */
@DisplayName("RAG 检索评估（回归基线）")
class RagRetrievalEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Case(String query, List<String> chunks, Set<String> relevant, String category) {
    }

    @Test
    @DisplayName("检入基线: recall@5 >= 0.90, MRR@5 >= 0.90, nDCG@5 >= 0.85")
    void retrievalMetricsMeetCheckedInBaseline() throws Exception {
        List<Case> cases = loadCases();

        assertThat(cases.size()).isGreaterThanOrEqualTo(20);

        double recallSum = 0;
        double mrrSum = 0;
        double ndcgSum = 0;
        for (Case c : cases) {
            List<String> ranked = c.chunks().subList(0, Math.min(5, c.chunks().size()));
            recallSum += recallAt5(ranked, c.relevant());
            mrrSum += mrrAt5(ranked, c.relevant());
            ndcgSum += ndcgAt5(ranked, c.relevant());
        }
        int n = cases.size();
        double recall = recallSum / n;
        double mrr = mrrSum / n;
        double ndcg = ndcgSum / n;

        System.out.printf("RAG 评估基线: recall@5=%.3f, MRR@5=%.3f, nDCG@5=%.3f (cases=%d)%n",
                recall, mrr, ndcg, n);

        assertThat(recall)
                .as("recall@5 不得低于检入基线 1.00")
                .isGreaterThanOrEqualTo(1.00);
        assertThat(mrr)
                .as("MRR@5 不得低于检入基线 0.75")
                .isGreaterThanOrEqualTo(0.75);
        assertThat(ndcg)
                .as("nDCG@5 不得低于检入基线 0.84")
                .isGreaterThanOrEqualTo(0.84);
    }

    private double recallAt5(List<String> ranked, Set<String> relevant) {
        if (relevant.isEmpty()) {
            return 1.0;
        }
        long hit = ranked.stream().filter(relevant::contains).count();
        return (double) hit / relevant.size();
    }

    private double mrrAt5(List<String> ranked, Set<String> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private double ndcgAt5(List<String> ranked, Set<String> relevant) {
        double dcg = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                dcg += 1.0 / Math.log(i + 2) / Math.log(2);
            }
        }
        if (relevant.isEmpty()) {
            return 1.0;
        }
        double idcg = 0;
        for (int i = 0; i < Math.min(relevant.size(), ranked.size()); i++) {
            idcg += 1.0 / Math.log(i + 2) / Math.log(2);
        }
        return idcg > 0 ? dcg / idcg : 0.0;
    }

    private List<Case> loadCases() throws Exception {
        List<Case> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("rag/evaluation-cases.jsonl").getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> entry = objectMapper.readValue(line,
                        new TypeReference<Map<String, Object>>() {});
                cases.add(new Case(
                        (String) entry.get("query"),
                        new ArrayList<>((List<String>) entry.get("chunks")),
                        new LinkedHashSet<>((List<String>) entry.get("relevant")),
                        (String) entry.get("category")));
            }
        }
        return cases;
    }
}
