package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.mapper.post.PostPrototypeMapper;
import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.service.post.support.TextSanitizationPolicy;
import com.example.matching.service.governance.GovernanceFilterRuleService;
import com.example.matching.entity.governance.GovernanceFilterRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 岗位数据清洗规则引擎：文本去噪、名称清洗、职责/要求提取、质量评分、去重检测、阻断判定。
 * <p>
 * 从 PostDataCleaningServiceImpl（700+ 行）中拆分的纯规则计算组件。
 */
@Slf4j
@Component
public class PostCleaningRulesEngine {

    private final PostPrototypeMapper postPrototypeMapper;
    private final MeterRegistry meterRegistry;

    private final Counter duplicateBlockCounter;
    private final Counter duplicateSuspectedCounter;
    private final Counter duplicateNoneCounter;
    private GovernanceFilterRuleService governanceFilterRuleService;

    public PostCleaningRulesEngine(PostPrototypeMapper postPrototypeMapper,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false) MeterRegistry meterRegistry) {
        this.postPrototypeMapper = postPrototypeMapper;
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            this.duplicateBlockCounter = Counter.builder("post.cleaning.duplicate.status")
                    .tag("status", "BLOCK")
                    .description("Post duplicate detection BLOCK count")
                    .register(meterRegistry);
            this.duplicateSuspectedCounter = Counter.builder("post.cleaning.duplicate.status")
                    .tag("status", "SUSPECTED")
                    .description("Post duplicate detection SUSPECTED count")
                    .register(meterRegistry);
            this.duplicateNoneCounter = Counter.builder("post.cleaning.duplicate.status")
                    .tag("status", "NONE")
                    .description("Post duplicate detection NONE count")
                    .register(meterRegistry);
        } else {
            this.duplicateBlockCounter = null;
            this.duplicateSuspectedCounter = null;
            this.duplicateNoneCounter = null;
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setGovernanceFilterRuleService(GovernanceFilterRuleService governanceFilterRuleService) {
        this.governanceFilterRuleService = governanceFilterRuleService;
    }
    private static final double DUPLICATE_BLOCK_THRESHOLD = 0.92;
    /** 疑似重复标记阈值 */
    private static final double DUPLICATE_SUSPECTED_THRESHOLD = 0.80;
    /** 质量阻断阈值 */
    private static final BigDecimal QUALITY_BLOCK_THRESHOLD = new BigDecimal("0.40");
    /** 质量警告阈值 */
    private static final BigDecimal QUALITY_WARNING_THRESHOLD = new BigDecimal("0.70");

    // ========== 噪声模式 ==========

    /** 常见噪声模式：广告、联系方式、公司介绍等 */
    private static final List<Pattern> NOISE_PATTERNS = List.of(
            // 联系方式：要求"关键词 + 号码特征"组合，防止误伤"负责电话会议系统维护"等正文。
            // 无词边界的关键词（电话/邮箱）不再单独出现即删除，必须后跟数字/账号特征。
            Pattern.compile("(?i)(联系电话|联系方式|手机号|电话|手机|tel|phone)\\s*[:：]?\\s*\\d{3,4}[- ]?\\d{7,8}\\b"),
            Pattern.compile("(?i)(邮箱|email|e-mail|mail)\\s*[:：]?\\s*[\\w.+-]+@[\\w.-]+\\.[\\w]+"),
            Pattern.compile("(?i)(qq|微信|vx|wx|微信号)\\s*[:：]?\\s*[\\w-]{5,15}\\b"),
            Pattern.compile("\\b\\d{11}\\b"),  // 裸手机号
            Pattern.compile("\\b[\\w.-]+@[\\w.-]+\\.\\w+\\b"),  // 裸邮箱
            // 公司广告：必须带冒号分隔（标题式），防误删正文段落
            Pattern.compile("(?i)(公司简介|企业介绍|关于我们|公司官网|公司地址)\\s*[:：][^\\n]{0,200}", Pattern.CASE_INSENSITIVE),
            // 招聘广告：必须带冒号分隔
            Pattern.compile("(?i)(简历投递|投递方式|应聘方式|招聘流程|面试流程)\\s*[:：][^\\n]{0,200}", Pattern.CASE_INSENSITIVE),
            // 格式噪声
            TextSanitizationPolicy.CONTROL_CHARS_PATTERN,  // 控制字符（统一策略）
            Pattern.compile("={3,}"),  // 分隔线
            Pattern.compile("-{3,}"),  // 分隔线
            Pattern.compile("\\*{3,}")  // 分隔线
    );

    /** 万金油标签（通用描述，降低质量分） */
    private static final List<String> GENERIC_TERMS = List.of(
            "沟通能力", "团队合作", "学习能力", "抗压能力", "责任心强", "积极主动",
            "良好的沟通", "团队协作", "自我驱动", "吃苦耐劳", "服从安排"
    );

    /** 职责相关关键词 */
    private static final List<String> RESPONSIBILITY_KEYWORDS = List.of(
            "负责", "参与", "主导", "承担", "完成", "开发", "设计", "实现", "维护", "优化",
            "管理", "协调", "推动", "推进", "搭建", "建设", "支撑", "保障"
    );

    /** 要求相关关键词 */
    private static final List<String> REQUIREMENT_KEYWORDS = List.of(
            "熟悉", "精通", "掌握", "了解", "具备", "具有", "要求", "需要", "优先",
            "学历", "经验", "本科", "硕士", "博士", "年以上"
    );
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String cleaned = rawText;

        // 1. 优先使用后台配置的岗位规则；配置服务不可用时保留原有内置规则兜底。
        List<GovernanceFilterRule> configuredRules = governanceFilterRuleService == null
                ? List.of() : governanceFilterRuleService.activeRules(GovernanceFilterRuleService.POST_JD);
        if (!configuredRules.isEmpty()) {
            for (GovernanceFilterRule rule : configuredRules) {
                if ("KEYWORD".equalsIgnoreCase(rule.getRuleType())) {
                    cleaned = cleaned.replace(rule.getPatternValue(), "");
                } else if ("REGEX".equalsIgnoreCase(rule.getRuleType())) {
                    try {
                        cleaned = Pattern.compile(rule.getPatternValue(), Pattern.CASE_INSENSITIVE)
                                .matcher(cleaned).replaceAll("");
                    } catch (RuntimeException ignored) {
                        log.warn("忽略无效岗位治理正则规则: ruleId={}", rule.getId());
                    }
                }
            }
        } else {
            for (Pattern pattern : NOISE_PATTERNS) {
                cleaned = pattern.matcher(cleaned).replaceAll("");
            }
        }

        // 2. 移除多余空白行
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        // 3. 移除首尾空白
        cleaned = cleaned.trim();

        return cleaned;
    }

    /**
     * 提取被移除的噪声内容
     */
    public String extractNoise(String rawText, String cleanedText) {
        if (rawText == null || cleanedText == null) {
            return "";
        }

        // 简单实现：找出rawText中有但cleanedText中没有的部分
        // 更精确的实现需要跟踪具体移除了哪些内容
        Set<String> rawLines = new LinkedHashSet<>(Arrays.asList(rawText.split("\\n")));
        Set<String> cleanedLines = new LinkedHashSet<>(Arrays.asList(cleanedText.split("\\n")));

        List<String> removedLines = rawLines.stream()
                .filter(line -> !cleanedLines.contains(line) && !line.trim().isEmpty())
                .collect(Collectors.toList());

        return String.join("\n", removedLines);
    }

    /**
     * 清洗岗位名称
     */
    public String cleanPostName(String postName) {
        if (postName == null || postName.isBlank()) {
            return "";
        }

        String cleaned = postName.trim();

        // 移除常见后缀
        cleaned = cleaned.replaceAll("(招聘|急聘|高薪|诚聘|岗位|职位)[\\s]*$", "");

        // 移除括号内容（如：Java开发工程师(15-25K)）
        cleaned = cleaned.replaceAll("[（(][^）)]*[）)]", "");

        return cleaned.trim();
    }

    /**
     * 提取职责列表
     */
    public List<String> extractResponsibilities(String text) {
        List<String> responsibilities = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return responsibilities;
        }

        String[] lines = text.split("\\n");
        boolean inResponsibilitySection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 检测职责段落标题
            if (trimmed.matches("(?i).*(岗位职责|工作职责|职责描述|工作内容|主要职责|职位描述).*")) {
                inResponsibilitySection = true;
                continue;
            }

            // 检测其他段落标题（退出职责段落）
            if (trimmed.matches("(?i).*(任职要求|岗位要求|任职资格|职位要求|基本要求|能力要求).*")) {
                inResponsibilitySection = false;
                continue;
            }

            // 在职责段落中，提取列表项
            if (inResponsibilitySection) {
                String item = trimmed.replaceAll("^[0-9]+[.、)）]\\s*", "")
                        .replaceAll("^[•·◆◇○●-]\\s*", "")
                        .trim();
                if (!item.isEmpty() && item.length() >= 4) {
                    responsibilities.add(item);
                }
            } else {
                // 通过关键词匹配
                for (String keyword : RESPONSIBILITY_KEYWORDS) {
                    if (trimmed.contains(keyword) && trimmed.length() >= 8 && trimmed.length() <= 200) {
                        String item = trimmed.replaceAll("^[0-9]+[.、)）]\\s*", "")
                                .replaceAll("^[•·◆◇○●-]\\s*", "")
                                .trim();
                        if (!item.isEmpty()) {
                            responsibilities.add(item);
                            break;
                        }
                    }
                }
            }
        }

        return responsibilities;
    }

    /**
     * 提取要求列表
     */
    public List<String> extractRequirements(String text) {
        List<String> requirements = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return requirements;
        }

        String[] lines = text.split("\\n");
        boolean inRequirementSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 检测要求段落标题
            if (trimmed.matches("(?i).*(任职要求|岗位要求|任职资格|职位要求|基本要求|能力要求).*")) {
                inRequirementSection = true;
                continue;
            }

            // 检测其他段落标题（退出要求段落）
            if (trimmed.matches("(?i).*(岗位职责|工作职责|职责描述|工作内容|主要职责).*")) {
                inRequirementSection = false;
                continue;
            }

            // 在要求段落中，提取列表项
            if (inRequirementSection) {
                String item = trimmed.replaceAll("^[0-9]+[.、)）]\\s*", "")
                        .replaceAll("^[•·◆◇○●-]\\s*", "")
                        .trim();
                if (!item.isEmpty() && item.length() >= 4) {
                    requirements.add(item);
                }
            } else {
                // 通过关键词匹配
                for (String keyword : REQUIREMENT_KEYWORDS) {
                    if (trimmed.contains(keyword) && trimmed.length() >= 4 && trimmed.length() <= 200) {
                        String item = trimmed.replaceAll("^[0-9]+[.、)）]\\s*", "")
                                .replaceAll("^[•·◆◇○●-]\\s*", "")
                                .trim();
                        if (!item.isEmpty()) {
                            requirements.add(item);
                            break;
                        }
                    }
                }
            }
        }

        return requirements;
    }

    /**
     * 计算质量评分
     */
    public PostCleaningResult.QualityDetails calculateQualityScore(String cleanedText,
                                                                      List<String> responsibilities,
                                                                      List<String> requirements) {
        PostCleaningResult.QualityDetails details = new PostCleaningResult.QualityDetails();
        List<String> warnings = new ArrayList<>();

        // 1. 文本长度评分
        int textLength = cleanedText != null ? cleanedText.length() : 0;
        BigDecimal lengthScore;
        if (textLength >= 500) {
            lengthScore = new BigDecimal("1.0");
        } else if (textLength >= 200) {
            lengthScore = new BigDecimal("0.7");
        } else if (textLength >= 100) {
            lengthScore = new BigDecimal("0.4");
            warnings.add("JD文本较短（" + textLength + "字），可能信息不足");
        } else {
            lengthScore = new BigDecimal("0.1");
            warnings.add("JD文本过短（" + textLength + "字），信息严重不足");
        }
        details.setLengthScore(lengthScore);

        // 2. 结构化程度评分
        int structureCount = responsibilities.size() + requirements.size();
        BigDecimal structureScore;
        if (structureCount >= 6) {
            structureScore = new BigDecimal("1.0");
        } else if (structureCount >= 3) {
            structureScore = new BigDecimal("0.7");
        } else if (structureCount >= 1) {
            structureScore = new BigDecimal("0.4");
            warnings.add("结构化信息较少（" + structureCount + "项），建议补充职责和要求");
        } else {
            structureScore = new BigDecimal("0.1");
            warnings.add("无法提取结构化职责和要求");
        }
        details.setStructureScore(structureScore);

        // 3. 技术关键词丰富度评分
        long techKeywordCount = 0;
        if (cleanedText != null) {
            List<String> techKeywords = List.of("Java", "Python", "Spring", "MySQL", "Redis", "Docker",
                    "Kubernetes", "微服务", "分布式", "高并发", "大数据", "机器学习", "深度学习",
                    "前端", "后端", "全栈", "架构", "算法", "数据库", "Linux", "Git",
                    "CI/CD", "REST", "API", "消息队列", "缓存", "索引", "并发", "多线程");
            for (String keyword : techKeywords) {
                if (cleanedText.contains(keyword)) {
                    techKeywordCount++;
                }
            }
        }
        BigDecimal keywordScore;
        if (techKeywordCount >= 5) {
            keywordScore = new BigDecimal("1.0");
        } else if (techKeywordCount >= 3) {
            keywordScore = new BigDecimal("0.7");
        } else if (techKeywordCount >= 1) {
            keywordScore = new BigDecimal("0.4");
        } else {
            keywordScore = new BigDecimal("0.2");
            warnings.add("JD中技术关键词较少，可能不够专业");
        }
        details.setKeywordScore(keywordScore);

        // 4. 通用描述占比评分（越低越好）
        long genericCount = 0;
        if (cleanedText != null) {
            for (String term : GENERIC_TERMS) {
                if (cleanedText.contains(term)) {
                    genericCount++;
                }
            }
        }
        BigDecimal genericRatioScore;
        if (genericCount <= 1) {
            genericRatioScore = new BigDecimal("1.0");
        } else if (genericCount <= 3) {
            genericRatioScore = new BigDecimal("0.7");
        } else {
            genericRatioScore = new BigDecimal("0.4");
            warnings.add("JD包含较多通用描述（" + genericCount + "项），建议增加具体技术要求");
        }
        details.setGenericRatioScore(genericRatioScore);

        details.setWarnings(warnings);
        return details;
    }

    /**
     * 去重检测
     */
    public void detectDuplicate(PostCleaningResult result, String postName, String cleanedText) {
        if (cleanedText == null || cleanedText.isEmpty()) {
            result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
            return;
        }

        // 查询所有岗位原型进行比对
        List<PostPrototype> prototypes = postPrototypeMapper.selectList(
                Wrappers.<PostPrototype>lambdaQuery().eq(PostPrototype::getStatus, 1));

        if (prototypes.isEmpty()) {
            result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
            return;
        }

        // 计算与每个原型的相似度
        double bestScore = 0;
        PostPrototype bestMatch = null;

        for (PostPrototype prototype : prototypes) {
            if (prototype.getDescription() == null || prototype.getDescription().isEmpty()) {
                continue;
            }

            double similarity = calculateTextSimilarity(cleanedText, prototype.getDescription());
            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = prototype;
            }
        }

        // 判定重复状态
        if (bestScore >= DUPLICATE_BLOCK_THRESHOLD) {
            result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_DUPLICATE_BLOCKED);
            result.setDuplicatePostId(bestMatch != null ? bestMatch.getId() : null);
            result.setDuplicatePostName(bestMatch != null ? bestMatch.getPrototypeName() : null);
            result.setDuplicateScore(new BigDecimal(String.valueOf(bestScore)).setScale(4, RoundingMode.HALF_UP));
            if (duplicateBlockCounter != null) duplicateBlockCounter.increment();
        } else if (bestScore >= DUPLICATE_SUSPECTED_THRESHOLD) {
            result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_SUSPECTED);
            result.setDuplicatePostId(bestMatch != null ? bestMatch.getId() : null);
            result.setDuplicatePostName(bestMatch != null ? bestMatch.getPrototypeName() : null);
            result.setDuplicateScore(new BigDecimal(String.valueOf(bestScore)).setScale(4, RoundingMode.HALF_UP));
            if (duplicateSuspectedCounter != null) duplicateSuspectedCounter.increment();
        } else {
            result.setDuplicateStatus(PostCleaningRecordVO.DUPLICATE_STATUS_NONE);
            if (duplicateNoneCounter != null) duplicateNoneCounter.increment();
        }

        if (meterRegistry != null) {
            Counter.builder("post.cleaning.duplicate.similarity")
                    .tag("bucket", similarityBucket(bestScore))
                    .description("Post duplicate similarity score distribution")
                    .register(meterRegistry)
                    .increment();
        }

        if (bestScore >= 0.85 && bestMatch != null) {
            String truncatedText = cleanedText.length() > 200 ? cleanedText.substring(0, 200) + "..." : cleanedText;
            log.info("[DUPLICATE_HIGH_SIM] similarity={}, postName={}, matchId={}, matchName={}, textPreview={}",
                    String.format("%.4f", bestScore), postName, bestMatch.getId(),
                    bestMatch.getPrototypeName(), truncatedText);
        }
    }

    private static String similarityBucket(double score) {
        if (score >= 0.95) return "0.95-1.00";
        if (score >= 0.90) return "0.90-0.95";
        if (score >= 0.85) return "0.85-0.90";
        if (score >= 0.80) return "0.80-0.85";
        if (score >= 0.70) return "0.70-0.80";
        if (score >= 0.50) return "0.50-0.70";
        return "0.00-0.50";
    }

    /**
     * 计算文本相似度（基于字符级Jaccard相似度）
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
     * 阻断判定
     */
    public void determineBlock(PostCleaningResult result) {
        // 1. 强重复阻断
        if (PostCleaningRecordVO.DUPLICATE_STATUS_DUPLICATE_BLOCKED.equals(result.getDuplicateStatus())) {
            result.setBlocked(true);
            result.setBlockReason("岗位与已有岗位「" + result.getDuplicatePostName() + "」高度重复（相似度：" + result.getDuplicateScore() + "）");
            return;
        }

        // 2. 质量阻断
        if (result.getQualityScore() != null && result.getQualityScore().compareTo(QUALITY_BLOCK_THRESHOLD) < 0) {
            result.setBlocked(true);
            result.setBlockReason("岗位数据质量过低（评分：" + result.getQualityScore() + "），请补充完整的岗位描述");
            return;
        }

        // 3. 清洗后内容过少
        if (result.getCleanedText() != null && result.getCleanedText().length() < 50) {
            result.setBlocked(true);
            result.setBlockReason("清洗后有效内容过少（" + result.getCleanedText().length() + "字），请提供完整的岗位描述");
            return;
        }

        // 4. 岗位名称缺失
        if (result.getCleanedPostName() == null || result.getCleanedPostName().isEmpty()) {
            result.setBlocked(true);
            result.setBlockReason("岗位名称缺失");
            return;
        }

        result.setBlocked(false);
    }
}
