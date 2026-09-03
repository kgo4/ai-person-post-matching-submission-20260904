package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.learning.LearningAssessmentGenerateRequest;
import com.example.matching.entity.learning.LearningAssessmentItem;
import com.example.matching.entity.learning.LearningPathPlan;
import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.mapper.learning.LearningAssessmentItemMapper;
import com.example.matching.mapper.learning.LearningPathPlanMapper;
import com.example.matching.mapper.learning.LearningPathStepMapper;
import com.example.matching.service.learning.LearningAssessmentService;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.agent.dto.learning.LearningAssessmentAiQuestion;
import com.example.matching.agent.dto.learning.LearningAssessmentAiScore;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 学习评估服务实现
 * <p>
 * 题目生成与答案评分优先走 AI（LangChain4jChatService，失败自动降级），
 * AI 不可用时回退为确定性模板/关键词评分，保证测评流程不阻断。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningAssessmentServiceImpl implements LearningAssessmentService {

    private final LearningAssessmentItemMapper assessmentItemMapper;
    private final LearningPathPlanMapper planMapper;
    private final LearningPathStepMapper stepMapper;
    private final CapabilityClosureService capabilityClosureService;
    private final LangChain4jChatService langChain4jChatService;
    private final LlmResponseParser llmResponseParser;
    private final ObjectMapper objectMapper;

    @Override
    public List<LearningAssessmentItem> generateAssessments(LearningAssessmentGenerateRequest request) {
        Long planId = request.getPlanId();

        // 验证计划存在
        LearningPathPlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getIsDeleted() == 1) {
            throw new BusinessException(10702, "学习路径计划不存在: " + planId);
        }

        // 生成测评必须幂等：页面重复点击或网络重试直接复用已生成题目，
        // 避免同一学习路径重复调用模型并产生重复记录。
        List<LearningAssessmentItem> existingItems = assessmentItemMapper.selectList(
                new LambdaQueryWrapper<LearningAssessmentItem>()
                        .eq(LearningAssessmentItem::getPlanId, planId)
                        .eq(LearningAssessmentItem::getIsDeleted, 0)
                        .orderByAsc(LearningAssessmentItem::getId));
        if (!existingItems.isEmpty()) {
            log.info("复用已有学习测评题目: planId={}, count={}", planId, existingItems.size());
            return existingItems;
        }

        // 加载学习步骤
        LambdaQueryWrapper<LearningPathStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(LearningPathStep::getPlanId, planId)
                .eq(LearningPathStep::getIsDeleted, 0)
                .orderByAsc(LearningPathStep::getSortOrder);
        List<LearningPathStep> steps = stepMapper.selectList(stepWrapper);

        if (steps.isEmpty()) {
            throw new BusinessException(10720, "学习路径计划暂无学习步骤，无法生成评估题目");
        }

        List<LearningAssessmentItem> generatedItems = new ArrayList<>();

        // 为每个步骤生成面试题（AI 优先，失败降级模板）
        for (LearningPathStep step : steps) {
            LearningAssessmentItem item = createAiInterviewQuestion(planId, step);
            if (item == null) {
                item = createInterviewQuestion(planId, step);
            }
            assessmentItemMapper.insert(item);
            generatedItems.add(item);
        }

        // 如果需要，生成项目评审题
        if (Boolean.TRUE.equals(request.getIncludeProjectReview())) {
            LearningAssessmentItem projectReview = createProjectReviewQuestion(planId);
            assessmentItemMapper.insert(projectReview);
            generatedItems.add(projectReview);
        }

        log.info("生成评估题目: planId={}, count={}", planId, generatedItems.size());

        return generatedItems;
    }

    @Override
    public List<LearningAssessmentItem> getAssessmentsByPlan(Long planId) {
        LambdaQueryWrapper<LearningAssessmentItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningAssessmentItem::getPlanId, planId)
                .eq(LearningAssessmentItem::getIsDeleted, 0)
                .orderByAsc(LearningAssessmentItem::getId);
        return assessmentItemMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningAssessmentItem answer(Long assessmentId, String answerText) {
        LearningAssessmentItem item = assessmentItemMapper.selectById(assessmentId);
        if (item == null || Integer.valueOf(1).equals(item.getIsDeleted())) {
            throw new BusinessException(10721, "学习测评题目不存在");
        }
        if (answerText == null || answerText.isBlank()) {
            throw new BusinessException(10722, "请先填写答案");
        }

        AssessmentScore scored = scoreWithAi(item, answerText);
        if (scored == null) {
            scored = scoreAnswer(answerText);
        }
        LocalDateTime now = LocalDateTime.now();
        item.setAnswerText(answerText.trim());
        item.setScore(scored.score());
        item.setAssessmentStatus(scored.passed() ? "PASSED" : "NOT_PASSED");
        item.setScoringFeedback(scored.feedback());
        item.setAnsweredTime(now);
        item.setScoredTime(now);
        assessmentItemMapper.updateById(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapabilityClosureResult confirmAbilityImprovement(Long planId, Long stepId) {
        LearningPathPlan plan = planMapper.selectById(planId);
        if (plan == null || Integer.valueOf(1).equals(plan.getIsDeleted())) {
            throw new BusinessException(10702, "学习路径计划不存在");
        }
        LearningPathStep step = stepMapper.selectById(stepId);
        if (step == null || Integer.valueOf(1).equals(step.getIsDeleted()) || !planId.equals(step.getPlanId())) {
            throw new BusinessException(10703, "学习步骤不存在或不属于当前计划");
        }
        Long passedCount = assessmentItemMapper.selectCount(new LambdaQueryWrapper<LearningAssessmentItem>()
                .eq(LearningAssessmentItem::getPlanId, planId)
                .eq(LearningAssessmentItem::getStepId, stepId)
                .eq(LearningAssessmentItem::getAssessmentStatus, "PASSED")
                .eq(LearningAssessmentItem::getIsDeleted, 0));
        if (passedCount == null || passedCount == 0) {
            throw new BusinessException(10723, "请先通过该能力的学习测评后再确认提升");
        }

        LearningOutcomeConfirmDTO outcome = new LearningOutcomeConfirmDTO();
        outcome.setEmpId(plan.getEmpId());
        outcome.setTagId(step.getAbilityTagId());
        outcome.setAbilityName(step.getAbilityName());
        outcome.setCompletedResourceId(step.getResourceId() != null ? step.getResourceId() : step.getId());
        outcome.setBeforeLevel(step.getCurrentLevel());
        outcome.setConfirmedLevel(Math.max(1, Math.min(5, step.getTargetLevel() != null ? step.getTargetLevel() : 1)));
        outcome.setConfirmationSource("LEARNING_PROJECT");
        outcome.setNote("学习路径测评通过：plan=" + planId + ", step=" + stepId);
        CapabilityClosureResult result = capabilityClosureService.onLearningOutcomeConfirmed(outcome);
        if (result == null || !"SUCCEEDED".equals(result.getClosureStatus())) {
            throw new BusinessException(10724, result != null ? result.getMessage() : "能力提升确认失败");
        }
        step.setEvidenceStatus("VERIFIED");
        step.setStatus("COMPLETED");
        stepMapper.updateById(step);
        return result;
    }

    /**
     * AI 生成个性化测评题目；失败返回 null（调用方降级为模板）。
     */
    private LearningAssessmentItem createAiInterviewQuestion(Long planId, LearningPathStep step) {
        String systemPrompt = loadPrompt("ai/prompt/learning-assessment-question-system.txt");
        String userPrompt = buildQuestionPrompt(step);
        String aiResponse = langChain4jChatService.chat(
                "learning-assessment-question", systemPrompt, userPrompt, () -> null, 15);
        if (aiResponse == null || aiResponse.isBlank()) {
            log.debug("AI生成测评题目不可用，使用模板: ability={}", step.getAbilityName());
            return null;
        }
        try {
            String json = llmResponseParser.extractJson(aiResponse);
            LearningAssessmentAiQuestion question = objectMapper.readValue(json, LearningAssessmentAiQuestion.class);
            if (question == null || question.getQuestionText() == null || question.getQuestionText().isBlank()) {
                log.debug("AI生成测评题目内容为空，使用模板: ability={}", step.getAbilityName());
                return null;
            }
            LearningAssessmentItem item = new LearningAssessmentItem();
            item.setPlanId(planId);
            item.setStepId(step.getId());
            item.setAbilityTagId(step.getAbilityTagId());
            item.setQuestionType("INTERVIEW");
            item.setQuestionText(question.getQuestionText());
            item.setReferenceAnswer(buildReferenceAnswer(step, question));
            item.setDifficultyLevel(question.getDifficultyLevel() != null
                    ? question.getDifficultyLevel() : mapPriorityToDifficulty(step.getPriority()));
            item.setSource("AI_LEARNING");
            item.setAssessmentStatus("PENDING");
            item.setIsDeleted(0);
            return item;
        } catch (Exception e) {
            log.warn("解析AI生成测评题目失败，使用模板: ability={}, error={}", step.getAbilityName(), e.getMessage());
            return null;
        }
    }

    private String buildQuestionPrompt(LearningPathStep step) {
        StringBuilder sb = new StringBuilder();
        sb.append("能力名称：").append(valueOrEmpty(step.getAbilityName())).append("\n");
        sb.append("目标等级：").append(step.getTargetLevel() == null ? "3" : step.getTargetLevel()).append("（1-5）\n");
        sb.append("当前等级：").append(step.getCurrentLevel() == null ? "-" : step.getCurrentLevel()).append("\n");
        sb.append("差距类型：").append(valueOrEmpty(step.getGapType())).append("\n");
        sb.append("差距优先级：").append(valueOrEmpty(step.getPriority())).append("\n");
        return sb.toString();
    }

    private String buildReferenceAnswer(LearningPathStep step, LearningAssessmentAiQuestion question) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标能力：").append(valueOrEmpty(step.getAbilityName()))
                .append("，目标等级：").append(step.getTargetLevel() == null ? "3" : step.getTargetLevel()).append("\n");
        if (question.getReferenceAnswer() != null && !question.getReferenceAnswer().isBlank()) {
            sb.append("参考答案要点：").append(question.getReferenceAnswer()).append("\n");
        }
        if (question.getScoringPoints() != null && !question.getScoringPoints().isBlank()) {
            sb.append("评分要点：").append(question.getScoringPoints());
        }
        return sb.toString();
    }

    /**
     * AI 评审答案；失败返回 null（调用方降级为关键词评分）。
     */
    private AssessmentScore scoreWithAi(LearningAssessmentItem item, String answerText) {
        String systemPrompt = loadPrompt("ai/prompt/learning-assessment-score-system.txt");
        String userPrompt = buildScorePrompt(item, answerText);
        String aiResponse = langChain4jChatService.chat(
                "learning-assessment-score", systemPrompt, userPrompt, () -> null, 20);
        if (aiResponse == null || aiResponse.isBlank()) {
            log.debug("AI评分不可用，使用关键词评分: id={}", item.getId());
            return null;
        }
        try {
            String json = llmResponseParser.extractJson(aiResponse);
            LearningAssessmentAiScore score = objectMapper.readValue(json, LearningAssessmentAiScore.class);
            if (score == null || score.getScore() == null) {
                log.debug("AI评分结果为空，使用关键词评分: id={}", item.getId());
                return null;
            }
            int finalScore = Math.max(0, Math.min(100, score.getScore()));
            boolean passed = score.getPassed() != null ? score.getPassed() : finalScore >= 60;
            String feedback = score.getFeedback() != null && !score.getFeedback().isBlank()
                    ? score.getFeedback() : (passed ? "已通过。" : "未通过，请补充实践细节。");
            return new AssessmentScore(finalScore, passed, feedback);
        } catch (Exception e) {
            log.warn("解析AI评分失败，使用关键词评分: id={}, error={}", item.getId(), e.getMessage());
            return null;
        }
    }

    private String buildScorePrompt(LearningAssessmentItem item, String answerText) {
        StringBuilder sb = new StringBuilder();
        sb.append("测评题目：").append(valueOrEmpty(item.getQuestionText())).append("\n\n");
        sb.append("参考答案/评分要点：").append(valueOrEmpty(item.getReferenceAnswer())).append("\n\n");
        sb.append("员工答案：").append(answerText).append("\n");
        return sb.toString();
    }

    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载提示词失败: path={}, error={}", path, e.getMessage());
            return null;
        }
    }

    private String valueOrEmpty(String text) {
        return text == null ? "" : text;
    }

    private LearningAssessmentItem createInterviewQuestion(Long planId, LearningPathStep step) {
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setPlanId(planId);
        item.setStepId(step.getId());
        item.setAbilityTagId(step.getAbilityTagId());
        item.setQuestionType("INTERVIEW");
        item.setQuestionText("请结合目标岗位，说明你如何在项目中应用「" + step.getAbilityName() + "」，并给出一个可验证的成果证据。");
        item.setReferenceAnswer("回答应包含业务场景、技术方案、关键实现、验证方式和证据链接。");
        item.setDifficultyLevel(mapPriorityToDifficulty(step.getPriority()));
        item.setSource("SYSTEM_TEMPLATE");
        item.setAssessmentStatus("PENDING");
        item.setIsDeleted(0);
        return item;
    }

    private LearningAssessmentItem createProjectReviewQuestion(Long planId) {
        LearningAssessmentItem item = new LearningAssessmentItem();
        item.setPlanId(planId);
        item.setQuestionType("PROJECT_REVIEW");
        item.setQuestionText("如果要把 Coolearn 的学习路线功能改造成当前人岗匹配系统的岗位差距学习路径，你会如何设计数据模型和证据回写？");
        item.setReferenceAnswer("回答应包含数据模型设计、学习路径生成逻辑、证据回写流程、与现有系统的集成方案。");
        item.setDifficultyLevel("HARD");
        item.setSource("SYSTEM_TEMPLATE");
        item.setAssessmentStatus("PENDING");
        item.setIsDeleted(0);
        return item;
    }

    private String mapPriorityToDifficulty(String priority) {
        if (priority == null) return "MEDIUM";
        switch (priority) {
            case "HIGH": return "HARD";
            case "MEDIUM": return "MEDIUM";
            case "LOW": return "EASY";
            default: return "MEDIUM";
        }
    }

    /** 本地确定性评分，避免外部模型拥塞导致测评无法完成。 */
    private AssessmentScore scoreAnswer(String answerText) {
        String text = answerText.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        int score = 20;
        if (text.length() >= 80) score += 15;
        if (text.length() >= 180) score += 10;
        if (containsAny(normalized, "场景", "项目", "业务", "需求")) score += 15;
        if (containsAny(normalized, "方案", "设计", "架构", "实现", "代码")) score += 15;
        if (containsAny(normalized, "验证", "测试", "指标", "结果", "效果", "监控")) score += 15;
        if (containsAny(normalized, "问题", "异常", "风险", "优化", "排查")) score += 10;
        boolean passed = score >= 60;
        return new AssessmentScore(score, passed,
                passed ? "已通过：回答包含可核验的实践说明，可确认能力提升。"
                        : "未通过：请补充项目或业务场景、技术实现、验证结果等具体内容后重新提交。");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private record AssessmentScore(int score, boolean passed, String feedback) {
    }
}
