package com.example.matching.application.system;

import com.example.matching.dto.system.PromptLogDTO;
import com.example.matching.service.system.AuditQueryService;
import freemarker.template.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptAdminApiFacade {

    private static final String PROMPT_DIR = "classpath:/ai/prompt/*";

    private final Configuration freemarkerConfig;
    private final AuditQueryService auditService;

    public Map<String, Object> listPrompts() {
        List<Map<String, String>> files = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PROMPT_DIR);

            for (Resource res : resources) {
                String filename = res.getFilename();
                if (filename == null) continue;

                Map<String, String> info = new LinkedHashMap<>();
                info.put("name", filename);
                info.put("type", filename.endsWith(".ftl") ? "FTL" : "SYSTEM_MSG");

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                    String firstLine = reader.readLine();
                    info.put("version", firstLine != null ? firstLine.replaceAll("[<#\\-\\-\\s#]+", "").trim() : "unknown");
                }

                files.add(info);
            }
        } catch (Exception e) {
            log.error("列出 Prompt 文件失败", e);
            return Map.of("error", e.getMessage());
        }

        Map<String, List<Map<String, String>>> grouped = files.stream()
                .collect(Collectors.groupingBy(f -> f.get("type")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", files.size());
        result.put("ftlCount", grouped.getOrDefault("FTL", List.of()).size());
        result.put("txtCount", grouped.getOrDefault("SYSTEM_MSG", List.of()).size());
        result.put("files", grouped);
        return result;
    }

    public Map<String, Object> reload() {
        try {
            freemarkerConfig.clearTemplateCache();
            log.info("Prompt 模板缓存已清除，FTL 文件将在下次请求时重新加载");
            return Map.of(
                    "success", true,
                    "message", "FTL Prompt 模板缓存已清除。修改后的 .ftl 文件下次请求时生效。.txt 文件需重编译。",
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.error("清除模板缓存失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> getExperimentResults(int days, String promptName) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<PromptLogDTO> logs = auditService.listPromptLogDtosSince(since, promptName);
        if (logs.isEmpty()) return Map.of("message", "最近 " + days + " 天无埋点数据", "since", since.toString());

        Map<String, Map<String, Object>> grouped = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.promptName() + " | " + l.promptVersion(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            Map<String, Object> stats = new LinkedHashMap<>();
                            stats.put("promptName", list.get(0).promptName());
                            stats.put("version", list.get(0).promptVersion());
                            stats.put("totalCalls", list.size());
                            stats.put("avgLatencyMs", list.stream().mapToLong(l -> l.latencyMs() != null ? l.latencyMs() : 0).average().orElse(0));
                            stats.put("successRate", list.stream().filter(l -> Boolean.TRUE.equals(l.success())).count() * 100.0 / list.size());
                            stats.put("avgFeedbackScore", list.stream()
                                    .filter(l -> l.feedbackScore() != null)
                                    .mapToInt(l -> l.feedbackScore()).average().orElse(0));
                            stats.put("feedbackCount", list.stream().filter(l -> l.feedbackScore() != null).count());
                            return stats;
                        })));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("since", since.toString());
        result.put("days", days);
        result.put("totalCalls", logs.size());
        result.put("groups", grouped);
        return result;
    }
}
