package com.example.matching.service.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 标签质量评估器
 * <p>
 * 评估标签名称是否符合入库标准，拒绝泛词、句子、噪声等低质量标签。
 *
 * @author system
 */
@Slf4j
@Component
public class TagQualityAssessor {

    /**
     * 标签名称最大长度（超过视为句子）
     */
    private static final int MAX_TAG_NAME_LENGTH = 20;

    /**
     * 标签名称最小长度
     */
    private static final int MIN_TAG_NAME_LENGTH = 2;

    /**
     * 泛词黑名单（这些词太宽泛，不能作为有效能力标签）
     */
    private static final Set<String> GENERIC_WORDS = new HashSet<>(Arrays.asList(
            // 中文泛词
            "能力强", "能力好", "能力优秀", "能力突出",
            "沟通能力强", "学习能力好", "团队合作能力强", "抗压能力强",
            "项目经验丰富", "经验丰富", "技术能力强", "专业能力强",
            "综合素质高", "综合能力强", "业务能力强", "执行力强",
            "创新能力强", "领导力强", "管理能力强", "分析能力强",
            "解决问题能力强", "沟通能力", "学习能力", "团队合作",
            "抗压能力", "执行能力", "创新能力", "领导能力",
            "管理能力", "分析能力", "解决问题能力", "专业能力",
            "业务能力", "技术能力", "综合素质", "基本能力",
            // 英文泛词
            "communication", "teamwork", "leadership", "management",
            "problem solving", "analytical", "creative", "innovative",
            "experienced", "skilled", "professional", "expert"
    ));

    /**
     * 句子特征正则（包含动词、助词等典型句子成分）
     */
    private static final Pattern SENTENCE_PATTERN_CN = Pattern.compile(
            ".*[的了是在有与和或但而因为所以虽然但是如果只要不但而且].*"
    );

    /**
     * 英文句子特征（包含常见动词、介词组合）
     */
    private static final Pattern SENTENCE_PATTERN_EN = Pattern.compile(
            ".*\\b(with|have|has|had|was|were|been|being|is|am|are|do|does|did|will|would|could|should|may|might|can|shall|must|need|dare|ought|used|to|of|in|for|on|at|by|from|through|during|before|after|above|below|between|under|over|into|out|off|about|against|along|among|around|behind|beside|beyond|inside|outside|throughout|toward|towards|upon|within|without)\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 纯数字或包含大量数字
     */
    private static final Pattern MOSTLY_DIGITS = Pattern.compile("^\\d+$|^\\d+.*\\d+$|.*\\d{4,}.*");

    /**
     * 特殊字符过多
     */
    private static final Pattern TOO_MANY_SPECIAL_CHARS = Pattern.compile(".*[!@#$%^&*()+=\\[\\]{}|;':\",./<>?].*");

    /**
     * 评估标签质量
     *
     * @param tagName 标签名称
     * @return 质量评估结果
     */
    public QualityAssessment assess(String tagName) {
        if (!StringUtils.hasText(tagName)) {
            return QualityAssessment.rejected("标签名称为空");
        }

        String trimmed = tagName.trim();

        // 1. 长度检查
        if (trimmed.length() < MIN_TAG_NAME_LENGTH) {
            return QualityAssessment.rejected("标签名称过短（少于" + MIN_TAG_NAME_LENGTH + "个字符）");
        }
        if (trimmed.length() > MAX_TAG_NAME_LENGTH) {
            return QualityAssessment.rejected("标签名称过长（超过" + MAX_TAG_NAME_LENGTH + "个字符），疑似句子");
        }

        // 2. 泛词检查
        if (GENERIC_WORDS.contains(trimmed)) {
            return QualityAssessment.rejected("标签名称是泛词，不适合入库: " + trimmed);
        }

        // 3. 句子特征检查
        if (isSentence(trimmed)) {
            return QualityAssessment.rejected("标签名称疑似句子，不是能力名: " + trimmed);
        }

        // 4. 纯数字检查
        if (MOSTLY_DIGITS.matcher(trimmed).matches()) {
            return QualityAssessment.rejected("标签名称包含过多数字: " + trimmed);
        }

        // 5. 特殊字符检查
        if (TOO_MANY_SPECIAL_CHARS.matcher(trimmed).matches()) {
            return QualityAssessment.rejected("标签名称包含特殊字符: " + trimmed);
        }

        // 6. 重复字符检查
        if (hasExcessiveRepetition(trimmed)) {
            return QualityAssessment.rejected("标签名称包含过多重复字符: " + trimmed);
        }

        return QualityAssessment.accepted(trimmed);
    }

    /**
     * 判断是否是句子
     */
    private boolean isSentence(String text) {
        // 中文句子特征
        if (SENTENCE_PATTERN_CN.matcher(text).matches()) {
            return true;
        }
        // 英文句子特征
        if (SENTENCE_PATTERN_EN.matcher(text).matches()) {
            return true;
        }
        // 包含多个空格（英文短语通常用空格分隔，但句子通常有更多空格）
        if (text.split("\\s+").length > 5) {
            return true;
        }
        return false;
    }

    /**
     * 检查是否有过多重复字符
     */
    private boolean hasExcessiveRepetition(String text) {
        if (text.length() < 3) {
            return false;
        }
        // 检查连续相同字符
        int maxRepeat = 0;
        int currentRepeat = 1;
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == text.charAt(i - 1)) {
                currentRepeat++;
                maxRepeat = Math.max(maxRepeat, currentRepeat);
            } else {
                currentRepeat = 1;
            }
        }
        return maxRepeat >= 3;
    }

    /**
     * 质量评估结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class QualityAssessment {
        /**
         * 是否通过质量检查
         */
        private boolean accepted;

        /**
         * 拒绝原因（如果未通过）
         */
        private String rejectReason;

        /**
         * 标准化后的标签名称
         */
        private String normalizedName;

        public static QualityAssessment accepted(String normalizedName) {
            return new QualityAssessment(true, null, normalizedName);
        }

        public static QualityAssessment rejected(String reason) {
            return new QualityAssessment(false, reason, null);
        }
    }
}
