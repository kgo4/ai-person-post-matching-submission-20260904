package com.example.matching.service.system.impl;

import com.example.matching.service.system.AbilityTagNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 能力标签名称规范化实现
 * <p>
 * 将 AI 提取的原始能力名称规范化为标准标签名称，
 * 避免因表达差异导致标签爆炸。
 *
 * @author system
 */
@Slf4j
@Component
public class AbilityTagNormalizerImpl implements AbilityTagNormalizer {

    /**
     * 程度词列表（需要去掉）
     */
    private static final List<String> DEGREE_WORDS = List.of(
            "熟练", "精通", "掌握", "了解", "具备", "熟悉", "深入", "扎实",
            "良好", "丰富", "较强", "优秀", "一定", "基本", "初步",
            "熟练掌握", "深入理解", "熟悉掌握", "熟练使用", "熟练运用",
            "精通掌握", "具备良好的", "具有丰富的"
    );

    /**
     * 泛化尾词列表（需要去掉）
     */
    private static final List<String> GENERIC_SUFFIXES = List.of(
            "能力", "经验", "技能", "项目经验", "开发能力", "开发经验",
            "技术能力", "技术经验", "实战经验", "实战能力", "应用能力",
            "应用经验", "使用经验", "使用能力", "编程能力", "编程经验",
            "设计能力", "设计经验", "架构能力", "架构经验", "调试能力",
            "调试经验", "优化能力", "优化经验", "运维能力", "运维经验",
            "测试能力", "测试经验", "管理能力", "管理经验", "沟通能力",
            "沟通经验", "协作能力", "协作经验", "学习能力", "学习经验",
            "分析能力", "分析经验", "解决问题能力", "解决问题经验"
    );

    /**
     * 动作词列表（需要去掉）
     */
    private static final List<String> ACTION_WORDS = List.of(
            "负责", "参与", "完成", "主导", "协助", "实现", "开发", "搭建",
            "设计", "维护", "优化", "部署", "测试", "调试", "管理", "带领",
            "推动", "建设", "构建", "研发", "编写", "编写代码", "编码",
            "能够", "可以", "会", "善于", "擅长"
    );

    /**
     * 句子型特征词（包含这些词的标签可能是句子）
     */
    private static final List<String> SENTENCE_INDICATORS = List.of(
            "能够", "负责", "参与", "完成", "实现", "具备", "具有",
            "熟悉", "精通", "掌握", "了解", "熟练", "善于", "擅长",
            "主导", "协助", "带领", "推动", "并且", "以及", "同时",
            "通过", "利用", "使用", "基于", "围绕", "针对"
    );

    /**
     * 低质量模式黑名单
     */
    private static final List<Pattern> LOW_QUALITY_PATTERNS = List.of(
            Pattern.compile("^沟通.{0,2}能力.{0,2}$"),
            Pattern.compile("^学习.{0,2}能力.{0,2}$"),
            Pattern.compile("^团队.{0,2}合作.{0,2}$"),
            Pattern.compile("^工作.{0,2}认真.{0,2}$"),
            Pattern.compile("^项目.{0,2}经验.{0,2}丰富.{0,2}$"),
            Pattern.compile("^熟练掌握$"),
            Pattern.compile("^技术能力$"),
            Pattern.compile("^开发能力$"),
            Pattern.compile("^开发经验$"),
            Pattern.compile("^相关经验$"),
            Pattern.compile("^编程能力$"),
            Pattern.compile("^解决问题能力$"),
            Pattern.compile("^分析能力$"),
            Pattern.compile("^沟通能力$"),
            Pattern.compile("^学习能力$"),
            Pattern.compile("^领导能力$"),
            Pattern.compile("^管理能力$"),
            Pattern.compile("^执行能力$"),
            Pattern.compile("^创新能力$"),
            Pattern.compile("^抗压能力$"),
            Pattern.compile("^综合素质$"),
            Pattern.compile("^业务能力$"),
            Pattern.compile("^专业能力$"),
            Pattern.compile("^基本能力$"),
            Pattern.compile("^通用能力$"),
            Pattern.compile("^基础能力$"),
            Pattern.compile("^核心能力$"),
            Pattern.compile("^综合能力$"),
            Pattern.compile("^.{0,2}能力强$"),
            Pattern.compile("^.{0,2}能力好$"),
            Pattern.compile("^.{0,2}能力优秀$"),
            Pattern.compile("^.{0,2}经验丰富$"),
            Pattern.compile("^.{0,2}经验充足$")
    );

