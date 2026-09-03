package com.example.matching.service.contest.report.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.kg.GraphQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 竞赛报告生成引擎：统计聚合 + Markdown 排版 + Prompt 渲染 + AI 分析 + 校验。
 * <p>
 * 从 ContestReportServiceImpl（700 行）中拆分的报告生成组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestReportGenerationEngine {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final GraphQueryPort graphQueryPort;
    private final MatchingQueryPort matchingQueryPort;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final TagQueryPort tagQueryPort;
    private final LangChain4jChatService langChain4jChatService;
    private final AiServiceResilience aiServiceResilience;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    public String generateStatReport(String reportType) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI人岗匹配系统竞赛报告\n\n");
        sb.append("报告类型: ").append(reportType).append("\n");
        sb.append("生成时间: ").append(LocalDateTime.now()).append("\n\n");

        switch (reportType) {
            case "SUMMARY" -> generateSummaryMarkdown(sb);
            case "GRAPH" -> generateGraphMarkdown(sb);
            case "EVIDENCE" -> generateEvidenceMarkdown(sb);
            case "SUBMISSION_CHECKLIST" -> generateChecklistMarkdown(sb);
            case "MATCHING_OVERVIEW" -> {
                Map<String, Object> dataModel = aggregateMatchingStats();
                sb.append(buildFallbackMarkdown(dataModel));
            }
            default -> sb.append("未知报告类型\n");
        }

        return sb.toString();
    }

    public void generateSummaryMarkdown(StringBuilder sb) {
        sb.append("## 项目概述\n\n");
        sb.append("AI人岗匹配系统是一个基于知识图谱和RAG技术的智能招聘匹配平台。\n\n");
        sb.append("### 核心功能\n\n");
        sb.append("- 岗位能力模型自动构建\n");
        sb.append("- 员工能力画像生成\n");
        sb.append("- 智能人岗匹配算法\n");
        sb.append("- RAG知识增强检索\n");
        sb.append("- 岗位演化分析\n");
        sb.append("- 知识图谱可视化\n\n");

        Map<String, Long> nodeTypeCounts = graphQueryPort.countNodesByType();

        sb.append("### 系统规模\n\n");
        sb.append("| 指标 | 数量 |\n");
        sb.append("|------|------|\n");
        sb.append("| 知识图谱节点 | ").append(nodeTypeCounts.values().stream().mapToLong(Long::longValue).sum()).append(" |\n");
        sb.append("| 岗位节点 | ").append(nodeTypeCounts.getOrDefault("POST", 0L)).append(" |\n");
        sb.append("| 能力节点 | ").append(nodeTypeCounts.getOrDefault("ABILITY", 0L)).append(" |\n");
        sb.append("| 员工节点 | ").append(nodeTypeCounts.getOrDefault("EMPLOYEE", 0L)).append(" |\n");
    }

    public void generateGraphMarkdown(StringBuilder sb) {
        sb.append("## 知识图谱\n\n");

        Map<String, Long> nodeTypeCounts = graphQueryPort.countNodesByType();

        sb.append("### 节点分布\n\n");
        sb.append("| 节点类型 | 数量 |\n");
        sb.append("|----------|------|\n");
        nodeTypeCounts.forEach((type, count) ->
                sb.append("| ").append(type).append(" | ").append(count).append(" |\n"));
    }

    public void generateEvidenceMarkdown(StringBuilder sb) {
        sb.append("## 证据中心\n\n");
        sb.append("系统通过多种来源收集能力证据，确保匹配结果可追溯、可验证。\n\n");
        sb.append("### 证据来源\n\n");
        sb.append("- JD导入解析\n");
        sb.append("- 简历解析\n");
        sb.append("- AI测试\n");
        sb.append("- 视频面试\n");
        sb.append("- 人工审核\n");
    }

    public void generateChecklistMarkdown(StringBuilder sb) {
        sb.append("## 竞赛提交清单\n\n");
        sb.append("- [ ] 源代码\n");
        sb.append("- [ ] 部署指南\n");
        sb.append("- [ ] Docker文件\n");
        sb.append("- [ ] P0指标报告\n");
        sb.append("- [ ] P1 RAG/演化证据\n");
        sb.append("- [ ] 图谱快照\n");
        sb.append("- [ ] 演示视频\n");
        sb.append("- [ ] PPT\n");
        sb.append("- [ ] 100 JD测试集\n");
        sb.append("- [ ] 新岗位图谱\n");
        sb.append("- [ ] 演化图谱\n");
    }

    /**
     * 聚合所有匹配记录数据，提取统计维度
     */
    public Map<String, Object> aggregateMatchingStats() {
        Map<String, Object> model = new LinkedHashMap<>();

        // 1. 系统规模
        long totalPosts = postQueryPort.countAllPosts();
        long totalEmployees = talentQueryPort.countAllEmployees();
        List<MatchingRecordDTO> allRecords = matchingQueryPort.listAllRecordsWithAiScore();

        model.put("totalPosts", totalPosts);
        model.put("totalEmployees", totalEmployees);
        model.put("totalMatches", allRecords.size());

        long matchedPostCount = allRecords.stream()
                .map(MatchingRecordDTO::postId).filter(Objects::nonNull).distinct().count();
        model.put("matchedPostCount", matchedPostCount);

        // 2. 整体匹配质量
        if (!allRecords.isEmpty()) {
            List<BigDecimal> scores = allRecords.stream()
                    .map(r -> r.aiMatchScore() != null ? r.aiMatchScore() : BigDecimal.ZERO)
                    .sorted().collect(Collectors.toList());

            BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            model.put("avgMatchScore", total.divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP));

            int mid = scores.size() / 2;
            model.put("medianMatchScore", scores.size() % 2 == 0
                    ? scores.get(mid - 1).add(scores.get(mid)).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP)
                    : scores.get(mid));

            int[] buckets = new int[5];
            String[] bucketRanges = {"0-20", "21-40", "41-60", "61-80", "81-100"};
            for (BigDecimal s : scores) {
                int v = s.intValue();
                if (v <= 20) buckets[0]++;
                else if (v <= 40) buckets[1]++;
                else if (v <= 60) buckets[2]++;
                else if (v <= 80) buckets[3]++;
                else buckets[4]++;
            }
            List<Map<String, Object>> scoreDist = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("range", bucketRanges[i]);
                b.put("count", buckets[i]);
                b.put("percent", BigDecimal.valueOf(buckets[i] * 100.0 / scores.size()).setScale(1, RoundingMode.HALF_UP));
                scoreDist.add(b);
            }
            model.put("scoreDistribution", scoreDist);

            Map<Integer, Long> statusCounts = allRecords.stream()
                    .collect(Collectors.groupingBy(r -> r.matchStatus() != null ? r.matchStatus() : 0,
                            Collectors.counting()));
            String[] statusNames = {"待审核", "强适配", "适配", "待观察", "不适配"};
            List<Map<String, Object>> statusDist = new ArrayList<>();
            for (int i = 0; i < statusNames.length; i++) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("status", statusNames[i]);
                s.put("count", statusCounts.getOrDefault(i, 0L));
                statusDist.add(s);
            }
            model.put("matchStatusDistribution", statusDist);

            Map<Integer, Long> levelCounts = allRecords.stream()
                    .collect(Collectors.groupingBy(r -> r.screeningLevel() != null ? r.screeningLevel() : 0,
                            Collectors.counting()));
            String[] levelNames = {"未筛选", "L1硬性条件通过", "L2能力标签通过", "L3 AI深度匹配完成"};
            List<Map<String, Object>> funnel = new ArrayList<>();
            for (int i = 1; i < levelNames.length; i++) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("name", levelNames[i]);
                l.put("count", levelCounts.getOrDefault(i, 0L));
                l.put("percent", BigDecimal.valueOf(levelCounts.getOrDefault(i, 0L) * 100.0 / scores.size())
                        .setScale(1, RoundingMode.HALF_UP));
                funnel.add(l);
            }
            model.put("screeningFunnel", funnel);

        } else {
            model.put("avgMatchScore", "无数据");
            model.put("medianMatchScore", "无数据");
            model.put("scoreDistribution", Collections.emptyList());
            model.put("matchStatusDistribution", Collections.emptyList());
            model.put("screeningFunnel", Collections.emptyList());
        }

        // 3. 岗位缺口 Top 5
        List<PostDTO> posts = postQueryPort.listAllPosts();
        if (!allRecords.isEmpty() && !posts.isEmpty()) {
            Map<Long, String> postNameMap = posts.stream()
                    .collect(Collectors.toMap(PostDTO::id,
                            p -> p.postName() != null ? p.postName() : "未知"));

            List<Map<String, Object>> postGaps = allRecords.stream()
                    .filter(r -> r.postId() != null)
                    .collect(Collectors.groupingBy(MatchingRecordDTO::postId,
                            Collectors.collectingAndThen(Collectors.toList(), list -> {
                                BigDecimal avg = list.stream()
                                        .map(r -> r.aiMatchScore() != null ? r.aiMatchScore() : BigDecimal.ZERO)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("postId", list.get(0).postId());
                                m.put("postName", postNameMap.getOrDefault(list.get(0).postId(), "未知"));
                                m.put("avgScore", avg);
                                m.put("matchCount", list.size());
                                return m;
                            })))
                    .values().stream()
                    .sorted(Comparator.comparing(m -> (BigDecimal) m.get("avgScore")))
                    .limit(5)
                    .collect(Collectors.toList());
            model.put("topGapPosts", postGaps);
        } else {
            model.put("topGapPosts", Collections.emptyList());
        }

        // 4. 能力缺失 Top 10
        List<PostAbilityDTO> allRequirements = postQueryPort.listAllPostAbilityModels();
        List<EmployeeAbilityDTO> allAbilities = talentQueryPort.listAllAbilities();
        List<TagDTO> allTags = tagQueryPort.listAllTags();
        Map<Long, String> tagNameMap = allTags.stream()
                .collect(Collectors.toMap(TagDTO::id,
                        t -> t.tagName() != null ? t.tagName() : "未知"));

        if (!allRequirements.isEmpty() && !allAbilities.isEmpty()) {
            Map<Long, Double> reqAvg = allRequirements.stream()
                    .collect(Collectors.groupingBy(PostAbilityDTO::tagId,
                            Collectors.averagingInt(r -> r.minRequiredLevel() != null ? r.minRequiredLevel() : 0)));

            Map<Long, Double> empAvg = allAbilities.stream()
                    .collect(Collectors.groupingBy(EmployeeAbilityDTO::tagId,
                            Collectors.averagingInt(a -> a.masteryLevel() != null ? a.masteryLevel() : 0)));

            List<Map<String, Object>> abilityGaps = reqAvg.entrySet().stream()
                    .filter(e -> empAvg.containsKey(e.getKey()))
                    .map(e -> {
                        double required = e.getValue();
                        double actual = empAvg.get(e.getKey());
                        double gap = required - actual;
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("abilityName", tagNameMap.getOrDefault(e.getKey(), "未知#" + e.getKey()));
                        m.put("avgRequired", BigDecimal.valueOf(required).setScale(1, RoundingMode.HALF_UP));
                        m.put("avgActual", BigDecimal.valueOf(actual).setScale(1, RoundingMode.HALF_UP));
                        m.put("gap", BigDecimal.valueOf(gap).setScale(1, RoundingMode.HALF_UP));
                        return m;
                    })
                    .filter(m -> ((BigDecimal) m.get("gap")).compareTo(BigDecimal.ZERO) > 0)
                    .sorted((a, b) -> ((BigDecimal) b.get("gap")).compareTo((BigDecimal) a.get("gap")))
                    .limit(10)
                    .collect(Collectors.toList());
            model.put("topGapAbilities", abilityGaps);
        } else {
            model.put("topGapAbilities", Collections.emptyList());
        }

        return model;
    }

    /**
     * 渲染 FreeMarker 模板为 prompt 文本
     */
    public String renderPromptTemplate(String templateName, Map<String, Object> dataModel) {
        return promptTemplateService.render(templateName, dataModel);
    }

    /**
     * AI 不可用时的降级报告：纯统计表格，不含 AI 叙述
     */
    public String buildFallbackMarkdown(Map<String, Object> dataModel) {
        StringBuilder sb = new StringBuilder();
        sb.append("> AI 服务暂不可用，以下为系统自动生成的统计数据。\n\n");

        sb.append("## 系统规模\n\n");
        sb.append("| 指标 | 数量 |\n|---|---|\n");
        sb.append("| 岗位总数 | ").append(dataModel.get("totalPosts")).append(" |\n");
        sb.append("| 员工总数 | ").append(dataModel.get("totalEmployees")).append(" |\n");
        sb.append("| 匹配记录总数 | ").append(dataModel.get("totalMatches")).append(" |\n\n");

        Object avg = dataModel.get("avgMatchScore");
        Object med = dataModel.get("medianMatchScore");
        if (!"无数据".equals(avg)) {
            sb.append("## 整体匹配质量\n\n");
            sb.append("| 指标 | 数值 |\n|---|---|\n");
            sb.append("| 平均匹配分 | ").append(avg).append(" |\n");
            sb.append("| 中位数匹配分 | ").append(med).append(" |\n\n");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreDist = (List<Map<String, Object>>) dataModel.get("scoreDistribution");
        if (scoreDist != null && !scoreDist.isEmpty()) {
            sb.append("### 分数分布\n\n");
            sb.append("| 区间 | 人数 | 占比 |\n|---|---|---|\n");
            for (Map<String, Object> b : scoreDist) {
                sb.append("| ").append(b.get("range")).append(" | ")
                        .append(b.get("count")).append(" | ")
                        .append(b.get("percent")).append("% |\n");
            }
            sb.append("\n");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topPosts = (List<Map<String, Object>>) dataModel.get("topGapPosts");
        if (topPosts != null && !topPosts.isEmpty()) {
            sb.append("## 岗位缺口 Top 5\n\n");
            sb.append("| 岗位 | 平均匹配分 | 匹配人数 |\n|---|---|---|\n");
            for (Map<String, Object> p : topPosts) {
                sb.append("| ").append(p.get("postName")).append(" | ")
                        .append(p.get("avgScore")).append(" | ")
                        .append(p.get("matchCount")).append(" |\n");
            }
            sb.append("\n");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topAbilities = (List<Map<String, Object>>) dataModel.get("topGapAbilities");
        if (topAbilities != null && !topAbilities.isEmpty()) {
            sb.append("## 能力缺失 Top 10\n\n");
            sb.append("| 能力 | 岗位要求 | 员工实际 | 差距 |\n|---|---|---|---|\n");
            for (Map<String, Object> a : topAbilities) {
                sb.append("| ").append(a.get("abilityName")).append(" | ")
                        .append(a.get("avgRequired")).append(" | ")
                        .append(a.get("avgActual")).append(" | ")
                        .append(a.get("gap")).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("---\n*AI 服务恢复后将自动生成包含数据解读和建议的完整报告。*\n");
        return sb.toString();
    }

    /**
     * 构建结构化 JSON（真实数据，非 AI 生成）
     */
    public String buildStructuredJson(String reportType, String markdown) {
        try {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("type", reportType);
            json.put("generatedAt", LocalDateTime.now().toString());

            // 从数据库聚合真实指标
            Map<String, Object> summary = new LinkedHashMap<>();
            long totalPosts = postQueryPort.countAllPosts();
            long totalEmployees = talentQueryPort.countAllEmployees();
            long totalMatches = matchingQueryPort.countAllRecordsWithAiScore();
            summary.put("totalPosts", totalPosts);
            summary.put("totalEmployees", totalEmployees);
            summary.put("totalMatches", totalMatches);
            json.put("summary", summary);

            // 核心指标
            List<Map<String, Object>> metrics = new ArrayList<>();
            List<MatchingRecordDTO> records = matchingQueryPort.listAllRecordsWithAiScore();
            if (!records.isEmpty()) {
                BigDecimal avgScore = records.stream()
                        .map(r -> r.aiMatchScore() != null ? r.aiMatchScore() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(records.size()), 1, RoundingMode.HALF_UP);
                metrics.add(Map.of("name", "平均匹配分", "value", avgScore.toString()));
            }
            long evidenceCount = evidenceItemMapper.selectCount(
                    Wrappers.<ContestEvidenceItem>lambdaQuery()
                            .eq(ContestEvidenceItem::getEvidenceStatus, "VERIFIED"));
            metrics.add(Map.of("name", "已验证证据数", "value", String.valueOf(evidenceCount)));
            json.put("metrics", metrics);

            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            log.warn("构建结构化 JSON 失败", e);
            return "{\"type\":\"" + reportType + "\",\"generatedAt\":\"" + LocalDateTime.now() + "\"}";
        }
    }

    /**
     * 校验报告内容
     */
    public String validateReport(String markdown, String json) {
        if (markdown == null || markdown.isEmpty()) {
            return "FAILED";
        }
        // 基础校验：报告长度合理
        if (markdown.length() < 50) {
            return "PARTIAL";
        }
        // JSON 可解析
        try {
            objectMapper.readTree(json);
        } catch (Exception e) {
            return "PARTIAL";
        }
        return "PASSED";
    }
}
