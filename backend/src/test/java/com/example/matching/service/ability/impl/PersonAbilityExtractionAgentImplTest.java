package com.example.matching.service.ability.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.common.util.PersonAbilityClaimNormalizer;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.service.harness.AiTrustHarnessService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * {@link PersonAbilityExtractionAgentImpl} 单元测试。
 *
 * <p>覆盖 6 个来源的提取路径、简历解析空/异常降级与时效性评分的各档位分支。
 * 所有 Mapper 与 normalizer 均 mock，不触达数据库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonAbilityExtractionAgentImplTest {

    @Mock private EmpResumeParseMapper resumeParseMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PersonAbilityClaimMapper claimMapper;
    @Mock private AiTrustHarnessService aiTrustHarnessService;
    @Mock private PersonAbilityClaimNormalizer claimNormalizer;

    private PersonAbilityExtractionAgentImpl agent;

    @BeforeAll
    static void initMpCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpAbility.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), EmpResumeParse.class);
    }

    @BeforeEach
    void setUp() {
        agent = new PersonAbilityExtractionAgentImpl(
                resumeParseMapper, empAbilityMapper, claimMapper, aiTrustHarnessService, claimNormalizer);
    }

    private EmpAbility ability(Long tagId, Integer level, String source, LocalDate date) {
        EmpAbility a = new EmpAbility();
        a.setEmpId(100L);
        a.setTagId(tagId);
        a.setMasteryLevel(level);
        a.setEvaluationSource(source);
        a.setEvaluationDate(date);
        a.setRemark("备注");
        return a;
    }

    private PersonAbilityClaim dtoClaim(String name, Integer level, String evidence) {
        PersonAbilityClaim c = new PersonAbilityClaim();
        c.setAbilityName(name);
        c.setNormalizedAbilityName(name.toLowerCase());
        c.setMasteryLevel(level);
        c.setEvidenceText(evidence);
        return c;
    }

    // ==================== extractFromResume ====================

    @Test
    @DisplayName("extractFromResume：正常解析 → 逐条构建 ACTIVE 主张并回填证据片段")
    void extractFromResume_success() throws Exception {
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(1L);
        parse.setAiAnalysisResult("{\"claims\":[]}");
        parse.setParsedContent("简历正文");
        parse.setCreatedTime(LocalDateTime.now().minusDays(10));
        when(resumeParseMapper.selectById(1L)).thenReturn(parse);

        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(
                dtoClaim("Kafka", 3, "用过 Kafka"),
                dtoClaim("Redis", 2, null)));
        when(claimNormalizer.normalize(anyString())).thenReturn(result);

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromResume(100L, 1L);

        assertEquals(2, claims.size());
        assertEquals("RESUME_PARSE", claims.get(0).getSourceType());
        assertEquals("ACTIVE", claims.get(0).getStatus());
        assertEquals("用过 Kafka", claims.get(0).getEvidenceText());
        // 第二条无证据文本 → 回落简历正文
        assertEquals("简历正文", claims.get(1).getEvidenceText());
        // 10 天内 → 时效性 100
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("extractFromResume：解析记录不存在 → 返回空列表")
    void extractFromResume_parseMissing() {
        when(resumeParseMapper.selectById(1L)).thenReturn(null);
        assertTrue(agent.extractFromResume(100L, 1L).isEmpty());
    }

    @Test
    @DisplayName("extractFromResume：无 AI 分析结果 → 返回空列表")
    void extractFromResume_noAiResult() {
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(1L);
        parse.setAiAnalysisResult(null);
        when(resumeParseMapper.selectById(1L)).thenReturn(parse);
        assertTrue(agent.extractFromResume(100L, 1L).isEmpty());
    }

    @Test
    @DisplayName("extractFromResume：normalize 返回 null 或 claims 为空 → 返回空列表")
    void extractFromResume_emptyClaims() throws Exception {
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(1L);
        parse.setAiAnalysisResult("{}");
        when(resumeParseMapper.selectById(1L)).thenReturn(parse);
        when(claimNormalizer.normalize(anyString())).thenReturn(null);
        assertTrue(agent.extractFromResume(100L, 1L).isEmpty());

        PersonAbilityExtractionResult empty = new PersonAbilityExtractionResult();
        empty.setClaims(List.of());
        when(claimNormalizer.normalize(anyString())).thenReturn(empty);
        assertTrue(agent.extractFromResume(100L, 1L).isEmpty());
    }

    @Test
    @DisplayName("extractFromResume：normalize 抛异常 → 吞没返回空列表")
    void extractFromResume_normalizeThrows() throws Exception {
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(1L);
        parse.setAiAnalysisResult("bad");
        when(resumeParseMapper.selectById(1L)).thenReturn(parse);
        when(claimNormalizer.normalize(anyString())).thenThrow(new RuntimeException("parse error"));

        assertTrue(agent.extractFromResume(100L, 1L).isEmpty());
    }

    @Test
    @DisplayName("extractFromResume：简历正文超 500 字 → 证据片段截断追加省略号")
    void extractFromResume_longContentTruncated() throws Exception {
        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(1L);
        parse.setAiAnalysisResult("{}");
        parse.setParsedContent("y".repeat(600));
        parse.setCreatedTime(LocalDateTime.now().minusDays(400));
        when(resumeParseMapper.selectById(1L)).thenReturn(parse);

        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(dtoClaim("Kafka", 3, null)));
        when(claimNormalizer.normalize(anyString())).thenReturn(result);

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromResume(100L, 1L);

        assertEquals(503, claims.get(0).getEvidenceText().length());
        assertTrue(claims.get(0).getEvidenceText().endsWith("..."));
        // 400 天前 → 时效性 20
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(20)));
    }

    // ==================== extractFromAiTest ====================

    @Test
    @DisplayName("extractFromAiTest：有 AI_TEST 能力 → 构建主张；空/未知日期走默认档")
    void extractFromAiTest_success() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(
                ability(5L, 3, "AI_TEST", LocalDate.now().minusDays(20)),
                ability(6L, 4, "AI_TEST", null)));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromAiTest(100L, 9L);

        assertEquals(2, claims.size());
        assertEquals("AI_TEST", claims.get(0).getSourceType());
        assertEquals(9L, claims.get(0).getSourceRefId());
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(100)));
        // null 日期 → 50
        assertEquals(0, claims.get(1).getFreshnessScore().compareTo(BigDecimal.valueOf(50)));
    }

    @Test
    @DisplayName("extractFromAiTest：无记录 → 空列表")
    void extractFromAiTest_empty() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());
        assertTrue(agent.extractFromAiTest(100L, 9L).isEmpty());
    }

    // ==================== extractFromPms / extractFromProject ====================

    @Test
    @DisplayName("extractFromPms：映射为 AI_PROJECT 来源并给出高置信度")
    void extractFromPms_success() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability(5L, 3, "AI_PROJECT", LocalDate.now().minusDays(200))));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromPms(100L);

        assertEquals(1, claims.size());
        assertEquals("AI_PROJECT", claims.get(0).getSourceType());
        assertEquals(0, claims.get(0).getConfidenceScore().compareTo(BigDecimal.valueOf(85)));
        // 200 天前 → 40
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(40)));
    }

    @Test
    @DisplayName("extractFromProject：映射为 AI_PROJECT 来源，置信度 80")
    void extractFromProject_success() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability(5L, 3, "AI_PROJECT", LocalDate.now().minusDays(500))));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromProject(100L);

        assertEquals(1, claims.size());
        assertEquals(0, claims.get(0).getConfidenceScore().compareTo(BigDecimal.valueOf(80)));
        // 超过 365 天 → 20
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(20)));
    }

    // ==================== extractFromLearning / extractFromManual ====================

    @Test
    @DisplayName("extractFromLearning：映射为 LEARNING_PROJECT 来源，时效性 80 档")
    void extractFromLearning_success() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability(5L, 2, "LEARNING_PROJECT", LocalDate.now().minusDays(60))));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromLearning(100L);

        assertEquals(1, claims.size());
        assertEquals("LEARNING_PROJECT", claims.get(0).getSourceType());
        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(80)));
    }

    @Test
    @DisplayName("extractFromManual：映射为 MANUAL 来源")
    void extractFromManual_success() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability(5L, 4, "MANUAL", LocalDate.now().minusDays(10))));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromManual(100L);

        assertEquals(1, claims.size());
        assertEquals("MANUAL", claims.get(0).getSourceType());
    }

    // ==================== 时效性各档位 ====================

    @Test
    @DisplayName("时效性档位：90/180/365 天边界分别得到 80/60/40")
    void freshness_boundaries() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(
                ability(1L, 1, "MANUAL", LocalDate.now().minusDays(90)),
                ability(2L, 1, "MANUAL", LocalDate.now().minusDays(180)),
                ability(3L, 1, "MANUAL", LocalDate.now().minusDays(365))));

        List<com.example.matching.entity.ability.PersonAbilityClaim> claims = agent.extractFromManual(100L);

        assertEquals(0, claims.get(0).getFreshnessScore().compareTo(BigDecimal.valueOf(80)));
        assertEquals(0, claims.get(1).getFreshnessScore().compareTo(BigDecimal.valueOf(60)));
        assertEquals(0, claims.get(2).getFreshnessScore().compareTo(BigDecimal.valueOf(40)));
    }

    // ==================== extractAll ====================

    @Test
    @DisplayName("extractAll：汇总各来源，且简历最新一条参与提取")
    void extractAll_aggregates() throws Exception {
        when(empAbilityMapper.selectList(any())).thenReturn(
                List.of(ability(5L, 3, "AI_PROJECT", LocalDate.now())),
                List.of(ability(5L, 3, "AI_TEST", LocalDate.now())),
                List.of(ability(5L, 3, "LEARNING_PROJECT", LocalDate.now())),
                List.of(ability(5L, 3, "MANUAL", LocalDate.now())));

        EmpResumeParse parse = new EmpResumeParse();
        parse.setId(77L);
        when(resumeParseMapper.selectList(any())).thenReturn(List.of(parse));

        EmpResumeParse detail = new EmpResumeParse();
        detail.setId(77L);
        detail.setAiAnalysisResult("{}");
        detail.setCreatedTime(LocalDateTime.now());
        when(resumeParseMapper.selectById(77L)).thenReturn(detail);
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(dtoClaim("Kafka", 3, "e")));
        when(claimNormalizer.normalize(anyString())).thenReturn(result);

        List<com.example.matching.entity.ability.PersonAbilityClaim> all = agent.extractAll(100L);

        // PMS(1) + AITest(1) + Learning(1) + Manual(1) + Resume(1)
        assertEquals(5, all.size());
    }

    @Test
    @DisplayName("extractAll：无简历解析记录 → 仅汇总其他来源")
    void extractAll_noResume() throws Exception {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of());
        when(resumeParseMapper.selectList(any())).thenReturn(List.of());

        assertTrue(agent.extractAll(100L).isEmpty());
    }
}
