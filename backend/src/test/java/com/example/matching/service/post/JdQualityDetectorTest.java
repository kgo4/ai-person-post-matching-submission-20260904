package com.example.matching.service.post;

import com.example.matching.dto.post.JdQualityReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JdQualityDetector} 单测。
 * <p>
 * 该类是纯逻辑（无外部依赖），覆盖三条检测链路：时效性、通胀、文本质量，
 * 以及抄袭检测的相似度计算与整体评分。
 */
class JdQualityDetectorTest {

    private final JdQualityDetector detector = new JdQualityDetector();

    // ==================== detectQuality：时效性 ====================

    @Test
    @DisplayName("引用过时技术（jQuery）→ 产生 TIMELINESS 警告并给出替代建议")
    void detectQuality_outdatedTech_producesTimelinessWarning() {
        JdQualityReport report = detector.detectQuality(
                "熟悉 jQuery 与 DOM 操作，负责前端页面开发，具备良好的编码习惯与文档撰写能力，能独立完成模块设计。",
                8, 3);

        assertNotNull(report.getTimelinessIssues());
        assertEquals(1, report.getTimelinessIssues().size());
        JdQualityReport.TimelinessIssue issue = report.getTimelinessIssues().get(0);
        assertEquals("jQuery", issue.getOutdatedTech());
        assertEquals("React/Vue/Angular", issue.getSuggestedReplacement());
        assertEquals("WARNING", issue.getSeverity());

        assertTrue(report.getWarnings().stream()
                .anyMatch(w -> "TIMELINESS".equals(w.getType())));
        assertTrue(report.getHasIssues());
    }

    @Test
    @DisplayName("过时技术大小写不敏感 → 小写 jquery 也能识别")
    void detectQuality_outdatedTechCaseInsensitive() {
        JdQualityReport report = detector.detectQuality("项目基于 jquery 与 struts 搭建。", 8, 3);

        assertEquals(2, report.getTimelinessIssues().size());
    }

    @Test
    @DisplayName("引用多项过时技术 → 逐条产生时效性问题")
    void detectQuality_multipleOutdatedTech() {
        JdQualityReport report = detector.detectQuality(
                "技术栈：Java 8 + SVN + MySQL 5.6 + Ant 构建。", 8, 3);

        List<String> techs = report.getTimelinessIssues().stream()
                .map(JdQualityReport.TimelinessIssue::getOutdatedTech)
                .toList();
        assertTrue(techs.contains("Java 8"));
        assertTrue(techs.contains("SVN"));
        assertTrue(techs.contains("MySQL 5.6"));
        assertTrue(techs.contains("Ant"));
    }

    @Test
    @DisplayName("jdText 为 null → 跳过时效性与文本质量检测，不抛异常")
    void detectQuality_nullText_skipsTextChecks() {
        JdQualityReport report = detector.detectQuality(null, 8, 3);

        assertNull(report.getTimelinessIssues());
        assertTrue(report.getWarnings().isEmpty());
        assertEquals(100, report.getOverallScore());
        assertFalse(report.getHasIssues());
    }

    @Test
    @DisplayName("jdText 为空白串 → 同样跳过文本类检测")
    void detectQuality_blankText_skipsTextChecks() {
        JdQualityReport report = detector.detectQuality("   ", 8, 3);

        assertNull(report.getTimelinessIssues());
        assertTrue(report.getWarnings().isEmpty());
        assertFalse(report.getHasIssues());
    }

    // ==================== detectQuality：通胀 ====================

    @Test
    @DisplayName("能力数量超过上限（>15）→ 产生 INFLATION 警告")
    void detectQuality_abilityCountTooHigh_producesInflationWarning() {
        JdQualityReport report = detector.detectQuality(
                "负责后端服务的设计、开发、测试与维护，参与需求评审、技术方案评审、代码评审，推动架构演进与性能优化，"
                        + "具备扎实的 Java 基础、并发编程经验与分布式系统设计能力，熟悉常用中间件与数据库调优。",
                20, 3);

        assertTrue(report.getWarnings().stream()
                .anyMatch(w -> "INFLATION".equals(w.getType()) && w.getMessage().contains("能力要求数量")));
    }

    @Test
    @DisplayName("最高等级超过正常（>4）→ 产生专家级通胀警告")
    void detectQuality_maxLevelTooHigh_producesInflationWarning() {
        JdQualityReport report = detector.detectQuality(
                "负责后端服务的设计、开发、测试与维护，参与需求评审、技术方案评审、代码评审，推动架构演进与性能优化，"
                        + "具备扎实的 Java 基础、并发编程经验与分布式系统设计能力，熟悉常用中间件与数据库调优。",
                8, 5);

        assertTrue(report.getWarnings().stream()
                .anyMatch(w -> "INFLATION".equals(w.getType()) && w.getMessage().contains("级专家要求")));
    }

