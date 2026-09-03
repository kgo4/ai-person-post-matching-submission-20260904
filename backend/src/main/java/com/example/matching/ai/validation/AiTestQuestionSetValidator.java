package com.example.matching.ai.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 测试题目集合校验器
 * <p>
 * 校验规则：
 * <ul>
 *   <li>题数 3-10</li>
 *   <li>题型白名单：choice / case / text</li>
 *   <li>选择题至少两个选项</li>
 *   <li>题干、答案、解析长度受限</li>
 *   <li>单题分值合法（1-100）</li>
 * </ul>
 */
@Slf4j
@Component
public class AiTestQuestionSetValidator {

    public static final String SCENARIO = "AI_TEST_QUESTION_SET";

    public static final int MIN_QUESTION_COUNT = 3;
    public static final int MAX_QUESTION_COUNT = 10;
    public static final int MIN_OPTION_COUNT = 2;
    public static final int MAX_QUESTION_TEXT_LENGTH = 500;
    public static final int MAX_OPTION_LENGTH = 200;
    public static final int MAX_ANSWER_LENGTH = 2000;
    public static final int MAX_REFERENCE_ANSWER_LENGTH = 2000;

    private static final List<String> ALLOWED_TYPES = List.of("choice_single", "choice_multiple", "case", "text");

    /**
     * 校验标准化后的题目集合；不合法时抛出 {@link AiOutputValidationException}
     *
     * @param questions 每个元素为 AiTestAgentImpl 标准化后的题目 Map
     */
    public void validate(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new AiOutputValidationException(SCENARIO, "questions", "题目集合为空");
        }
        if (questions.size() < MIN_QUESTION_COUNT || questions.size() > MAX_QUESTION_COUNT) {
            throw new AiOutputValidationException(SCENARIO, "questions",
                    "题数超出范围 [" + MIN_QUESTION_COUNT + "," + MAX_QUESTION_COUNT + "]，实际 " + questions.size());
        }

        for (int i = 0; i < questions.size(); i++) {
            validateQuestion(i, questions.get(i));
        }
    }

    private void validateQuestion(int index, Map<String, Object> question) {
        String prefix = "questions[" + index + "]";
        if (question == null) {
            throw new AiOutputValidationException(SCENARIO, prefix, "题目为空");
        }

        String type = String.valueOf(question.getOrDefault("type", ""));
        if (!ALLOWED_TYPES.contains(type)) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".type",
                    "非法题型: " + type + "，允许: " + ALLOWED_TYPES);
        }

        String text = String.valueOf(question.getOrDefault("question", ""));
        if (text.isBlank()) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".question", "题干为空");
        }
        if (text.length() > MAX_QUESTION_TEXT_LENGTH) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".question",
                    "题干超长，最大 " + MAX_QUESTION_TEXT_LENGTH + " 字");
        }

        Object optionsObj = question.get("options");
        if ("choice_single".equals(type) || "choice_multiple".equals(type)) {
            if (!(optionsObj instanceof List<?> options) || options.size() < MIN_OPTION_COUNT) {
                throw new AiOutputValidationException(SCENARIO, prefix + ".options",
                        "选择题至少 " + MIN_OPTION_COUNT + " 个选项");
            }
            for (Object option : options) {
                String optionText = String.valueOf(option);
                if (optionText.isBlank() || optionText.length() > MAX_OPTION_LENGTH) {
                    throw new AiOutputValidationException(SCENARIO, prefix + ".options",
                            "选项为空或超长，最大 " + MAX_OPTION_LENGTH + " 字");
                }
            }
        }

        String answer = String.valueOf(question.getOrDefault("answer", ""));
        if (answer.length() > MAX_ANSWER_LENGTH) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".answer",
                    "答案超长，最大 " + MAX_ANSWER_LENGTH + " 字");
        }

        String referenceAnswer = String.valueOf(question.getOrDefault("referenceAnswer", ""));
        if (referenceAnswer.length() > MAX_REFERENCE_ANSWER_LENGTH) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".referenceAnswer",
                    "解析超长，最大 " + MAX_REFERENCE_ANSWER_LENGTH + " 字");
        }

        Object scoreObj = question.get("score");
        if (scoreObj != null) {
            int score = toInt(scoreObj);
            if (score < 1 || score > 100) {
                throw new AiOutputValidationException(SCENARIO, prefix + ".score",
                        "单题分值不合法: " + scoreObj);
            }
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new AiOutputValidationException(SCENARIO, "score", "分值不是数字: " + value);
        }
    }
}
