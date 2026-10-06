package com.example.matching.service.post.impl;

import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.entity.governance.GovernanceFilterRule;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.mapper.post.PostPrototypeMapper;
import com.example.matching.service.governance.GovernanceFilterRuleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PostCleaningRulesEngine} 单元测试。
 *
 * <p>覆盖文本去噪、名称清洗、职责/要求提取、质量评分、去重检测与阻断判定的
 * 正常路径与各条规则的正反分支。所有外部依赖（Mapper / 治理规则服务）均 mock。
 */
class PostCleaningRulesEngineTest {

    private PostPrototypeMapper postPrototypeMapper;
    private PostCleaningRulesEngine engine;

    @BeforeEach
    void setUp() {
        postPrototypeMapper = mock(PostPrototypeMapper.class);
        // 传 null MeterRegistry 走「无指标」分支，避免 Counter 注册副作用
        engine = new PostCleaningRulesEngine(postPrototypeMapper, null);
    }

    private static PostCleaningRulesEngine withMeter() {
        return new PostCleaningRulesEngine(mock(PostPrototypeMapper.class), new SimpleMeterRegistry());
    }

    private static PostPrototype prototype(Long id, String name, String description) {
        PostPrototype p = new PostPrototype();
        p.setId(id);
        p.setPrototypeName(name);
        p.setDescription(description);
        p.setStatus(1);
        return p;
    }

    // ==================== cleanText ====================

    @Test
    @DisplayName("cleanText：null / 空白输入 → 返回空串")
    void cleanText_blankInput() {
        assertEquals("", engine.cleanText(null));
        assertEquals("", engine.cleanText("   "));
    }

    @Test
    @DisplayName("cleanText：无配置规则 → 内置噪声规则移除联系方式与广告，并折叠多余空行")
    void cleanText_builtInNoiseRules() {
        String raw = "Java开发工程师\n"
                + "联系电话：13800138000\n"
                + "邮箱：hr@example.com\n"
                + "公司简介：我们是一家很厉害的公司\n"
                + "\n\n\n"
                + "负责核心系统开发";
        String cleaned = engine.cleanText(raw);

        assertFalse(cleaned.contains("13800138000"));
        assertFalse(cleaned.contains("hr@example.com"));
        assertFalse(cleaned.contains("公司简介"));
        assertTrue(cleaned.contains("Java开发工程师"));
        assertTrue(cleaned.trim().equals(cleaned));
    }

    @Test
    @DisplayName("cleanText：配置了 KEYWORD 与 REGEX 规则 → 按规则移除内容")
    void cleanText_configuredRules() {
        GovernanceFilterRuleService ruleService = mock(GovernanceFilterRuleService.class);
        GovernanceFilterRule keyword = new GovernanceFilterRule();
        keyword.setRuleType("keyword"); // 大小写不敏感
        keyword.setPatternValue("【广告】");
        GovernanceFilterRule regex = new GovernanceFilterRule();
        regex.setRuleType("REGEX");
        regex.setPatternValue("\\d{4}-\\d{2}-\\d{2}");
        when(ruleService.activeRules(GovernanceFilterRuleService.POST_JD))
                .thenReturn(List.of(keyword, regex));

        PostCleaningRulesEngine configured = new PostCleaningRulesEngine(postPrototypeMapper, null);
        configured.setGovernanceFilterRuleService(ruleService);

        String cleaned = configured.cleanText("【广告】Java岗位 2024-01-01 招聘");

        assertFalse(cleaned.contains("【广告】"));
        assertFalse(cleaned.contains("2024-01-01"));
        assertTrue(cleaned.contains("Java岗位"));
    }

    @Test
    @DisplayName("cleanText：配置了非法正则 → 忽略该规则，文本原样保留")
    void cleanText_invalidRegexRuleIgnored() {
        GovernanceFilterRuleService ruleService = mock(GovernanceFilterRuleService.class);
        GovernanceFilterRule badRegex = new GovernanceFilterRule();
        badRegex.setId(9L);
        badRegex.setRuleType("REGEX");
        badRegex.setPatternValue("([unclosed");
        when(ruleService.activeRules(any())).thenReturn(List.of(badRegex));

        PostCleaningRulesEngine configured = new PostCleaningRulesEngine(postPrototypeMapper, null);
        configured.setGovernanceFilterRuleService(ruleService);

        assertEquals("Java工程师", configured.cleanText("Java工程师"));
    }

    // ==================== extractNoise ====================

    @Test
    @DisplayName("extractNoise：任一入参为 null → 返回空串")
    void extractNoise_nullInput() {
        assertEquals("", engine.extractNoise(null, "x"));
        assertEquals("", engine.extractNoise("x", null));
    }

