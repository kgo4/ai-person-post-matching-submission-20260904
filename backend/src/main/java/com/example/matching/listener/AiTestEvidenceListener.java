package com.example.matching.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.assessment.impl.AiTestAbilityLevelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 测试评估完成 -> 能力评估工作流测试证据保存监听器
 * <p>
 * 工作流测试评分完成后，将测试结果（题目、答案、评分、观察等级）保存为
 * AI_TEST 证据 Claim（COLLECTED + DISPLAY_ONLY），并按能力聚合分组，
 * 推进工作流到 TEST_EVIDENCE_READY。不直接生成正式能力。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTestEvidenceListener {

    private final EmpAiTestMapper empAiTestMapper;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final AiTestAbilityLevelResolver abilityLevelResolver;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final ObjectMapper objectMapper;

    /**
     * AI 测试评估完成 -> 保存测试证据并推进工作流。
     * <p>
     * 评分在 MQ 消费线程执行（无活动事务），若仅用 {@code AFTER_COMMIT} 且不开启
     * {@code fallbackExecution}，事件会被静默丢弃，工作流将永久卡在 TEST_EVALUATING。
     * 这里开启 {@code fallbackExecution = true}：无事务时同步执行，有事务时提交后执行；
     * 方法自身声明 REQUIRES_NEW 事务，保证证据保存 + Outbox 事件发布原子提交
     * （Spring 6.2 起 @TransactionalEventListener 方法上的 @Transactional 仅允许
     * REQUIRES_NEW 或 NOT_SUPPORTED）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAiTestEvaluated(com.example.matching.event.AiTestEvaluatedEvent event) {
        if (event.workflowId() == null) {
            return;
        }
        try {
            EmpAiTest test = empAiTestMapper.selectById(event.testId());
            if (test == null || test.getStatus() != 2) {
                log.warn("测试记录不存在或未完成，跳过证据保存: testId={}", event.testId());
                return;
            }
            // A completed evaluation may legitimately have insufficient evidence. Do not
            // let the resolver turn missing score/level into a fabricated L1 Claim.
            if (test.getScore() == null || test.getMasteryLevel() == null) {
                log.info("AI测试证据不足，跳过Claim保存但推进阶段: testId={}", event.testId());
                publishEvaluationSucceeded(event, test);
                return;
            }
            List<PersonAbilityClaim> claims = buildTestClaims(test);
            if (claims.isEmpty()) {
                log.warn("测试无能力证据，跳过保存: testId={}", event.testId());
                // 测试评分本身成功：阶段运行仍推进成功，工作流由协调器按转换表推进
                publishEvaluationSucceeded(event, test);
                return;
            }
            // 创建/复用 AI_TEST_EVALUATION 阶段运行
            String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                    event.workflowId().toString(), "AI_TEST_EVALUATION", String.valueOf(event.testId()));
            PersonCapabilityStageRun stageRun = workflowService.createStageRun(
                    event.workflowId(), "AI_TEST_EVALUATION", hash,
                    "{\"testId\":" + event.testId() + "}", "AI_TEST", event.testId());
            int saved = evidenceCollectionService.saveTestClaims(
                    event.workflowId(), stageRun.getId(), event.empId(), claims, null);
            if (saved > 0) {
                evidenceCollectionService.groupClaimsByAbility(event.workflowId(), event.empId());
            }
            // 不再直接推进工作流：发布生命周期事件，由协调器推进 TEST_EVALUATING -> TEST_EVIDENCE_READY
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                    event.workflowId(), stageRun.getId(), "AI_TEST_EVALUATION", "AI_TEST", event.testId()));
            log.info("测试证据已保存并发布生命周期事件: testId={}, workflowId={}, saved={}",
                    event.testId(), event.workflowId(), saved);
        } catch (Exception e) {
            // 证据保存失败不影响测试本身，可由前端手动重试
            log.error("测试证据保存失败: testId={}, error={}", event.testId(), e.getMessage(), e);
        }
    }

    /**
     * 测试评分成功但无证据：直接发布阶段成功事件（工作流仍推进到 TEST_EVIDENCE_READY）。
     */
    private void publishEvaluationSucceeded(com.example.matching.event.AiTestEvaluatedEvent event, EmpAiTest test) {
        try {
            String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                    event.workflowId().toString(), "AI_TEST_EVALUATION", String.valueOf(event.testId()));
            PersonCapabilityStageRun stageRun = workflowService.createStageRun(
                    event.workflowId(), "AI_TEST_EVALUATION", hash,
                    "{\"testId\":" + event.testId() + "}", "AI_TEST", event.testId());
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                    event.workflowId(), stageRun.getId(), "AI_TEST_EVALUATION", "AI_TEST", event.testId()));
        } catch (Exception e) {
            log.warn("发布测试评估成功事件失败: testId={}, error={}", event.testId(), e.getMessage());
        }
    }

    /**
     * 构建测试证据 Claim：仅主 Claim（测试能力的得分与掌握等级）。
     * <p>
     * 模型在评分中新发现的能力（discoveredAbilities）不自动保存为测试证据，
     * 避免模型新主张绕过"简历主张优先验证"的约束进入融合；新发现能力
     * 由标签治理/人工确认流程独立处理。
     */
    private List<PersonAbilityClaim> buildTestClaims(EmpAiTest test) {
        List<PersonAbilityClaim> claims = new ArrayList<>();
        // 逐项归并：按简历能力组核验每能力等级（确定性，不依赖 AI 多能力结构化输出）
        List<PersonAbilityClaimGroup> groups = test.getWorkflowId() != null
                ? claimGroupMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, test.getWorkflowId()))
                : List.of();
        List<AiTestAbilityLevelResolver.ResolvedAbility> resolved = abilityLevelResolver.resolve(
                test.getQuestions(), test.getAiEvaluation(), groups, test.getMasteryLevel());
        for (AiTestAbilityLevelResolver.ResolvedAbility ability : resolved) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setAbilityName(ability.abilityName());
            claim.setNormalizedAbilityName(ability.abilityName());
            claim.setClaimedLevel(ability.level());
            claim.setEvidenceText(ability.evidenceText());
            claim.setSourceRefId(test.getId());
            claim.setConfidenceScore(scoreToConfidence(test.getScore()));
            claim.setSourceRefsJson(toJson(ability.questionIndexes().stream()
                    .map(i -> "source:AI_TEST:" + test.getId() + ":Q" + (i + 1))
                    .toList()));
            claim.setTagId(ability.tagId());
            claims.add(claim);
        }
        // 未知标签：题目绑定但无法映射到 scope 内 Claim Group 的标签，保存为未归类观察（不进入画像）
        claims.addAll(buildUnclassifiedObservations(test, groups));
        // 回退：无逐项覆盖时（题目无 tagId / 无能力组）保留整体主 Claim
        if (claims.isEmpty()) {
            String evidenceText = buildEvidenceText(test);
            if (test.getAbilityTagName() != null && test.getMasteryLevel() != null) {
                PersonAbilityClaim main = new PersonAbilityClaim();
                main.setAbilityName(test.getAbilityTagName());
                main.setNormalizedAbilityName(test.getAbilityTagName());
                main.setClaimedLevel(test.getMasteryLevel());
                main.setEvidenceText(evidenceText);
                main.setSourceRefId(test.getId());
                main.setConfidenceScore(scoreToConfidence(test.getScore()));
                main.setSourceRefsJson("[\"source:AI_TEST:" + test.getId() + "\"]");
                main.setTagId(test.getAbilityTagId());
                claims.add(main);
            }
        }
        return claims;
    }

    private String toJson(List<String> refs) {
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 题目绑定但无法映射到 scope 内 Claim Group 的标签。
     * <p>
     * ⚠️ 历史行为：为这些标签生成一条可见的能力主张（能力名形如「未归类能力#123」）。
     * 问题是该名称是**标签体系对不上的技术细节**，会被当成一条真实能力建组并展示在界面上，
     * 属于脏数据 —— 而它的语义只是"测试题绑的标签不在本次评估范围内"。
     * <p>
     * 现在：**不再生成可见 claim**，仅记录 WARN 日志留痕。
     * <ul>
     *   <li>对评估结果无影响：该来源本就不会被计为一次有效核验（Harness 会排除）；</li>
     *   <li>对已有数据无影响：存量 claim 由独立的数据清理脚本处理。</li>
     * </ul>
     */
    private List<PersonAbilityClaim> buildUnclassifiedObservations(EmpAiTest test,
                                                                   List<PersonAbilityClaimGroup> groups) {
        java.util.Set<Long> knownTagIds = new java.util.HashSet<>();
        for (PersonAbilityClaimGroup group : groups) {
            if (group.getCanonicalTagId() != null) {
                knownTagIds.add(group.getCanonicalTagId());
            }
        }
        List<PersonAbilityClaim> result = new ArrayList<>();
        for (Long tagId : parseQuestionTagIds(test.getQuestions())) {
            if (knownTagIds.contains(tagId)) {
                continue;
            }
            // 不再生成可见 claim（避免「未归类能力#123」这类技术细节被当作真实能力展示）。
            // 该标签不影响评估结论：它既不能作为有效核验，也不参与等级计算。
            log.warn("AI测试题目绑定的标签不在评估范围内，已忽略: testId={}, tagId={}", test.getId(), tagId);
        }
        return result;
    }

    private java.util.Set<Long> parseQuestionTagIds(String questionsJson) {
        java.util.Set<Long> tagIds = new java.util.LinkedHashSet<>();
        if (questionsJson == null || questionsJson.isBlank()) {
            return tagIds;
        }
        try {
            List<Map<String, Object>> questions = objectMapper.readValue(questionsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> q : questions) {
                if (q.get("tagId") instanceof Number n) {
                    tagIds.add(n.longValue());
                }
            }
        } catch (Exception e) {
            log.debug("解析测试题目 tagId 失败（忽略）: error={}", e.getMessage());
        }
        return tagIds;
    }

    private String buildEvidenceText(EmpAiTest test) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI测试评分结果: 得分=").append(test.getScore()).append(", 掌握等级=L").append(test.getMasteryLevel());
        if (test.getQuestions() != null && !test.getQuestions().isBlank()) {
            sb.append("\n题目: ").append(truncate(test.getQuestions(), 600));
        }
        if (test.getAnswers() != null && !test.getAnswers().isBlank()) {
            sb.append("\n作答: ").append(truncate(test.getAnswers(), 600));
        }
        if (test.getAiEvaluation() != null && !test.getAiEvaluation().isBlank()) {
            sb.append("\n评分结果: ").append(truncate(test.getAiEvaluation(), 600));
        }
        return sb.toString();
    }

    private BigDecimal scoreToConfidence(BigDecimal score) {
        if (score == null) {
            return BigDecimal.valueOf(60);
        }
        return score;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