    @Test
    @DisplayName("能力数量与等级均在正常范围 → 不产生通胀警告")
    void detectQuality_normalRanges_noInflationWarning() {
        JdQualityReport report = detector.detectQuality(
                "负责后端服务的设计、开发、测试与维护，参与需求评审、技术方案评审、代码评审，推动架构演进与性能优化，"
                        + "具备扎实的 Java 基础、并发编程经验与分布式系统设计能力，熟悉常用中间件与数据库调优。",
                10, 4);

        assertFalse(report.getWarnings().stream()
                .anyMatch(w -> "INFLATION".equals(w.getType())));
    }

    @Test
    @DisplayName("边界值：能力数量恰好 15、等级恰好 4 → 不触发通胀")
    void detectQuality_boundaryValues_notInflation() {
        JdQualityReport report = detector.detectQuality(
                "负责后端服务的设计、开发、测试与维护，参与需求评审、技术方案评审、代码评审，推动架构演进与性能优化，"
                        + "具备扎实的 Java 基础、并发编程经验与分布式系统设计能力，熟悉常用中间件与数据库调优。",
                15, 4);

        assertFalse(report.getWarnings().stream()
                .anyMatch(w -> "INFLATION".equals(w.getType())));
    }

    @Test
    @DisplayName("边界值：能力数量 16、等级 5 → 两条通胀警告同时产生")
    void detectQuality_bothInflationWarnings() {
        JdQualityReport report = detector.detectQuality(
                "负责后端服务的设计、开发、测试与维护，参与需求评审、技术方案评审、代码评审，推动架构演进与性能优化，"
                        + "具备扎实的 Java 基础、并发编程经验与分布式系统设计能力，熟悉常用中间件与数据库调优。",
                16, 5);

        long inflationCount = report.getWarnings().stream()
                .filter(w -> "INFLATION".equals(w.getType()))
                .count();
        assertEquals(2, inflationCount);
    }

    // ==================== detectQuality：文本质量 ====================

    @Test
    @DisplayName("JD 文本过短（<100 字）→ 产生 INFO 级质量警告")
    void detectQuality_shortText_producesInfoWarning() {
        JdQualityReport report = detector.detectQuality("招后端。", 5, 2);

        assertTrue(report.getWarnings().stream()
                .anyMatch(w -> "QUALITY".equals(w.getType()) && w.getMessage().contains("JD文本过短")));
    }

    @Test
    @DisplayName("包含 3 个及以上万金油标签 → 产生通用描述过多的质量警告")
    void detectQuality_manyGenericTerms_producesInfoWarning() {
        String text = "岗位要求：具备良好的沟通能力、团队合作精神、较强的学习能力，以及抗压能力。"
                + "日常工作需要与产品、测试、运维多方协作，参与需求评审与技术方案设计，"
                + "推动系统架构持续演进，保障线上服务稳定可靠运行。";
        JdQualityReport report = detector.detectQuality(text, 8, 3);

        assertTrue(report.getWarnings().stream()
                .anyMatch(w -> "QUALITY".equals(w.getType()) && w.getMessage().contains("通用描述")));
    }

    @Test
    @DisplayName("万金油标签不足 3 个 → 不产生通用描述警告")
    void detectQuality_fewGenericTerms_noWarning() {
        String text = "岗位要求：具备良好的沟通能力与团队合作精神。"
                + "日常工作需要与产品、测试、运维多方协作，参与需求评审与技术方案设计，"
                + "推动系统架构持续演进，保障线上服务稳定可靠运行，负责核心链路性能调优。";
        JdQualityReport report = detector.detectQuality(text, 8, 3);

        assertFalse(report.getWarnings().stream()
                .anyMatch(w -> "QUALITY".equals(w.getType()) && w.getMessage().contains("通用描述")));
    }

    @Test
    @DisplayName("文本长度恰好 100 字 → 不触发过短警告")
    void detectQuality_exactlyHundredChars_noShortWarning() {
        String text = "a".repeat(100);
        JdQualityReport report = detector.detectQuality(text, 8, 3);

        assertFalse(report.getWarnings().stream()
                .anyMatch(w -> w.getMessage() != null && w.getMessage().contains("JD文本过短")));
    }

    @Test
    @DisplayName("完全合规的 JD → 无任何警告、满分、hasIssues=false")
    void detectQuality_cleanJd_noIssues() {
        // 注意：必须 ≥100 字，否则会触发"文本过短"的 INFO 警告
        String text = "负责后端核心服务的设计与开发，参与需求评审和技术方案评审，推动架构演进与性能优化。"
                + "要求熟悉 Java 17、Spring Boot、MySQL 8.0 与 Redis，具备分布式系统设计经验，"
                + "能够独立完成模块设计、编码、测试与上线，并持续关注线上服务稳定性与链路性能。";
        JdQualityReport report = detector.detectQuality(text, 8, 3);

        assertTrue(report.getWarnings().isEmpty());
        assertEquals(100, report.getOverallScore());
        assertFalse(report.getHasIssues());
    }