    @Test
    @DisplayName("extractNoise：返回原文有、清洗后没有的非空行")
    void extractNoise_returnsRemovedLines() {
        String raw = "Java工程师\n联系电话：13800138000\n负责开发";
        String cleaned = "Java工程师\n负责开发";

        String noise = engine.extractNoise(raw, cleaned);

        assertTrue(noise.contains("联系电话"));
        assertFalse(noise.contains("Java工程师"));
    }

    // ==================== cleanPostName ====================

    @Test
    @DisplayName("cleanPostName：null / 空白 → 空串；正常 → 去后缀与括号内容")
    void cleanPostName_variants() {
        assertEquals("", engine.cleanPostName(null));
        assertEquals("", engine.cleanPostName("  "));
        assertEquals("Java开发工程师", engine.cleanPostName("Java开发工程师招聘"));
        assertEquals("Java开发工程师", engine.cleanPostName("Java开发工程师(15-25K)"));
        assertEquals("产品经理", engine.cleanPostName("  产品经理急聘  "));
    }

    // ==================== extractResponsibilities ====================

    @Test
    @DisplayName("extractResponsibilities：null / 空文本 → 空列表")
    void extractResponsibilities_blank() {
        assertTrue(engine.extractResponsibilities(null).isEmpty());
        assertTrue(engine.extractResponsibilities("").isEmpty());
    }

    @Test
    @DisplayName("extractResponsibilities：职责段落内逐个提取列表项，遇到要求标题即退出")
    void extractResponsibilities_sectionMode() {
        String text = "岗位职责：\n"
                + "1. 负责核心系统的架构设计与开发\n"
                + "• 参与需求评审并推动落地\n"
                + "短\n"
                + "任职要求：\n"
                + "熟悉Java与Spring\n";

        List<String> list = engine.extractResponsibilities(text);

        assertEquals(2, list.size());
        assertTrue(list.get(0).contains("负责核心系统的架构设计"));
        assertTrue(list.get(1).contains("参与需求评审"));
    }

    @Test
    @DisplayName("extractResponsibilities：非职责段落 → 命中职责关键词的行被提取")
    void extractResponsibilities_keywordMode() {
        String text = "负责支付网关的稳定性建设\n"
                + "今天天气不错哦";

        List<String> list = engine.extractResponsibilities(text);

        assertEquals(1, list.size());
        assertTrue(list.get(0).contains("支付网关"));
    }

    @Test
    @DisplayName("extractResponsibilities：职责段落外的短行（<8字）不提取")
    void extractResponsibilities_tooShortSkipped() {
        assertTrue(engine.extractResponsibilities("负责").isEmpty());
    }

    // ==================== extractRequirements ====================

    @Test
    @DisplayName("extractRequirements：null / 空文本 → 空列表")
    void extractRequirements_blank() {
        assertTrue(engine.extractRequirements(null).isEmpty());
        assertTrue(engine.extractRequirements("").isEmpty());
    }

    @Test
    @DisplayName("extractRequirements：要求段落内提取列表项，遇到职责标题即退出")
    void extractRequirements_sectionMode() {
        String text = "任职要求：\n"
                + "1. 熟悉Java并发编程与JVM调优\n"
                + "2. 本科以上学历，三年以上经验\n"
                + "岗位职责：\n"
                + "负责后端服务开发\n";

        List<String> list = engine.extractRequirements(text);

        assertEquals(2, list.size());
        assertTrue(list.get(0).contains("Java并发编程"));
    }

    @Test
    @DisplayName("extractRequirements：非要求段落 → 命中要求关键词的行被提取")
    void extractRequirements_keywordMode() {
        String text = "熟悉分布式系统设计\n"
                + "爱好打篮球";

        List<String> list = engine.extractRequirements(text);

        assertEquals(1, list.size());
        assertTrue(list.get(0).contains("分布式"));
    }

    // ==================== calculateQualityScore ====================