    /**
     * 常见技术栈别名映射（用于统一大小写和格式）
     */
    private static final Map<String, String> TECH_ALIASES = Map.ofEntries(
            // 前端框架
            Map.entry("vue.js", "Vue"),
            Map.entry("vue3", "Vue"),
            Map.entry("vue2", "Vue"),
            Map.entry("vuejs", "Vue"),
            Map.entry("react.js", "React"),
            Map.entry("reactjs", "React"),
            Map.entry("angular.js", "Angular"),
            Map.entry("angularjs", "Angular"),
            Map.entry("next.js", "Next.js"),
            Map.entry("nextjs", "Next.js"),
            Map.entry("nuxt.js", "Nuxt.js"),
            Map.entry("nuxtjs", "Nuxt.js"),
            Map.entry("node.js", "Node.js"),
            Map.entry("nodejs", "Node.js"),

            // 后端框架
            Map.entry("springboot", "Spring Boot"),
            Map.entry("spring boot", "Spring Boot"),
            Map.entry("spring-boot", "Spring Boot"),
            Map.entry("springmvc", "Spring MVC"),
            Map.entry("spring mvc", "Spring MVC"),
            Map.entry("springcloud", "Spring Cloud"),
            Map.entry("spring cloud", "Spring Cloud"),
            Map.entry("mybatis-plus", "MyBatis Plus"),
            Map.entry("mybatisplus", "MyBatis Plus"),
            Map.entry("mybatis plus", "MyBatis Plus"),
            Map.entry("django", "Django"),
            Map.entry("flask", "Flask"),
            Map.entry("fastapi", "FastAPI"),
            Map.entry("fast api", "FastAPI"),
            Map.entry("express.js", "Express"),
            Map.entry("expressjs", "Express"),
            Map.entry("nestjs", "NestJS"),
            Map.entry("nest.js", "NestJS"),

            // 编程语言
            Map.entry("javascript", "JavaScript"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("python", "Python"),
            Map.entry("java", "Java"),
            Map.entry("golang", "Go"),
            Map.entry("c++", "C++"),
            Map.entry("c#", "C#"),
            Map.entry(".net", ".NET"),
            Map.entry("dotnet", ".NET"),
            Map.entry("php", "PHP"),
            Map.entry("ruby", "Ruby"),
            Map.entry("rust", "Rust"),
            Map.entry("kotlin", "Kotlin"),
            Map.entry("scala", "Scala"),
            Map.entry("swift", "Swift"),
            Map.entry("objective-c", "Objective-C"),
            Map.entry("objective c", "Objective-C"),

            // 数据库
            Map.entry("mysql", "MySQL"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("postgres", "PostgreSQL"),
            Map.entry("mongodb", "MongoDB"),
            Map.entry("mongo", "MongoDB"),
            Map.entry("redis", "Redis"),
            Map.entry("elasticsearch", "Elasticsearch"),
            Map.entry("elastic search", "Elasticsearch"),
            Map.entry("es", "Elasticsearch"),

            // 容器和云
            Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("k8s", "Kubernetes"),
            Map.entry("aws", "AWS"),
            Map.entry("azure", "Azure"),
            Map.entry("gcp", "GCP"),
            Map.entry("aliyun", "阿里云"),
            Map.entry("阿里云", "阿里云"),

            // 大数据和AI
            Map.entry("hadoop", "Hadoop"),
            Map.entry("spark", "Spark"),
            Map.entry("flink", "Flink"),
            Map.entry("kafka", "Kafka"),
            Map.entry("tensorflow", "TensorFlow"),
            Map.entry("pytorch", "PyTorch"),
            Map.entry("llm", "LLM"),
            Map.entry("大模型", "LLM"),
            Map.entry("大语言模型", "LLM"),
            Map.entry("rag", "RAG"),
            Map.entry("检索增强生成", "RAG"),
            Map.entry("ai", "AI"),
            Map.entry("人工智能", "AI"),
            Map.entry("机器学习", "机器学习"),
            Map.entry("深度学习", "深度学习"),
            Map.entry("ml", "机器学习"),
            Map.entry("dl", "深度学习"),

            // 微服务相关
            Map.entry("微服务", "微服务架构"),
            Map.entry("微服务开发", "微服务架构"),
            Map.entry("微服务项目", "微服务架构"),
            Map.entry("微服务经验", "微服务架构"),
            Map.entry("microservice", "微服务架构"),
            Map.entry("microservices", "微服务架构"),
            Map.entry("micro service", "微服务架构")
    );

    @Override
    public String normalize(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            return rawName;
        }

        String normalized = rawName.trim();

        // 1. 去掉前后空白和多余空格
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // 2. 去掉无意义标点（中文和英文标点）
        normalized = normalized.replaceAll("[[\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF\\u2018-\\u201F\\u2026\\u2013-\\u2014]&&[^+.#]]", "");

        // 3. 去掉程度词（从长到短匹配，避免部分匹配）
        List<String> sortedDegreeWords = new ArrayList<>(DEGREE_WORDS);
        sortedDegreeWords.sort((a, b) -> b.length() - a.length());
        for (String word : sortedDegreeWords) {
            if (normalized.startsWith(word)) {
                normalized = normalized.substring(word.length()).trim();
                break;
            }
        }

        // 4. 去掉动作词前缀
        for (String word : ACTION_WORDS) {
            if (normalized.startsWith(word)) {
                String remaining = normalized.substring(word.length()).trim();
                if (remaining.length() >= 2) {
                    normalized = remaining;
                    break;
                }
            }
        }

        // 5. 去掉泛化尾词
        List<String> sortedSuffixes = new ArrayList<>(GENERIC_SUFFIXES);
        sortedSuffixes.sort((a, b) -> b.length() - a.length());
        for (String suffix : sortedSuffixes) {
            if (normalized.endsWith(suffix)) {
                String candidate = normalized.substring(0, normalized.length() - suffix.length()).trim();
                if (candidate.length() >= 2) {
                    normalized = candidate;
                    break;
                }
            }
        }

        // 6. 再次清理空白
        normalized = normalized.replaceAll("\\s+", " ").trim();

        // 7. 统一技术栈别名（大小写不敏感匹配）
        String lowerNormalized = normalized.toLowerCase();
        if (TECH_ALIASES.containsKey(lowerNormalized)) {
            normalized = TECH_ALIASES.get(lowerNormalized);
        }

        // 8. 处理常见模式："XXX开发" -> "XXX"
        if (normalized.endsWith("开发") && normalized.length() > 4) {
            String candidate = normalized.substring(0, normalized.length() - 2).trim();
            if (candidate.length() >= 2) {
                normalized = candidate;
            }
        }

        // 9. 处理"XXX技术" -> "XXX"
        if (normalized.endsWith("技术") && normalized.length() > 4) {
            String candidate = normalized.substring(0, normalized.length() - 2).trim();
            if (candidate.length() >= 2) {
                normalized = candidate;
            }
        }

        // 10. 处理"XXX框架" -> "XXX"（对于已知框架）
        if (normalized.endsWith("框架") && normalized.length() > 4) {
            String candidate = normalized.substring(0, normalized.length() - 2).trim();
            String lowerCandidate = candidate.toLowerCase();
            if (TECH_ALIASES.containsKey(lowerCandidate)) {
                normalized = TECH_ALIASES.get(lowerCandidate);
            }
        }

        // 11. 最终清理
        normalized = normalized.trim();

        // 如果规范化后为空，返回原始名称
        if (!StringUtils.hasText(normalized)) {
            return rawName.trim();
        }

        log.debug("标签规范化: '{}' -> '{}'", rawName, normalized);
        return normalized;
    }

    @Override
    public boolean isLowQualityName(String normalizedName) {
        if (!StringUtils.hasText(normalizedName)) {
            return true;
        }

        String name = normalizedName.trim();

        // 检查长度
        if (name.length() < 2 || name.length() > 30) {
            return true;
        }

        // 检查低质量模式
        for (Pattern pattern : LOW_QUALITY_PATTERNS) {
            if (pattern.matcher(name).matches()) {
                log.debug("低质量标签匹配模式: '{}'", name);
                return true;
            }
        }

        // 检查是否是纯数字
        if (name.matches("^\\d+$")) {
            return true;
        }

        // 检查是否包含过多数字
        long digitCount = name.chars().filter(Character::isDigit).count();
        if (digitCount > name.length() * 0.5) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isSentenceLike(String normalizedName) {
        if (!StringUtils.hasText(normalizedName)) {
            return false;
        }

        String name = normalizedName.trim();

        // 长度超过20个字符，可能是句子
        if (name.length() > 20) {
            return true;
        }

        // 包含多个句子特征词
        int indicatorCount = 0;
        for (String indicator : SENTENCE_INDICATORS) {
            if (name.contains(indicator)) {
                indicatorCount++;
            }
        }

        // 包含2个以上句子特征词
        if (indicatorCount >= 2) {
            return true;
        }

        // 包含"能够/负责/参与/完成/实现/具备"等动词且长度较长
        if (name.length() > 10) {
            for (String indicator : SENTENCE_INDICATORS) {
                if (name.contains(indicator)) {
                    return true;
                }
            }
        }

        // 检查是否包含多个空格（可能是短语或句子）
        long spaceCount = name.chars().filter(c -> c == ' ').count();
        if (spaceCount >= 3) {
            return true;
        }

        return false;
    }

    @Override
    public int getQualityScore(String normalizedName) {
        if (!StringUtils.hasText(normalizedName)) {
            return 0;
        }

        String name = normalizedName.trim();
        int score = 100;

        // 长度惩罚
        if (name.length() < 2) {
            score -= 50;
        } else if (name.length() < 3) {
            score -= 20;
        } else if (name.length() > 20) {
            score -= 30;
        } else if (name.length() > 15) {
            score -= 10;
        }

        // 低质量模式惩罚
        if (isLowQualityName(name)) {
            score -= 60;
        }

        // 句子型惩罚
        if (isSentenceLike(name)) {
            score -= 40;
        }

        // 包含技术栈关键词加分
        String lowerName = name.toLowerCase();
        if (TECH_ALIASES.containsKey(lowerName)) {
            score += 20;
        }

        // 包含常见技术领域关键词加分
        String[] techKeywords = {"架构", "设计", "算法", "数据", "网络", "安全", "性能", "并发", "分布式", "集群"};
        for (String keyword : techKeywords) {
            if (name.contains(keyword)) {
                score += 5;
                break;
            }
        }

        return Math.max(0, Math.min(100, score));
    }
}
