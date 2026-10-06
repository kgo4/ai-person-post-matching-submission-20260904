package com.example.matching.service.contest.report.impl;

import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.kg.GraphQueryPort;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ContestReportGenerationEngine} 单元测试。
 *
 * <p>覆盖各报告类型的 Markdown 生成、匹配统计聚合（有/无数据、Top 缺口）、
 * 降级报告排版、结构化 JSON 与报告校验。全部端口/AI 服务均 mock，不联网。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestReportGenerationEngineTest {

    @Mock private ContestEvidenceItemMapper evidenceItemMapper;
    @Mock private GraphQueryPort graphQueryPort;
    @Mock private MatchingQueryPort matchingQueryPort;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TalentQueryPort talentQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private AiServiceResilience aiServiceResilience;
    @Mock private PromptTemplateService promptTemplateService;

    private ContestReportGenerationEngine engine;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), ContestEvidenceItem.class);
    }

    @BeforeEach
    void setUp() {
        engine = new ContestReportGenerationEngine(
                evidenceItemMapper, graphQueryPort, matchingQueryPort, postQueryPort,
                talentQueryPort, tagQueryPort, langChain4jChatService, aiServiceResilience,
                promptTemplateService, new ObjectMapper());
        when(graphQueryPort.countNodesByType()).thenReturn(Map.of("POST", 5L, "ABILITY", 10L, "EMPLOYEE", 3L));
        when(postQueryPort.countAllPosts()).thenReturn(10L);
        when(talentQueryPort.countAllEmployees()).thenReturn(20L);
        when(matchingQueryPort.listAllRecordsWithAiScore()).thenReturn(Collections.emptyList());
        when(matchingQueryPort.countAllRecordsWithAiScore()).thenReturn(0L);
        when(postQueryPort.listAllPosts()).thenReturn(Collections.emptyList());
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(talentQueryPort.listAllAbilities()).thenReturn(Collections.emptyList());
        when(tagQueryPort.listAllTags()).thenReturn(Collections.emptyList());
        when(evidenceItemMapper.selectCount(any())).thenReturn(0L);
    }

    private static MatchingRecordDTO record(Long empId, Long postId, String score, Integer status, Integer level) {
        return new MatchingRecordDTO(1L, empId, postId, "岗位",
                new BigDecimal(score), new BigDecimal(score), null, null, null, null,
                "v1", status, level, null, null);
    }

    // ==================== generateStatReport 各类型 ====================

    @Test
    @DisplayName("generateStatReport：SUMMARY 输出项目概述与系统规模")
    void generateStatReport_summary() {
        String md = engine.generateStatReport("SUMMARY");

        assertTrue(md.contains("AI人岗匹配系统竞赛报告"));
        assertTrue(md.contains("## 项目概述"));
        assertTrue(md.contains("| 知识图谱节点 | 18 |"));
    }

    @Test
    @DisplayName("generateStatReport：GRAPH 输出节点分布表")
    void generateStatReport_graph() {
        String md = engine.generateStatReport("GRAPH");

        assertTrue(md.contains("## 知识图谱"));
        assertTrue(md.contains("| POST | 5 |"));
    }

    @Test
    @DisplayName("generateStatReport：EVIDENCE 输出证据来源")
    void generateStatReport_evidence() {
        String md = engine.generateStatReport("EVIDENCE");

        assertTrue(md.contains("## 证据中心"));
        assertTrue(md.contains("AI测试"));
    }

    @Test
    @DisplayName("generateStatReport：SUBMISSION_CHECKLIST 输出清单")
    void generateStatReport_checklist() {
        String md = engine.generateStatReport("SUBMISSION_CHECKLIST");

        assertTrue(md.contains("## 竞赛提交清单"));
        assertTrue(md.contains("- [ ] 演示视频"));
    }

    @Test
    @DisplayName("generateStatReport：MATCHING_OVERVIEW 走降级 Markdown")
    void generateStatReport_matchingOverview() {
        String md = engine.generateStatReport("MATCHING_OVERVIEW");

        assertTrue(md.contains("AI 服务暂不可用"));
        assertTrue(md.contains("| 岗位总数 | 10 |"));
    }

    @Test
    @DisplayName("generateStatReport：未知类型 → 输出「未知报告类型」")
    void generateStatReport_unknown() {
        assertTrue(engine.generateStatReport("NOPE").contains("未知报告类型"));
    }

    // ==================== aggregateMatchingStats ====================

    @Test
    @DisplayName("aggregateMatchingStats：无匹配记录 → 各统计为「无数据」/空列表")
    void aggregateMatchingStats_noData() {
        Map<String, Object> model = engine.aggregateMatchingStats();

        assertEquals(10L, model.get("totalPosts"));
        assertEquals(20L, model.get("totalEmployees"));
        assertEquals(0, model.get("totalMatches"));
        assertEquals("无数据", model.get("avgMatchScore"));
        assertEquals("无数据", model.get("medianMatchScore"));
        assertEquals(Collections.emptyList(), model.get("scoreDistribution"));
        assertEquals(Collections.emptyList(), model.get("matchStatusDistribution"));
        assertEquals(Collections.emptyList(), model.get("screeningFunnel"));
        assertEquals(Collections.emptyList(), model.get("topGapPosts"));
        assertEquals(Collections.emptyList(), model.get("topGapAbilities"));
    }

    @Test
    @DisplayName("aggregateMatchingStats：奇数记录 → 中位数为中间值，分桶/状态/漏斗齐全")
    void aggregateMatchingStats_oddRecords() {
        when(matchingQueryPort.listAllRecordsWithAiScore()).thenReturn(List.of(
                record(1L, 100L, "30", 0, 1),
                record(2L, 100L, "50", 1, 2),
                record(3L, 100L, "90", 2, 3)));
        when(postQueryPort.listAllPosts()).thenReturn(List.of(
                new PostQueryPort.PostDTO(100L, "Java后端", "P1", "P5", 1L, 1, "jd")));

        Map<String, Object> model = engine.aggregateMatchingStats();

        assertEquals(3, model.get("totalMatches"));
        assertEquals(1L, model.get("matchedPostCount"));
        assertEquals(new BigDecimal("56.7"), model.get("avgMatchScore"));
        assertEquals(new BigDecimal("50"), model.get("medianMatchScore"));
        assertEquals(5, ((List<?>) model.get("scoreDistribution")).size());
        assertEquals(5, ((List<?>) model.get("matchStatusDistribution")).size());
        assertEquals(3, ((List<?>) model.get("screeningFunnel")).size());
        assertEquals(1, ((List<?>) model.get("topGapPosts")).size());
    }

    @Test
    @DisplayName("aggregateMatchingStats：偶数记录 → 中位数为两值均值")
    void aggregateMatchingStats_evenRecords() {
        when(matchingQueryPort.listAllRecordsWithAiScore()).thenReturn(List.of(
                record(1L, 100L, "20", 0, 1),
                record(2L, 100L, "80", 0, 1)));

        Map<String, Object> model = engine.aggregateMatchingStats();

        assertEquals(new BigDecimal("50.0"), model.get("medianMatchScore"));
    }

    @Test
    @DisplayName("aggregateMatchingStats：岗位缺口按平均分升序取前 5")
    void aggregateMatchingStats_topGapPosts() {
        when(matchingQueryPort.listAllRecordsWithAiScore()).thenReturn(List.of(
                record(1L, 100L, "80", 0, 1),
                record(2L, 101L, "20", 0, 1),
                record(3L, 102L, "50", 0, 1),
                record(4L, 103L, "10", 0, 1),
                record(5L, 104L, "30", 0, 1),
                record(6L, 105L, "40", 0, 1),
                record(7L, 106L, "60", 0, 1)));
        when(postQueryPort.listAllPosts()).thenReturn(List.of(
                new PostQueryPort.PostDTO(100L, "A", "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(101L, "B", "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(102L, null, "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(103L, "D", "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(104L, "E", "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(105L, "F", "P", "P5", 1L, 1, "j"),
                new PostQueryPort.PostDTO(106L, "G", "P", "P5", 1L, 1, "j")));

        Map<String, Object> model = engine.aggregateMatchingStats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) model.get("topGapPosts");
        assertEquals(5, gaps.size());
        assertEquals(new BigDecimal("10.0"), gaps.get(0).get("avgScore"));
    }

    @Test
    @DisplayName("aggregateMatchingStats：能力缺口只保留正差距，按差距降序取前 10")
    void aggregateMatchingStats_topGapAbilities() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 10L, 5, BigDecimal.ONE, 1, 1, "v1", null, "Java"),
                new PostQueryPort.PostAbilityDTO(2L, 100L, 11L, 2, BigDecimal.ONE, 1, 0, "v1", null, "MySQL")));
        when(talentQueryPort.listAllAbilities()).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 1L, 10L, 2, "M", null, null, null, "Java"),
                new TalentQueryPort.EmployeeAbilityDTO(2L, 1L, 11L, 4, "M", null, null, null, "MySQL")));
        when(tagQueryPort.listAllTags()).thenReturn(List.of(
                new TagQueryPort.TagDTO(10L, "Java", "T", "TECH", null, 2, null, null, "SYS", null, null, null),
                new TagQueryPort.TagDTO(11L, "MySQL", "T", "TECH", null, 2, null, null, "SYS", null, null, null)));

        Map<String, Object> model = engine.aggregateMatchingStats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) model.get("topGapAbilities");
        assertEquals(1, gaps.size(), "只有 Java 存在正差距");
        assertEquals("Java", gaps.get(0).get("abilityName"));
        assertEquals(new BigDecimal("3.0"), gaps.get(0).get("gap"));
    }

    @Test
    @DisplayName("aggregateMatchingStats：tagId 缺失的能力用「未知」兜底")
    void aggregateMatchingStats_minRequiredLevelNull() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 10L, null, BigDecimal.ONE, 1, 1, "v1", null, "Java")));
        when(talentQueryPort.listAllAbilities()).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 1L, 10L, null, "M", null, null, null, "Java")));

        Map<String, Object> model = engine.aggregateMatchingStats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) model.get("topGapAbilities");
        // 缺失等级按 0 处理，差距为 0 → 被过滤掉
        assertTrue(gaps.isEmpty());
    }

    // ==================== renderPromptTemplate / buildFallbackMarkdown ====================

    @Test
    @DisplayName("renderPromptTemplate：代理到模板服务")
    void renderPromptTemplate_delegates() {
        when(promptTemplateService.render(anyString(), any())).thenReturn("PROMPT");

        assertEquals("PROMPT", engine.renderPromptTemplate("t.ftl", Map.of("k", "v")));
    }

    @Test
    @DisplayName("buildFallbackMarkdown：完整数据 → 含规模/质量/分布/缺口各段")
    void buildFallbackMarkdown_full() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("totalPosts", 10L);
        model.put("totalEmployees", 20L);
        model.put("totalMatches", 30L);
        model.put("avgMatchScore", new BigDecimal("70.0"));
        model.put("medianMatchScore", new BigDecimal("68.0"));
        model.put("scoreDistribution", List.of(Map.of("range", "61-80", "count", 5, "percent", BigDecimal.valueOf(50.0))));
        model.put("topGapPosts", List.of(Map.of("postName", "Java", "avgScore", new BigDecimal("30.0"), "matchCount", 2)));
        model.put("topGapAbilities", List.of(Map.of("abilityName", "Kafka",
                "avgRequired", new BigDecimal("4.0"), "avgActual", new BigDecimal("2.0"), "gap", new BigDecimal("2.0"))));

        String md = engine.buildFallbackMarkdown(model);

        assertTrue(md.contains("## 系统规模"));
        assertTrue(md.contains("## 整体匹配质量"));
        assertTrue(md.contains("### 分数分布"));
        assertTrue(md.contains("## 岗位缺口 Top 5"));
        assertTrue(md.contains("## 能力缺失 Top 10"));
    }

    @Test
    @DisplayName("buildFallbackMarkdown：无数据 → 只保留规模段")
    void buildFallbackMarkdown_noData() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("totalPosts", 0L);
        model.put("totalEmployees", 0L);
        model.put("totalMatches", 0L);
        model.put("avgMatchScore", "无数据");
        model.put("medianMatchScore", "无数据");
        model.put("scoreDistribution", Collections.emptyList());
        model.put("topGapPosts", Collections.emptyList());
        model.put("topGapAbilities", Collections.emptyList());

        String md = engine.buildFallbackMarkdown(model);

        assertTrue(md.contains("## 系统规模"));
        assertFalse(md.contains("## 整体匹配质量"));
        assertFalse(md.contains("### 分数分布"));
        assertTrue(md.contains("AI 服务恢复后"));
    }

    // ==================== buildStructuredJson ====================

    @Test
    @DisplayName("buildStructuredJson：有记录 → 含平均匹配分与已验证证据数")
    void buildStructuredJson_withRecords() {
        when(matchingQueryPort.listAllRecordsWithAiScore()).thenReturn(List.of(
                record(1L, 100L, "80", 0, 1), record(2L, 100L, "60", 0, 1)));
        when(evidenceItemMapper.selectCount(any())).thenReturn(7L);

        String json = engine.buildStructuredJson("SUMMARY", "# md");

        assertTrue(json.contains("\"type\":\"SUMMARY\""));
        assertTrue(json.contains("平均匹配分"));
        assertTrue(json.contains("已验证证据数"));
        assertTrue(json.contains("\"value\":\"7\""));
    }

    @Test
    @DisplayName("buildStructuredJson：无记录 → 不含平均匹配分，但仍有证据数")
    void buildStructuredJson_noRecords() {
        String json = engine.buildStructuredJson("GRAPH", "");

        assertFalse(json.contains("平均匹配分"));
        assertTrue(json.contains("已验证证据数"));
    }

    // ==================== validateReport ====================

    @Test
    @DisplayName("validateReport：null/空 → FAILED")
    void validateReport_failed() {
        assertEquals("FAILED", engine.validateReport(null, "{}"));
        assertEquals("FAILED", engine.validateReport("", "{}"));
    }

    @Test
    @DisplayName("validateReport：正文过短 → PARTIAL")
    void validateReport_partialTooShort() {
        assertEquals("PARTIAL", engine.validateReport("太短", "{}"));
    }

    @Test
    @DisplayName("validateReport：JSON 不可解析 → PARTIAL")
    void validateReport_partialBadJson() {
        String md = "x".repeat(60);

        assertEquals("PARTIAL", engine.validateReport(md, "not-a-json"));
    }

    @Test
    @DisplayName("validateReport：长度足够且 JSON 可解析 → PASSED")
    void validateReport_passed() {
        String md = "x".repeat(60);

        assertEquals("PASSED", engine.validateReport(md, "{\"a\":1}"));
    }
}
