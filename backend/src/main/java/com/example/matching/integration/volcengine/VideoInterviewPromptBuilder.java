package com.example.matching.integration.volcengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 视频面试Prompt构建器
 * <p>
 * 负责构建发送给豆包模型的结构化提示词：
 * - 面试题生成（文本模型）
 * - 视觉分析（视觉理解模型，分析视频帧）
 * - 语音内容分析（文本模型，分析转录文本）
 * - 会话级汇总（文本模型）
 */
@Slf4j
@Component
public class VideoInterviewPromptBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 面试题生成 ====================

    /**
     * 构建面试题生成的系统提示词
     */
    public String buildQuestionGenerationSystemPrompt() {
        return """
                你是一位资深技术面试官，擅长设计结构化、场景化的面试题目。

                ## 出题原则
                1. 基于岗位JD和能力要求，设计能真实评估候选人能力的题目
                2. 题目要具体、场景化，避免泛泛而谈
                3. 每道题聚焦1-2项核心能力
                4. 题目难度与能力等级要求匹配
                5. 使用STAR法则（情境-任务-行动-结果）来设计行为类问题
                6. 技术类问题要有具体的场景描述

                ## 输出要求
                你必须严格按照指定的JSON格式输出结果，不要输出任何其他内容。
                """;
    }

    /**
     * 构建面试题生成的用户提示词
     */
    public String buildQuestionGenerationPrompt(String postSummary, int questionCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 岗位信息\n");
        sb.append(postSummary).append("\n\n");
        sb.append("## 出题要求\n");
        sb.append("请为上述岗位设计 ").append(questionCount).append(" 道面试题。\n");
        sb.append("- 优先覆盖核心能力和必填能力\n");
        sb.append("- 技术类题目聚焦具体场景，行为类题目使用STAR法则\n");
        sb.append("- 难度分为EASY/MEDIUM/HARD，时长30-90秒\n\n");

        sb.append("""
                ## 请按以下JSON格式输出
                ```json
                {
                  "questions": [
                    {
                      "text": "题目文本",
                      "type": "TECHNICAL/BEHAVIORAL/GENERAL",
                      "difficulty": "EASY/MEDIUM/HARD",
                      "durationSeconds": 60,
                      "expectedTags": ["能力标签名称1", "能力标签名称2"]
                    }
                  ]
                }
                ```

                注意：
                - type：TECHNICAL=技术能力题，BEHAVIORAL=行为面试题，GENERAL=综合题
                - difficulty：EASY(45s)/MEDIUM(60s)/HARD(90s)
                - durationSeconds：建议回答时长（秒）
                - expectedTags：该题期望考察的能力标签名称（必须来自岗位能力列表中的标签名称）
                """);

        return sb.toString();
    }

    // ==================== 视觉分析（视频帧） ====================

    /**
     * 构建视觉分析的系统提示词（用于视觉理解模型）
     */
    public String buildVisionAnalysisSystemPrompt() {
        return """
                你是一个专业的面试评估AI助手，专注于通过视频画面分析候选人的非语言表现。

                ## 评估原则
                1. 只描述可观察到的行为，不推断内心想法
                2. 不评估任何敏感属性（性别、年龄、民族、外貌等）
                3. 保持客观、专业的评估态度

                ## 严格禁止
                - 禁止根据口型或面部动作猜测候选人说了什么
                - 禁止评价回答内容的质量或相关性
                - 禁止编造或推断候选人的语言回答
                - 只分析可见的非语言行为（表情、姿态、手势、环境等）

                ## 输出要求
                你必须严格按照指定的JSON格式输出结果，不要输出任何其他内容。
                """;
    }

    /**
     * 构建视觉分析的用户提示词（用于视觉理解模型）
     *
     * @param postSummary  岗位摘要
     * @param questionText 面试问题
     * @param frameRefs    视频帧元数据
     */
    public String buildVisionAnalysisPrompt(String postSummary, String questionText, List<Map<String, Object>> frameRefs) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 岗位信息\n");
        sb.append(postSummary != null ? postSummary : "通用岗位").append("\n\n");

        sb.append("## 面试问题\n");
        sb.append(questionText).append("\n\n");

        sb.append("""
                ## 视频画面分析要求
                请从以下维度分析候选人的视频表现：

                ### 沟通表现
                - 眼神交流：是否保持面对镜头，频繁躲闪或长时间低头
                - 面部表情：自然、僵硬、过于夸张或无表情
                - 口齿清晰度：嘴唇动作是否正常

                ### 体态与姿态
                - 坐姿：端正、前倾（投入）、后仰（放松/防御）、歪斜
                - 手势：自然辅助表达、过多挥舞、僵持不动、摸脸抓头
                - 身体晃动：稳定、轻微晃动（紧张）、大幅度摇摆

                ### 情绪与状态
                - 紧张程度：无明显紧张、轻微紧张（搓手/抿嘴）、明显紧张（出汗/颤抖）
                - 自信程度：眼神坚定/回避
                - 情绪波动：答题过程中情绪是否明显变化

                ### 异常行为（重点关注）
                - 不当表情：吐舌头、翻白眼、做鬼脸、冷笑
                - 异常动作：竖中指、捂脸、拍桌、大幅度挥舞手臂
                - 疑似作弊：频繁低头看桌面或键盘、视线飘向屏幕侧方、画面中出现他人
                - 离场/中断：起身离开、长时间背对摄像头

                ### 环境与着装
                - 背景环境：整洁专业、杂乱
                - 着装：正式、商务休闲、过于随意
                - 光线与清晰度：面部是否清晰可见
                """);

        if (frameRefs != null && !frameRefs.isEmpty()) {
            try {
                sb.append("\n抽帧元数据：").append(objectMapper.writeValueAsString(frameRefs)).append("\n\n");
            } catch (Exception e) {
                sb.append("\n");
            }
        }

        sb.append("""
                ## 请按以下JSON格式输出分析结果
                ```json
                {
                  "visualScore": 75,
                  "visualComment": "对候选人视觉表现的整体评价",
                  "evidences": [
                    {
                      "type": "VISUAL",
                      "startSecond": 0,
                      "endSecond": 0,
                      "evidenceText": "具体的视觉行为描述",
                      "confidenceScore": 0.85,
                      "rawScore": 75
                    }
                  ]
                }
                ```

                注意：
                - visualScore: 0-100分（基于非语言表现的综合评分）
                - 只描述可观察行为，不要推断年龄、性别、民族、外貌等敏感属性
                - 如有异常行为，在evidences中以VISUAL类型记录
                """);

        return sb.toString();
    }

    // ==================== 语音内容分析（转录文本） ====================

    /**
     * 构建语音内容分析的系统提示词（用于文本推理模型）
     */
    public String buildTextAnalysisSystemPrompt() {
        return """
                你是一个专业的面试评估AI助手，专注于分析候选人的语言回答内容质量。

                ## 评估原则
                1. 只评估与岗位能力相关的技能，不评估任何敏感属性
                2. 每项能力评估必须有具体证据支撑
                3. 评估结果必须使用指定的能力标签，不得自行创造标签
                4. 保持客观、专业的评估态度

                ## 输出要求
                你必须严格按照指定的JSON格式输出结果，不要输出任何其他内容。
                """;
    }

    /**
     * 构建语音内容分析的用户提示词（用于文本推理模型）
     *
     * @param postSummary        岗位摘要
     * @param allowedTags        允许的能力标签列表
     * @param questionText       面试问题
     * @param transcriptSegment  转录文本片段
     */
    public String buildTextAnalysisPrompt(String postSummary,
                                           List<Map<String, Object>> allowedTags,
                                           String questionText,
                                           String transcriptSegment) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 岗位信息\n");
        sb.append(postSummary != null ? postSummary : "通用岗位").append("\n\n");

        sb.append("## 允许评估的能力标签（只能从以下标签中选择）\n");
        try {
            sb.append(objectMapper.writeValueAsString(allowedTags)).append("\n\n");
        } catch (Exception e) {
            sb.append("[]\n\n");
        }

        sb.append("## 面试问题\n");
        sb.append(questionText).append("\n\n");

        sb.append("## 候选人回答转录\n");
        sb.append(transcriptSegment != null ? transcriptSegment : "（无转录内容）").append("\n\n");

        sb.append("""
                ## 请按以下JSON格式输出分析结果
                ```json
                {
                  "contentScore": 80,
                  "contentComment": "对回答内容质量的整体评价",
                  "evidences": [
                    {
                      "type": "TEXT",
                      "startSecond": 0,
                      "endSecond": 0,
                      "evidenceText": "具体的证据描述",
                      "confidenceScore": 0.85,
                      "rawScore": 78
                    }
                  ],
                  "abilities": [
                    {
                      "tagId": 1,
                      "tagName": "能力标签名称",
                      "masteryLevel": 3,
                      "confidenceScore": 0.80,
                      "sourceWeight": 0.75,
                      "evidenceSummary": "该能力的证据摘要",
                      "analysisComment": "该能力的分析评语"
                    }
                  ]
                }
                ```

                注意：
                - contentScore: 0-100分（基于回答内容质量的综合评分）
                - masteryLevel: 1-5（1入门/2熟悉/3掌握/4精通/5专家）
                - confidenceScore: 0.00-1.00
                - sourceWeight: 0.00-1.00
                - abilities中的tagId必须来自允许列表
                - 如果回答中没有体现某项能力，不要在abilities中包含该能力
                """);

        return sb.toString();
    }

    // ==================== 会话级汇总 ====================

    /**
     * 构建会话级汇总的系统提示词
     */
    public String buildSessionSummarySystemPrompt() {
        return """
                你是一个专业的面试评估汇总AI助手。你的任务是综合分析候选人在整个面试中的表现，生成最终的评估报告。

                ## 输出要求
                你必须严格按照指定的JSON格式输出结果，不要输出任何其他内容。
                """;
    }

    /**
     * 构建会话级汇总的用户提示词
     */
    public String buildSessionSummaryPrompt(String postSummary,
                                             String questionResults,
                                             int totalQuestions) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 岗位信息\n");
        sb.append(postSummary != null ? postSummary : "通用岗位").append("\n\n");

        sb.append("## 面试概况\n");
        sb.append("共 ").append(totalQuestions).append(" 道问题\n\n");

        sb.append("## 各问题分析结果摘要\n");
        sb.append(questionResults != null ? questionResults : "无").append("\n\n");

        sb.append("""
                ## 请按以下JSON格式输出汇总结果
                ```json
                {
                  "overallScore": 78,
                  "summaryReport": "面试综合评价报告，包含优势、不足和建议"
                }
                ```

                注意：
                - overallScore: 0-100分的综合得分
                - summaryReport: 200-500字的综合评价
                - 综合考虑语言回答质量和视觉表现
                """);

        return sb.toString();
    }
}
