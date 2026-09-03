package com.example.matching.service.post;

import com.example.matching.dto.post.JdQualityReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JD质量检测器
 * <p>
 * 检测JD（岗位描述）中的"时滞"与"噪音"问题：
 * 1. 抄袭检测：计算JD之间的文本相似度，标记高度相似的JD
 * 2. 通胀检测：统计同类岗位的能力要求数量和等级分布，标记异常
 * 3. 时效性检测：基于JD中引用的技术版本判断是否过时
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdQualityDetector {

    // 过时技术关键词及其替代品
    private static final Map<String, String> OUTDATED_TECH = Map.ofEntries(
            Map.entry("jQuery", "React/Vue/Angular"),
            Map.entry("Struts", "Spring MVC/Spring Boot"),
            Map.entry("EJB", "Spring/Spring Boot"),
            Map.entry("JSP", "Thymeleaf/前后端分离"),
            Map.entry("Servlet", "Spring MVC"),
            Map.entry("JDBC Template", "MyBatis/JPA"),
            Map.entry("Ant", "Maven/Gradle"),
            Map.entry("SVN", "Git"),
            Map.entry("Flash", "HTML5/CSS3"),
            Map.entry("IE6", "现代浏览器"),
            Map.entry("IE8", "现代浏览器"),
            Map.entry("Windows XP", "Windows 10/11"),
            Map.entry("Java 6", "Java 17/21"),
            Map.entry("Java 7", "Java 17/21"),
            Map.entry("Java 8", "Java 17/21"),
            Map.entry("Python 2", "Python 3"),
            Map.entry("AngularJS", "Angular 2+"),
            Map.entry("Vue 1", "Vue 3"),
            Map.entry("Vue 2", "Vue 3"),
            Map.entry("React 15", "React 18+"),
            Map.entry("MySQL 5.6", "MySQL 8.0"),
            Map.entry("MySQL 5.5", "MySQL 8.0")
    );

    // 能力标签正常范围（同类岗位的典型能力数量）
    private static final int NORMAL_ABILITY_COUNT_MIN = 5;
    private static final int NORMAL_ABILITY_COUNT_MAX = 15;
    private static final int NORMAL_MAX_LEVEL = 4; // 通常不要求5级专家

    /**
     * 检测JD质量
     *
     * @param jdText      JD文本
     * @param abilityCount 能力要求数量
     * @param maxLevel    最高等级要求
     * @return 质量报告
     */
    public JdQualityReport detectQuality(String jdText, int abilityCount, int maxLevel) {
        JdQualityReport report = new JdQualityReport();
        List<JdQualityReport.QualityWarning> warnings = new ArrayList<>();

        // 1. 时效性检测
        List<JdQualityReport.TimelinessIssue> timelinessIssues = checkTimeliness(jdText);
        if (!timelinessIssues.isEmpty()) {
            report.setTimelinessIssues(timelinessIssues);
            for (JdQualityReport.TimelinessIssue issue : timelinessIssues) {
                JdQualityReport.QualityWarning warning = new JdQualityReport.QualityWarning();
                warning.setType("TIMELINESS");
                warning.setLevel(issue.getSeverity());
                warning.setMessage("引用过时技术：" + issue.getOutdatedTech() + "，建议替换为：" + issue.getSuggestedReplacement());
                warnings.add(warning);
            }
        }

        // 2. 通胀检测
        if (abilityCount > NORMAL_ABILITY_COUNT_MAX) {
            JdQualityReport.QualityWarning warning = new JdQualityReport.QualityWarning();
            warning.setType("INFLATION");
            warning.setLevel("WARNING");
            warning.setMessage("能力要求数量（" + abilityCount + "项）超出正常范围（" + NORMAL_ABILITY_COUNT_MIN + "-" + NORMAL_ABILITY_COUNT_MAX + "项），可能存在要求通胀");
            warnings.add(warning);
        }

        if (maxLevel > NORMAL_MAX_LEVEL) {
            JdQualityReport.QualityWarning warning = new JdQualityReport.QualityWarning();
            warning.setType("INFLATION");
            warning.setLevel("WARNING");
            warning.setMessage("存在" + maxLevel + "级专家要求，通常岗位最高要求为" + NORMAL_MAX_LEVEL + "级精通，请确认是否合理");
            warnings.add(warning);
        }

        // 3. 文本质量检测
        if (jdText != null && !jdText.isBlank()) {
            // 检测是否过于简短
            if (jdText.length() < 100) {
                JdQualityReport.QualityWarning warning = new JdQualityReport.QualityWarning();
                warning.setType("QUALITY");
                warning.setLevel("INFO");
                warning.setMessage("JD文本过短（" + jdText.length() + "字），可能信息不足");
                warnings.add(warning);
            }

            // 检测是否包含万金油标签
            List<String> genericTerms = List.of("沟通能力", "团队合作", "学习能力", "抗压能力", "责任心强", "积极主动");
            List<String> foundGeneric = genericTerms.stream()
                    .filter(term -> jdText.contains(term))
                    .collect(Collectors.toList());
            if (foundGeneric.size() >= 3) {
                JdQualityReport.QualityWarning warning = new JdQualityReport.QualityWarning();
                warning.setType("QUALITY");
                warning.setLevel("INFO");
                warning.setMessage("JD包含较多通用描述（" + String.join("、", foundGeneric) + "），建议增加具体技术要求");
                warnings.add(warning);
            }
        }

        report.setWarnings(warnings);
        report.setOverallScore(calculateOverallScore(warnings));
        report.setHasIssues(!warnings.isEmpty());

        return report;
    }

    /**
     * 检测JD之间的抄袭/相似
     *
     * @param jdTexts JD文本列表
     * @return 相似度矩阵（上三角）
     */
    public List<JdQualityReport.SimilarityPair> detectPlagiarism(List<String> jdTexts) {
        List<JdQualityReport.SimilarityPair> pairs = new ArrayList<>();

        if (jdTexts == null || jdTexts.size() < 2) {
            return pairs;
        }

        for (int i = 0; i < jdTexts.size(); i++) {
            for (int j = i + 1; j < jdTexts.size(); j++) {
                double similarity = calculateTextSimilarity(jdTexts.get(i), jdTexts.get(j));
                if (similarity > 0.8) { // 相似度超过80%
                    JdQualityReport.SimilarityPair pair = new JdQualityReport.SimilarityPair();
                    pair.setIndex1(i);
                    pair.setIndex2(j);
                    pair.setSimilarity(similarity);
                    pair.setIsSuspicious(true);
                    pair.setMessage("两个JD相似度为" + String.format("%.1f%%", similarity * 100) + "，可能存在抄袭");
                    pairs.add(pair);
                }
            }
        }

        return pairs;
    }

    /**
     * 检测时效性问题
     */
    private List<JdQualityReport.TimelinessIssue> checkTimeliness(String jdText) {
        List<JdQualityReport.TimelinessIssue> issues = new ArrayList<>();

        if (jdText == null || jdText.isBlank()) {
            return issues;
        }

        String lowerText = jdText.toLowerCase();

        for (Map.Entry<String, String> entry : OUTDATED_TECH.entrySet()) {
            String outdated = entry.getKey();
            String replacement = entry.getValue();

            // 不区分大小写检查
            if (lowerText.contains(outdated.toLowerCase())) {
                JdQualityReport.TimelinessIssue issue = new JdQualityReport.TimelinessIssue();
                issue.setOutdatedTech(outdated);
                issue.setSuggestedReplacement(replacement);
                issue.setSeverity("WARNING");
                issue.setMessage("引用了过时技术「" + outdated + "」，建议更新为「" + replacement + "」");
                issues.add(issue);
            }
        }

        return issues;
    }

    /**
     * 计算文本相似度（简化版：基于字符级Jaccard相似度）
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0;
        }

        // 提取2-gram字符集合
        Set<String> ngrams1 = extractNgrams(text1, 2);
        Set<String> ngrams2 = extractNgrams(text2, 2);

        if (ngrams1.isEmpty() || ngrams2.isEmpty()) {
            return 0;
        }

        // Jaccard相似度
        Set<String> intersection = new HashSet<>(ngrams1);
        intersection.retainAll(ngrams2);

        Set<String> union = new HashSet<>(ngrams1);
        union.addAll(ngrams2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 提取n-gram字符集合
     */
    private Set<String> extractNgrams(String text, int n) {
        Set<String> ngrams = new HashSet<>();
        String cleaned = text.replaceAll("\\s+", ""); // 去除空白
        for (int i = 0; i <= cleaned.length() - n; i++) {
            ngrams.add(cleaned.substring(i, i + n));
        }
        return ngrams;
    }

    /**
     * 计算整体质量评分
     */
    private int calculateOverallScore(List<JdQualityReport.QualityWarning> warnings) {
        int score = 100;
        for (JdQualityReport.QualityWarning warning : warnings) {
            switch (warning.getLevel()) {
                case "ERROR":
                    score -= 30;
                    break;
                case "WARNING":
                    score -= 15;
                    break;
                case "INFO":
                    score -= 5;
                    break;
            }
        }
        return Math.max(0, score);
    }
}