    // ==================== detectQuality：评分 ====================

    @Test
    @DisplayName("评分随警告级别递减：WARNING 扣 15、INFO 扣 5")
    void detectQuality_scoreDeduction() {
        // 一条 INFLATION(WARNING) + 一条过短(INFO) = 100 - 15 - 5 = 80
        JdQualityReport report = detector.detectQuality("招后端。", 20, 2);

        assertEquals(80, report.getOverallScore());
    }

    @Test
    @DisplayName("警告过多时评分下限为 0，不出现负分")
    void detectQuality_scoreFloorAtZero() {
        // 大量过时技术（每条 WARNING 扣 15）足以把分数打到 0 以下
        String text = "技术栈：jQuery、Struts、EJB、JSP、Servlet、JDBC Template、Ant、SVN、Flash、IE6、"
                + "IE8、Windows XP、Java 6、Java 7、Java 8、Python 2、AngularJS、Vue 1、Vue 2、React 15、"
                + "MySQL 5.6、MySQL 5.5。";
        JdQualityReport report = detector.detectQuality(text, 20, 5);

        assertEquals(0, report.getOverallScore());
    }

    // ==================== detectPlagiarism ====================

    @Test
    @DisplayName("文本列表为 null → 返回空列表，不抛异常")
    void detectPlagiarism_nullList_returnsEmpty() {
        assertTrue(detector.detectPlagiarism(null).isEmpty());
    }

    @Test
    @DisplayName("文本列表少于 2 条 → 无可比对象，返回空列表")
    void detectPlagiarism_lessThanTwo_returnsEmpty() {
        assertTrue(detector.detectPlagiarism(List.of()).isEmpty());
        assertTrue(detector.detectPlagiarism(List.of("只有一个岗位描述")).isEmpty());
    }

    @Test
    @DisplayName("两段完全相同的 JD → 判定为高度相似（疑似抄袭）")
    void detectPlagiarism_identicalTexts_flaggedSuspicious() {
        String jd = "负责后端服务的设计与开发，熟悉 Java 与 Spring Boot，具备分布式系统经验，"
                + "参与需求评审与技术方案设计，推动架构演进与性能优化。";

        List<JdQualityReport.SimilarityPair> pairs = detector.detectPlagiarism(List.of(jd, jd));

        assertEquals(1, pairs.size());
        JdQualityReport.SimilarityPair pair = pairs.get(0);
        assertEquals(0, pair.getIndex1());
        assertEquals(1, pair.getIndex2());
        assertTrue(pair.getIsSuspicious());
        assertEquals(1.0, pair.getSimilarity(), 1e-9);
        assertTrue(pair.getMessage().contains("可能存在抄袭"));
    }

    @Test
    @DisplayName("两段完全不同的 JD → 相似度低，不产生可疑对")
    void detectPlagiarism_differentTexts_noPair() {
        List<JdQualityReport.SimilarityPair> pairs = detector.detectPlagiarism(List.of(
                "负责 Java 后端服务开发，熟悉 Spring Boot 与 MySQL。",
                "负责市场营销活动策划与品牌推广，具备数据分析与文案撰写能力。"));

        assertTrue(pairs.isEmpty());
    }

    @Test
    @DisplayName("含 null 文本 → 相似度按 0 处理，不抛异常")
    void detectPlagiarism_nullElement_handledAsZero() {
        assertDoesNotThrow(() -> detector.detectPlagiarism(
                java.util.Arrays.asList(null, "某段岗位描述", null)));
    }

    @Test
    @DisplayName("三份相同 JD → 三组两两组合全部标记为可疑")
    void detectPlagiarism_threeIdenticalTexts_threePairs() {
        String jd = "负责后端服务的设计与开发，熟悉 Java 与 Spring Boot，具备分布式系统经验，"
                + "参与需求评审与技术方案设计，推动架构演进与性能优化。";

        List<JdQualityReport.SimilarityPair> pairs = detector.detectPlagiarism(List.of(jd, jd, jd));

        assertEquals(3, pairs.size());
        assertTrue(pairs.stream().allMatch(JdQualityReport.SimilarityPair::getIsSuspicious));
    }

    @Test
    @DisplayName("空白文本 → 清洗后无 2-gram，相似度为 0 不产生可疑对")
    void detectPlagiarism_blankTexts_noPair() {
        List<JdQualityReport.SimilarityPair> pairs =
                detector.detectPlagiarism(List.of("     ", "   \t  "));

        assertTrue(pairs.isEmpty());
    }
}