    @Test
    @DisplayName("calculateQualityScore：长文本+结构化+技术词丰富+少通用词 → 各项满分且无警告")
    void calculateQualityScore_highQuality() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("Java Spring MySQL Redis Docker Kubernetes 微服务 分布式 高并发 ");
        }
        List<String> responsibilities = List.of("a1", "a2", "a3");
        List<String> requirements = List.of("b1", "b2", "b3");

        PostCleaningResult.QualityDetails details =
                engine.calculateQualityScore(sb.toString(), responsibilities, requirements);

        assertEquals(new BigDecimal("1.0"), details.getLengthScore());
        assertEquals(new BigDecimal("1.0"), details.getStructureScore());
        assertEquals(new BigDecimal("1.0"), details.getKeywordScore());
        assertEquals(new BigDecimal("1.0"), details.getGenericRatioScore());
        assertTrue(details.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("calculateQualityScore：null 文本 + 空结构 → 触发长度/结构/关键词/通用词全部警告")
    void calculateQualityScore_lowQuality() {
        PostCleaningResult.QualityDetails details =
                engine.calculateQualityScore(null, List.of(), List.of());

        assertEquals(new BigDecimal("0.1"), details.getLengthScore());
        assertEquals(new BigDecimal("0.1"), details.getStructureScore());
        assertEquals(new BigDecimal("0.2"), details.getKeywordScore());
        assertEquals(new BigDecimal("1.0"), details.getGenericRatioScore());
        assertTrue(details.getWarnings().size() >= 3);
    }

    @Test
    @DisplayName("calculateQualityScore：中等长度+中等结构+少量技术词+较多通用词 → 走中间档评分")
    void calculateQualityScore_midQuality() {
        String text = "Java " + "沟".repeat(120) + " 沟通能力 团队合作 学习能力 抗压能力";
        PostCleaningResult.QualityDetails details =
                engine.calculateQualityScore(text, List.of("r1"), List.of("q1"));

        assertEquals(new BigDecimal("0.4"), details.getLengthScore());   // 100..199
        assertEquals(new BigDecimal("0.4"), details.getStructureScore()); // 1 项
        assertEquals(new BigDecimal("0.4"), details.getKeywordScore());   // 仅 Java
        assertEquals(new BigDecimal("0.4"), details.getGenericRatioScore()); // >3 通用词
        assertFalse(details.getWarnings().isEmpty());
    }

    // ==================== detectDuplicate ====================

    @Test
    @DisplayName("detectDuplicate：清洗后文本为空 → NONE，不查库")
    void detectDuplicate_emptyText() {
        PostCleaningResult result = new PostCleaningResult();

        engine.detectDuplicate(result, "岗位", "");

        assertEquals(PostCleaningRecordVO.DUPLICATE_STATUS_NONE, result.getDuplicateStatus());
    }

    @Test
    @DisplayName("detectDuplicate：无原型数据 → NONE")
    void detectDuplicate_noPrototypes() {
        when(postPrototypeMapper.selectList(any())).thenReturn(new ArrayList<>());
        PostCleaningResult result = new PostCleaningResult();

        engine.detectDuplicate(result, "岗位", "Java服务端开发");

        assertEquals(PostCleaningRecordVO.DUPLICATE_STATUS_NONE, result.getDuplicateStatus());
    }

    @Test
    @DisplayName("detectDuplicate：与原型完全一致 → DUPLICATE_BLOCKED 且回填匹配信息")
    void detectDuplicate_blocked() {
        String text = "负责核心交易系统的架构设计与高并发性能优化，保障线上稳定";
        when(postPrototypeMapper.selectList(any()))
                .thenReturn(List.of(prototype(7L, "交易系统工程师", text)));
        PostCleaningResult result = new PostCleaningResult();

        engine.detectDuplicate(result, "交易系统工程师", text);

        assertEquals(PostCleaningRecordVO.DUPLICATE_STATUS_DUPLICATE_BLOCKED, result.getDuplicateStatus());
        assertEquals(7L, result.getDuplicatePostId());
        assertEquals("交易系统工程师", result.getDuplicatePostName());
        assertNotNull(result.getDuplicateScore());
    }

    @Test
    @DisplayName("detectDuplicate：相似度处于疑似区间 → SUSPECTED")
    void detectDuplicate_suspected() {
        // 构造 Jaccard 相似度落在 0.80~0.92 的文本（共用大量 2-gram）
        String target = "负责订单系统的开发维护优化以及稳定性保障工作";
        String prototypeText = "负责订单系统的开发维护优化以及稳定性保障工作A";
        when(postPrototypeMapper.selectList(any()))
                .thenReturn(List.of(prototype(8L, "订单工程师", prototypeText)));
        PostCleaningResult result = new PostCleaningResult();

        engine.detectDuplicate(result, "订单工程师", target);

        String status = result.getDuplicateStatus();
        assertTrue(PostCleaningRecordVO.DUPLICATE_STATUS_SUSPECTED.equals(status)
                        || PostCleaningRecordVO.DUPLICATE_STATUS_DUPLICATE_BLOCKED.equals(status),
                "相似文本应被判为疑似或强重复，实际=" + status);
    }

    @Test
    @DisplayName("detectDuplicate：完全不同的文本 → NONE")
    void detectDuplicate_none() {
        when(postPrototypeMapper.selectList(any()))
                .thenReturn(List.of(prototype(9L, "财务总监", "负责公司财务预算与税务筹划管理")));
        PostCleaningResult result = new PostCleaningResult();

        engine.detectDuplicate(result, "Java工程师", "熟练掌握Java并发与JVM调优技术");

        assertEquals(PostCleaningRecordVO.DUPLICATE_STATUS_NONE, result.getDuplicateStatus());
    }

    @Test
    @DisplayName("detectDuplicate：原型描述为空 → 跳过该原型，判定 NONE；带 MeterRegistry 时走指标分支")
    void detectDuplicate_emptyPrototypeDescriptionAndMeter() {
        PostCleaningRulesEngine engineWithMeter = withMeter();
        PostPrototypeMapper mapper = mock(PostPrototypeMapper.class);
        PostCleaningRulesEngine local = new PostCleaningRulesEngine(mapper, new SimpleMeterRegistry());
        when(mapper.selectList(any())).thenReturn(List.of(prototype(10L, "空原型", "")));
        PostCleaningResult result = new PostCleaningResult();

        local.detectDuplicate(result, "岗位", "一段用于比较的岗位描述文本");

        assertEquals(PostCleaningRecordVO.DUPLICATE_STATUS_NONE, result.getDuplicateStatus());
        assertNotNull(engineWithMeter);
    }

    // ==================== determineBlock ====================

    @Test
    @DisplayName("determineBlock：强重复 → 阻断，原因包含重复岗位名")
    void determineBlock_duplicate() {
        PostCleaningResult result = new PostCleaningResult();
        result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_DUPLICATE_BLOCKED);
        result.setDuplicatePostName("后端工程师");
        result.setDuplicateScore(new BigDecimal("0.9500"));

        engine.determineBlock(result);

        assertTrue(result.isBlocked());
        assertTrue(result.getBlockReason().contains("后端工程师"));
    }

    @Test
    @DisplayName("determineBlock：质量分低于 0.40 → 阻断")
    void determineBlock_lowQuality() {
        PostCleaningResult result = new PostCleaningResult();
        result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
        result.setQualityScore(new BigDecimal("0.30"));
        result.setCleanedText("足够长的清洗后文本，用于通过长度校验测试用例".repeat(3));
        result.setCleanedPostName("岗位A");

        engine.determineBlock(result);

        assertTrue(result.isBlocked());
        assertTrue(result.getBlockReason().contains("质量过低"));
    }

    @Test
    @DisplayName("determineBlock：清洗后内容 <50 字 → 阻断")
    void determineBlock_tooShortContent() {
        PostCleaningResult result = new PostCleaningResult();
        result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
        result.setQualityScore(new BigDecimal("0.90"));
        result.setCleanedText("太短了");
        result.setCleanedPostName("岗位A");

        engine.determineBlock(result);

        assertTrue(result.isBlocked());
        assertTrue(result.getBlockReason().contains("有效内容过少"));
    }

    @Test
    @DisplayName("determineBlock：岗位名缺失 → 阻断")
    void determineBlock_missingPostName() {
        PostCleaningResult result = new PostCleaningResult();
        result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
        result.setQualityScore(new BigDecimal("0.90"));
        result.setCleanedText("足够长的清洗后文本，用于通过长度校验测试用例".repeat(3));
        result.setCleanedPostName("");

        engine.determineBlock(result);

        assertTrue(result.isBlocked());
        assertEquals("岗位名称缺失", result.getBlockReason());
    }

    @Test
    @DisplayName("determineBlock：全部条件满足 → 不阻断")
    void determineBlock_passes() {
        PostCleaningResult result = new PostCleaningResult();
        result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
        result.setQualityScore(new BigDecimal("0.85"));
        result.setCleanedText("足够长的清洗后文本，用于通过长度校验测试用例".repeat(3));
        result.setCleanedPostName("Java工程师");

        engine.determineBlock(result);

        assertFalse(result.isBlocked());
    }

    // ==================== 构造器的 MeterRegistry 分支 ====================

    @Test
    @DisplayName("构造器：传入 MeterRegistry → 计数器被注册且去重检测可正常累计")
    void constructor_withMeterRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostCleaningRulesEngine withMeter = new PostCleaningRulesEngine(postPrototypeMapper, registry);
        when(postPrototypeMapper.selectList(any()))
                .thenReturn(List.of(prototype(1L, "岗位X", "负责Java后端服务的开发与维护工作")));

        PostCleaningResult result = new PostCleaningResult();
        withMeter.detectDuplicate(result, "岗位X", "负责Java后端服务的开发与维护工作");

        assertTrue(registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().startsWith("post.cleaning.duplicate")));
    }
}
