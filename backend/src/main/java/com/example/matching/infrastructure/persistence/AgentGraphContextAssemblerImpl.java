package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.agent.dto.graph.AgentAbilityGapFact;
import com.example.matching.agent.dto.graph.AgentAbilityMatchFact;
import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.agent.dto.graph.AgentGraphEdge;
import com.example.matching.agent.dto.graph.AgentGraphNode;
import com.example.matching.agent.dto.graph.AgentPrerequisiteFact;
import com.example.matching.agent.dto.graph.AgentVerifiedEvidenceFact;
import com.example.matching.agent.service.AgentGraphContextAssembler;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 图谱上下文装配器实现。
 * <p>
 * 服务端为当前任务构建完整受限子图（节点/边/预计算事实/白名单/新鲜度），
 * 一次性放入 Agent 上下文。权威能力数据：person_ability_profile 优先，
 * 无可用融合画像时回退 emp_ability；证据只允许 VERIFIED 且归属当前员工。
 * 图谱（KgGraphNode/KgGraphEdge）仅用于前置关系、知识域与新鲜度判断，
 * 不得覆盖人工治理等级、融合画像等级、岗位要求、硬条件或最终匹配分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGraphContextAssemblerImpl implements AgentGraphContextAssembler {

    // ==================== 规模限制（方案第十章） ====================
    static final int MAX_POST_ABILITIES = 30;
    static final int MAX_EMP_ABILITIES = 30;
    static final int MAX_EVIDENCE_PER_ABILITY = 3;
    static final int MAX_PREREQUISITES = 50;
    static final int MAX_KNOWLEDGE_NODES = 20;
    static final int MAX_NODES = 120;
    static final int MAX_EDGES = 240;

    static final String STATUS_FRESH = "FRESH";
    static final String STATUS_STALE = "STALE";
    static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private static final String VERIFIED = "VERIFIED";
    private static final String EMP_ABILITY = "EMP_ABILITY";

    private final EmpEmployeeMapper empEmployeeMapper;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final EmployeeAbilityReadPort employeeAbilityReadPort;
    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final KgGraphNodeMapper graphNodeMapper;
    private final KgGraphEdgeMapper graphEdgeMapper;
    private final EmpVideoInterviewSessionMapper interviewSessionMapper;
    private final EmpVideoInterviewQuestionMapper interviewQuestionMapper;
    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ==================== 匹配分析子图 ====================

    @Override
    public AgentGraphContext buildForMatching(Long empId, Long postId) {
        try {
            EmpEmployee emp = empId == null ? null : empEmployeeMapper.selectById(empId);
            PostPost post = postId == null ? null : postPostMapper.selectById(postId);
            if (emp == null || post == null) {
                return unavailable("员工或岗位不存在: empId=" + empId + ", postId=" + postId);
            }
            return buildCoreGraph(emp, post, null, null);
        } catch (Exception e) {
            return failOpen(e, "buildForMatching");
        }
    }

    // ==================== 学习路径子图 ====================

    @Override
    public AgentGraphContext buildForLearningPath(Long empId, Long postId, Set<Long> gapTagIds) {
        try {
            EmpEmployee emp = empId == null ? null : empEmployeeMapper.selectById(empId);
            PostPost post = postId == null ? null : postPostMapper.selectById(postId);
            if (emp == null || post == null) {
                return unavailable("员工或岗位不存在: empId=" + empId + ", postId=" + postId);
            }
            AgentGraphContext ctx = buildCoreGraph(emp, post, null, gapTagIds);
            // 学习路径输出白名单 = 已验证缺口
            ctx.setAllowedAbilityTagIds(gapTagIds == null ? Set.of() : new LinkedHashSet<>(gapTagIds));
            return ctx;
        } catch (Exception e) {
            return failOpen(e, "buildForLearningPath");
        }
    }

    // ==================== 面试计划子图 ====================

    @Override
    public AgentGraphContext buildForInterviewPlan(Long sessionId, Long empId, Long postId, Set<Long> allowedTagIds) {
        try {
            EmpEmployee emp = empId == null ? null : empEmployeeMapper.selectById(empId);
            PostPost post = postId == null ? null : postPostMapper.selectById(postId);
            if (emp == null || post == null) {
                return unavailable("员工或岗位不存在: empId=" + empId + ", postId=" + postId);
            }
            AgentGraphContext ctx = buildCoreGraph(emp, post, sessionId, null);
            if (allowedTagIds != null) {
                ctx.setAllowedAbilityTagIds(new LinkedHashSet<>(allowedTagIds));
            }
            return ctx;
        } catch (Exception e) {
            return failOpen(e, "buildForInterviewPlan");
        }
    }

    // ==================== 面试观察子图 ====================

    @Override
    public AgentGraphContext buildForInterviewObservation(Long sessionId, Set<Long> allowedTagIds) {
        try {
            AgentGraphContext ctx = new AgentGraphContext();
            ctx.setRelationshipContract("PRECOMPUTED_FACTS_ONLY");
            ctx.setRelationTypes(List.of("HAS_ABILITY", "HAS_ABILITY_FACT", "REQUIRES", "SUPPORTED_BY",
                    "PREREQUISITE_OF", "BELONGS_TO_DOMAIN", "HAS_KNOWLEDGE_NODE",
                    "ASKED_IN", "ANSWERED_BY", "FOLLOWED_UP_BY", "LEARN_BY"));
            EmpVideoInterviewSession session = sessionId == null ? null
                    : interviewSessionMapper.selectById(sessionId);
            if (session == null) {
                return unavailable("面试会话不存在: sessionId=" + sessionId);
            }
            ctx.setGraphVersion("interview-session:" + sessionId);
            ctx.setRefreshedAt(session.getUpdatedTime() != null ? session.getUpdatedTime() : LocalDateTime.now());
            ctx.setStatus(STATUS_FRESH);

            // 会话节点
            ctx.getNodes().add(AgentGraphNode.of("INTERVIEW_SESSION:" + sessionId,
                    "INTERVIEW_SESSION", sessionId, session.getSessionName() != null
                            ? session.getSessionName() : "面试会话#" + sessionId));

            // 当前会话问题（含追问、回答引用）
            List<EmpVideoInterviewQuestion> questions = interviewQuestionMapper.selectList(
                    Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                            .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                            .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder));
            for (EmpVideoInterviewQuestion question : questions) {
                String questionKey = "INTERVIEW_QUESTION:" + question.getId();
                AgentGraphNode questionNode = AgentGraphNode.of(questionKey, "INTERVIEW_QUESTION",
                        question.getId(), question.getQuestionText());
                questionNode.getProperties().put("questionOrder", question.getQuestionOrder());
                questionNode.getProperties().put("difficulty", question.getDifficulty());
                questionNode.getProperties().put("expectedTagsJson", question.getExpectedTagsJson());
                ctx.getNodes().add(questionNode);
                ctx.getAllowedSourceRefs().add(SourceRefConstants.factRef(
                        SourceRefConstants.ENTITY_INTERVIEW_QUESTION, question.getId()));
                ctx.getEdges().add(AgentGraphEdge.of("INTERVIEW_SESSION:" + sessionId,
                        questionKey, "ASKED_IN"));

                // 问题关联能力标签
                Set<Long> expectedTagIds = parseExpectedTagIds(question.getExpectedTagsJson());
                for (Long tagId : expectedTagIds) {
                    ctx.getEdges().add(AgentGraphEdge.of(questionKey, "ABILITY:" + tagId, "ANSWERED_BY"));
                }

                // 追问
                List<InterviewFollowUpQuestion> followUps = followUpQuestionMapper.selectList(
                        Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                                .eq(InterviewFollowUpQuestion::getParentQuestionId, question.getId())
                                .eq(InterviewFollowUpQuestion::getIsDeleted, 0)
                                .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder));
                for (InterviewFollowUpQuestion followUp : followUps) {
                    String followUpKey = "INTERVIEW_FOLLOW_UP:" + followUp.getId();
                    AgentGraphNode followUpNode = AgentGraphNode.of(followUpKey, "INTERVIEW_FOLLOW_UP",
                            followUp.getId(), followUp.getQuestionText());
                    followUpNode.getProperties().put("followUpStatus", followUp.getFollowUpStatus());
                    followUpNode.getProperties().put("targetAbilityTagId", followUp.getTargetAbilityTagId());
                    ctx.getNodes().add(followUpNode);
                    ctx.getEdges().add(AgentGraphEdge.of(questionKey, followUpKey, "FOLLOWED_UP_BY"));
                    ctx.getAllowedSourceRefs().add(SourceRefConstants.factRef(
                            SourceRefConstants.ENTITY_INTERVIEW_FOLLOW_UP, followUp.getId()));
                }
            }

            ctx.setAllowedAbilityTagIds(allowedTagIds == null ? Set.of() : new LinkedHashSet<>(allowedTagIds));
            ctx.getAllowedSourceRefs().add(SourceRefConstants.factRef(
                    SourceRefConstants.ENTITY_INTERVIEW_SESSION, sessionId));
            // 观察子图不包含其他员工证据/其他会话/全局图谱节点/未审核证据
            return ctx;
        } catch (Exception e) {
            return failOpen(e, "buildForInterviewObservation");
        }
    }

    // ==================== 核心子图构建（匹配/学习路径/面试计划共用） ====================

    private AgentGraphContext buildCoreGraph(EmpEmployee emp, PostPost post,
                                             Long sessionId, Set<Long> gapTagIds) {
        AgentGraphContext ctx = new AgentGraphContext();
        ctx.setRelationshipContract("PRECOMPUTED_FACTS_ONLY");
        ctx.setRelationTypes(List.of("HAS_ABILITY", "HAS_ABILITY_FACT", "REQUIRES", "SUPPORTED_BY",
                "PREREQUISITE_OF", "BELONGS_TO_DOMAIN", "HAS_KNOWLEDGE_NODE",
                "ASKED_IN", "ANSWERED_BY", "FOLLOWED_UP_BY", "LEARN_BY"));

        // 1. 员工/岗位节点
        ctx.getNodes().add(AgentGraphNode.of("EMPLOYEE:" + emp.getId(), "EMPLOYEE",
                emp.getId(), emp.getRealName()));
        ctx.getNodes().add(AgentGraphNode.of("POST:" + post.getId(), "POST",
                post.getId(), post.getPostName()));
        if (sessionId != null) {
            ctx.getNodes().add(AgentGraphNode.of("INTERVIEW_SESSION:" + sessionId,
                    "INTERVIEW_SESSION", sessionId, "面试会话#" + sessionId));
        }

        // 2. 岗位能力模型（核心必填→必填→加分→权重降序→tagId 升序）
        List<PostAbilityModel> postAbilities = loadPostAbilities(post.getId());
        if (postAbilities.isEmpty()) {
            ctx.setStatus(STATUS_FRESH);
            ctx.setGraphVersion("post-model:" + post.getId());
            ctx.setRefreshedAt(LocalDateTime.now());
            return ctx;
        }
        Map<Long, AbilityTag> tagMap = loadTagMap(postAbilities);

        // 3. 正式员工能力（仅 emp_ability）
        Map<Long, EmpAbilityView> empAbilities = loadAuthoritativeAbilities(emp.getId());
        Map<String, EmpAbilityView> empAbilitiesByName = empAbilities.values().stream()
                .filter(v -> v.abilityName() != null && !v.abilityName().isBlank())
                .collect(Collectors.toMap(v -> normalizeName(v.abilityName()), v -> v, (a, b) -> a));
        Set<Long> empTagIds = new LinkedHashSet<>(empAbilities.keySet());
        if (gapTagIds != null) {
            empTagIds.retainAll(gapTagIds);
        }

        // 4. 能力节点 + 匹配/差距事实
        Set<String> abilityKeys = new LinkedHashSet<>();
        List<AgentAbilityMatchFact> matches = new ArrayList<>();
        List<AgentAbilityGapFact> gaps = new ArrayList<>();
        for (PostAbilityModel requirement : postAbilities) {
            Long tagId = requirement.getTagId();
            if (gapTagIds != null && tagId != null && !gapTagIds.contains(tagId)) {
                continue;
            }
            AbilityTag tag = tagMap.get(tagId);
            String abilityName = requirement.getAbilityName() != null
                    && !requirement.getAbilityName().isBlank()
                    ? requirement.getAbilityName()
                    : empAbilities.get(tagId) != null
                    && empAbilities.get(tagId).abilityName() != null
                    && !empAbilities.get(tagId).abilityName().isBlank()
                    ? empAbilities.get(tagId).abilityName()
                    : tag != null ? tag.getTagName() : null;
            if (abilityName == null || abilityName.isBlank()) {
                log.warn("跳过无有效名称的岗位能力图谱节点: postId={}, modelId={}, tagId={}",
                        requirement.getPostId(), requirement.getId(), requirement.getTagId());
                continue;
            }
            EmpAbilityView empView = tagId != null ? empAbilities.get(tagId) : empAbilitiesByName.get(normalizeName(abilityName));
            boolean required = Integer.valueOf(1).equals(requirement.getIsRequired());
            boolean core = Integer.valueOf(1).equals(requirement.getIsCore());
            int requiredLevel = requirement.getMinRequiredLevel() != null
                    ? requirement.getMinRequiredLevel() : 0;
            Integer empLevel = empView != null ? empView.level() : null;

            String matchState = matchState(empLevel, requiredLevel, required);
            AgentAbilityMatchFact match = AgentAbilityMatchFact.of(tagId, abilityName,
                    empLevel, requiredLevel, requirement.getWeight(), required, core, matchState);
            if (empView != null) {
                match.getSourceRefs().add("fact:EMP_ABILITY:" + empView.empAbilityId());
            }
            match.getSourceRefs().add("fact:POST_ABILITY_MODEL:" + requirement.getId());
            matches.add(match);

            // 差距只统计必填能力（加分项缺口不构成学习路径/面试目标）
            if (required && (empLevel == null || empLevel < requiredLevel)) {
                gaps.add(AgentAbilityGapFact.of(tagId, abilityName, empLevel, requiredLevel, true, core));
            }

            String abilityKey = tagId != null ? "ABILITY:" + tagId : "POST_ABILITY:" + requirement.getId();
            abilityKeys.add(abilityKey);
            AgentGraphNode abilityNode = AgentGraphNode.of(abilityKey, "ABILITY", tagId != null ? tagId : requirement.getId(), abilityName);
            abilityNode.getProperties().put("requiredLevel", requiredLevel);
            abilityNode.getProperties().put("employeeLevel", empLevel);
            abilityNode.getProperties().put("required", required);
            abilityNode.getProperties().put("core", core);
            abilityNode.getProperties().put("weight", requirement.getWeight());
            ctx.getNodes().add(abilityNode);
            ctx.getEdges().add(AgentGraphEdge.of("POST:" + post.getId(), abilityKey, "REQUIRES"));
            if (empView != null) {
                AgentGraphEdge hasAbility = AgentGraphEdge.of("EMPLOYEE:" + emp.getId(),
                        abilityKey, "HAS_ABILITY");
                hasAbility.getProperties().put("masteryLevel", empView.level());
                hasAbility.getProperties().put("source", empView.source());
                ctx.getEdges().add(hasAbility);
            }
        }

        // 岗位未要求但人员正式能力表中存在的能力也必须进入子图，且名称以正式能力表为准。
        for (EmpAbilityView empView : empAbilities.values()) {
            if (empView.empAbilityId() == null || empView.abilityName() == null || empView.abilityName().isBlank()) {
                continue;
            }
            if (empView.tagId() != null && abilityKeys.contains("ABILITY:" + empView.tagId())) {
                continue;
            }
            String nodeKey = empView.tagId() != null ? "ABILITY:" + empView.tagId() : "EMP_ABILITY:" + empView.empAbilityId();
            AgentGraphNode node = AgentGraphNode.of(nodeKey,
                    empView.tagId() != null ? "ABILITY" : "ABILITY_FACT",
                    empView.tagId() != null ? empView.tagId() : empView.empAbilityId(),
                    empView.abilityName().trim());
            node.getProperties().put("employeeOnly", true);
            node.getProperties().put("employeeAbilityId", empView.empAbilityId());
            node.getProperties().put("masteryLevel", empView.level());
            node.getProperties().put("source", empView.source());
            ctx.getNodes().add(node);
            AgentGraphEdge edge = AgentGraphEdge.of("EMPLOYEE:" + emp.getId(), nodeKey,
                    empView.tagId() != null ? "HAS_ABILITY" : "HAS_ABILITY_FACT");
            edge.getProperties().put("masteryLevel", empView.level());
            edge.getProperties().put("source", empView.source());
            edge.getSourceRefs().add("fact:EMP_ABILITY:" + empView.empAbilityId());
            ctx.getEdges().add(edge);
            if (empView.tagId() != null) {
                abilityKeys.add(nodeKey);
            }
        }

        // 5. 已验证员工证据（VERIFIED + EMP_ABILITY + 归属当前员工能力，每能力 ≤3）
        List<AgentVerifiedEvidenceFact> evidenceFacts = loadVerifiedEvidence(empAbilities.values());
        List<Long> evidenceIds = evidenceFacts.stream()
                .map(AgentVerifiedEvidenceFact::getEvidenceId).toList();
        Map<Long, List<AgentVerifiedEvidenceFact>> evidenceByTag = evidenceFacts.stream()
                .collect(Collectors.groupingBy(AgentVerifiedEvidenceFact::getAbilityTagId));
        for (Map.Entry<Long, List<AgentVerifiedEvidenceFact>> entry : evidenceByTag.entrySet()) {
            Long tagId = entry.getKey();
            if (!abilityKeys.contains("ABILITY:" + tagId)) {
                continue;
            }
            List<AgentVerifiedEvidenceFact> evidences = entry.getValue().stream()
                    .limit(MAX_EVIDENCE_PER_ABILITY).toList();
            for (AgentVerifiedEvidenceFact evidence : evidences) {
                String evidenceKey = "EVIDENCE:" + evidence.getEvidenceId();
                AgentGraphNode evidenceNode = AgentGraphNode.of(evidenceKey, "EVIDENCE",
                        evidence.getEvidenceId(), truncate(evidence.getEvidenceText(), 80));
                evidenceNode.getProperties().put("abilityTagId", tagId);
                evidenceNode.getProperties().put("reviewStatus", VERIFIED);
                ctx.getNodes().add(evidenceNode);
                AgentGraphEdge edge = AgentGraphEdge.of("ABILITY:" + tagId, evidenceKey, "SUPPORTED_BY");
                edge.getSourceRefs().add("fact:EVIDENCE:" + evidence.getEvidenceId());
                ctx.getEdges().add(edge);
                ctx.getAllowedSourceRefs().add("fact:EVIDENCE:" + evidence.getEvidenceId());
            }
        }

        // 6. 前置关系（图谱）+ 知识域/知识节点
        Map<String, Object> graphInfo = loadGraphRelations(abilityKeys, ctx);

        // 7. 白名单
        for (AgentAbilityMatchFact match : matches) {
            ctx.getAllowedAbilityTagIds().add(match.getAbilityTagId());
            ctx.getAllowedSourceRefs().addAll(match.getSourceRefs());
        }
        for (AgentVerifiedEvidenceFact evidence : evidenceFacts) {
            ctx.getAllowedSourceRefs().addAll(evidence.getSourceRefs());
        }

        // 8. 新鲜度
        applyFreshness(ctx, emp, post, postAbilities, graphInfo);

        // 9. 稳定排序 + 限额截断
        ctx.setAbilityMatches(matches);
        ctx.setGaps(gaps);
        ctx.setVerifiedEvidence(evidenceFacts);
        sortAndLimit(ctx);

        log.info("Agent 图谱子图构建完成: scenario=matching, empId={}, postId={}, status={}, "
                        + "nodes={}, edges={}, matches={}, gaps={}, evidence={}, prerequisites={}",
                emp.getId(), post.getId(), ctx.getStatus(), ctx.getNodes().size(), ctx.getEdges().size(),
                ctx.getAbilityMatches().size(), ctx.getGaps().size(),
                ctx.getVerifiedEvidence().size(), ctx.getPrerequisites().size());
        return ctx;
    }

    // ==================== 权威能力加载（方案第八章） ====================

    /**
     * 加载员工正式能力：统一经 EmployeeAbilityReadPort 读取 emp_ability。
     */
    Map<Long, EmpAbilityView> loadAuthoritativeAbilities(Long empId) {
        Map<Long, EmpAbilityView> result = new LinkedHashMap<>();
        List<com.example.matching.dto.matching.MatchingAbilitySnapshot> snapshots =
                employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(empId))
                        .getOrDefault(empId, List.of());
        // 同标签取最高等级（与匹配引擎一致）
        for (com.example.matching.dto.matching.MatchingAbilitySnapshot snapshot : snapshots) {
            // 标签是可选元数据；无标签能力以能力记录 ID 作为内部索引，名称仍来自正式能力表。
            if (snapshot == null || snapshot.abilityId() == null) {
                continue;
            }
            Long mapKey = snapshot.tagId() != null ? snapshot.tagId() : -snapshot.abilityId();
            EmpAbilityView existing = result.get(mapKey);
            int level = snapshot.level() != null ? snapshot.level() : 0;
            if (existing == null || level > (existing.level() != null ? existing.level() : 0)) {
                result.put(mapKey, new EmpAbilityView(
                        snapshot.tagId(), snapshot.abilityId(), snapshot.abilityName(), level,
                        snapshot.sourceType() != null ? snapshot.sourceType() : "EMP_ABILITY",
                        snapshot.confidence() != null ? snapshot.confidence().doubleValue() : null,
                        0));
            }
        }
        return result;
    }

    // ==================== 证据过滤（方案第九章） ====================

    private List<AgentVerifiedEvidenceFact> loadVerifiedEvidence(Collection<EmpAbilityView> empAbilities) {
        List<Long> empAbilityIds = empAbilities.stream()
                .map(EmpAbilityView::empAbilityId)
                .filter(Objects::nonNull)
                .toList();
        if (empAbilityIds.isEmpty()) {
            return List.of();
        }
        // isDeleted=0 + VERIFIED + targetType=EMP_ABILITY + targetRefId 属于当前员工能力
        List<ContestEvidenceItem> evidences = evidenceItemMapper.selectList(
                Wrappers.<ContestEvidenceItem>lambdaQuery()
                        .eq(ContestEvidenceItem::getIsDeleted, 0)
                        .eq(ContestEvidenceItem::getEvidenceStatus, VERIFIED)
                        .eq(ContestEvidenceItem::getTargetType, EMP_ABILITY)
                        .in(ContestEvidenceItem::getTargetRefId, empAbilityIds)
                        .orderByDesc(ContestEvidenceItem::getCredibilityScore));

        Map<Long, Long> abilityIdByEmpAbilityId = empAbilities.stream()
                .filter(v -> v.empAbilityId() != null)
                .collect(Collectors.toMap(EmpAbilityView::empAbilityId, EmpAbilityView::tagId, (a, b) -> a));

        List<AgentVerifiedEvidenceFact> facts = new ArrayList<>();
        for (ContestEvidenceItem evidence : evidences) {
            // 应用层双重过滤：即使查询条件被绕过，非 VERIFIED 证据也禁止进入子图
            if (!VERIFIED.equals(evidence.getEvidenceStatus())
                    || !EMP_ABILITY.equals(evidence.getTargetType())
                    || evidence.getIsDeleted() == null || evidence.getIsDeleted() != 0) {
                continue;
            }
            Long tagId = abilityIdByEmpAbilityId.get(evidence.getTargetRefId());
            if (tagId == null) {
                continue;
            }
            AgentVerifiedEvidenceFact fact = AgentVerifiedEvidenceFact.of(
                    evidence.getId(), evidence.getTargetRefId(), tagId,
                    evidence.getSourceText() != null ? evidence.getSourceText() : "");
            fact.getSourceRefs().add("fact:EVIDENCE:" + evidence.getId());
            facts.add(fact);
        }
        return facts;
    }

    // ==================== 前置关系与知识域（图谱表，只读关系） ====================

    private Map<String, Object> loadGraphRelations(Set<String> abilityKeys, AgentGraphContext ctx) {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            if (abilityKeys.isEmpty()) {
                info.put("refreshedAt", null);
                info.put("graphVersion", null);
                return info;
            }
            List<KgGraphNode> abilityNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, abilityKeys));
            if (abilityNodes.isEmpty()) {
                info.put("refreshedAt", null);
                info.put("graphVersion", null);
                return info;
            }
            LocalDateTime graphRefreshedAt = abilityNodes.stream()
                    .map(KgGraphNode::getUpdatedTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo).orElse(null);
            String graphVersion = abilityNodes.stream()
                    .map(n -> parseVersion(n.getMetadataJson()))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (graphRefreshedAt == null || abilityNodes.stream().anyMatch(n -> n.getUpdatedTime() == null)) {
                info.put("refreshedAt", null);
                info.put("graphVersion", null);
                return info;
            }
            info.put("refreshedAt", graphRefreshedAt);
            info.put("graphVersion", graphVersion);

            // 能力 → 知识域 → 知识节点 → 前置能力（与 KnowledgeGraphContextQueryService 同路径）
            List<KgGraphEdge> domainEdges = graphEdgeMapper.selectList(
                    Wrappers.<KgGraphEdge>lambdaQuery()
                            .in(KgGraphEdge::getSourceNodeKey, abilityKeys)
                            .eq(KgGraphEdge::getEdgeType, "BELONGS_TO_DOMAIN"));
            if (domainEdges.isEmpty()) {
                return info;
            }
            Map<String, List<KgGraphEdge>> domainsByAbility = domainEdges.stream()
                    .collect(Collectors.groupingBy(KgGraphEdge::getSourceNodeKey));
            Set<String> domainKeys = domainEdges.stream()
                    .map(KgGraphEdge::getTargetNodeKey).collect(Collectors.toSet());
            List<KgGraphEdge> domainKnowledgeEdges = graphEdgeMapper.selectList(
                    Wrappers.<KgGraphEdge>lambdaQuery()
                            .in(KgGraphEdge::getSourceNodeKey, domainKeys)
                            .eq(KgGraphEdge::getEdgeType, "HAS_KNOWLEDGE_NODE"));
            Map<String, List<String>> knowledgeByDomain = domainKnowledgeEdges.stream()
                    .collect(Collectors.groupingBy(KgGraphEdge::getSourceNodeKey,
                            Collectors.mapping(KgGraphEdge::getTargetNodeKey, Collectors.toList())));
            Map<String, List<String>> abilitiesByKnowledgeNode = new LinkedHashMap<>();
            for (String abilityKey : abilityKeys) {
                for (KgGraphEdge domainEdge : domainsByAbility.getOrDefault(abilityKey, List.of())) {
                    for (String knowledgeKey : knowledgeByDomain.getOrDefault(
                            domainEdge.getTargetNodeKey(), List.of())) {
                        abilitiesByKnowledgeNode.computeIfAbsent(knowledgeKey,
                                ignored -> new ArrayList<>()).add(abilityKey);
                        // 知识域/知识节点加入子图
                        addKnowledgeNodes(ctx, domainEdge.getTargetNodeKey(), knowledgeKey);
                    }
                }
            }
            if (abilitiesByKnowledgeNode.isEmpty()) {
                return info;
            }
            List<KgGraphEdge> prerequisiteEdges = graphEdgeMapper.selectList(
                    Wrappers.<KgGraphEdge>lambdaQuery()
                            .in(KgGraphEdge::getTargetNodeKey, abilitiesByKnowledgeNode.keySet())
                            .eq(KgGraphEdge::getEdgeType, "PREREQUISITE_OF"));
            if (prerequisiteEdges.isEmpty()) {
                return info;
            }
            Set<String> prereqKeys = prerequisiteEdges.stream()
                    .map(KgGraphEdge::getSourceNodeKey).collect(Collectors.toSet());
            List<KgGraphNode> prereqNodes = graphNodeMapper.selectList(
                    Wrappers.<KgGraphNode>lambdaQuery().in(KgGraphNode::getNodeKey, prereqKeys));
            Map<String, KgGraphNode> prereqNodeMap = prereqNodes.stream()
                    .collect(Collectors.toMap(KgGraphNode::getNodeKey, n -> n, (a, b) -> a));

            Map<Long, String> tagNameById = abilityTagMapper.selectList(null).stream()
                    .collect(Collectors.toMap(AbilityTag::getId, AbilityTag::getTagName, (a, b) -> a));

            int count = 0;
            for (KgGraphEdge edge : prerequisiteEdges) {
                if (count >= MAX_PREREQUISITES) {
                    break;
                }
                KgGraphNode prereqNode = prereqNodeMap.get(edge.getSourceNodeKey());
                if (prereqNode == null) {
                    continue;
                }
                for (String targetAbilityKey : abilitiesByKnowledgeNode.getOrDefault(
                        edge.getTargetNodeKey(), List.of())) {
                    if (count >= MAX_PREREQUISITES) {
                        break;
                    }
                    Long abilityTagId = refIdOf(targetAbilityKey);
                    Long prereqTagId = prereqNode.getRefId();
                    if (abilityTagId == null || prereqTagId == null || abilityTagId.equals(prereqTagId)) {
                        continue;
                    }
                    AgentPrerequisiteFact fact = AgentPrerequisiteFact.of(
                            abilityTagId, prereqTagId,
                            tagNameById.getOrDefault(abilityTagId, "能力#" + abilityTagId),
                            tagNameById.getOrDefault(prereqTagId, "能力#" + prereqTagId),
                            edge.getEdgeType());
                    List<String> refs = parseRefs(edge.getMetadataJson());
                    fact.setSourceRefs(refs);
                    ctx.getPrerequisites().add(fact);
                    ctx.getAllowedSourceRefs().addAll(refs);
                    // 前置能力节点/边（若在子图外仅补节点，不引岗位要求）
                    String prereqKey = "ABILITY:" + prereqTagId;
                    boolean alreadyThere = ctx.getNodes().stream()
                            .anyMatch(n -> prereqKey.equals(n.getNodeKey()));
                    if (!alreadyThere) {
                        ctx.getNodes().add(AgentGraphNode.of(prereqKey, "ABILITY", prereqTagId,
                                tagNameById.getOrDefault(prereqTagId, "能力#" + prereqTagId)));
                    }
                    AgentGraphEdge prereqEdge = AgentGraphEdge.of(prereqKey, targetAbilityKey, "PREREQUISITE_OF");
                    prereqEdge.setSourceRefs(refs);
                    ctx.getEdges().add(prereqEdge);
                    count++;
                }
            }
        } catch (Exception e) {
            log.warn("Agent 图谱前置关系加载失败（不影响主业务）: {}", e.getMessage());
            info.put("refreshedAt", null);
            info.put("graphVersion", null);
        }
        return info;
    }

    private void addKnowledgeNodes(AgentGraphContext ctx, String domainKey, String knowledgeKey) {
        long domainCount = ctx.getNodes().stream()
                .filter(n -> "KNOWLEDGE_DOMAIN".equals(n.getNodeType())).count();
        long knowledgeCount = ctx.getNodes().stream()
                .filter(n -> "KNOWLEDGE_NODE".equals(n.getNodeType())).count();
        if (domainCount < MAX_KNOWLEDGE_NODES && ctx.getNodes().stream()
                .noneMatch(n -> domainKey.equals(n.getNodeKey()))) {
            ctx.getNodes().add(AgentGraphNode.of(domainKey, "KNOWLEDGE_DOMAIN", refIdOf(domainKey),
                    domainKey));
        }
        if (knowledgeCount < MAX_KNOWLEDGE_NODES && ctx.getNodes().stream()
                .noneMatch(n -> knowledgeKey.equals(n.getNodeKey()))) {
            ctx.getNodes().add(AgentGraphNode.of(knowledgeKey, "KNOWLEDGE_NODE", refIdOf(knowledgeKey),
                    knowledgeKey));
            ctx.getEdges().add(AgentGraphEdge.of(domainKey, knowledgeKey, "HAS_KNOWLEDGE_NODE"));
        }
    }

    // ==================== 新鲜度（方案第十一章） ====================

    private void applyFreshness(AgentGraphContext ctx, EmpEmployee emp, PostPost post,
                                List<PostAbilityModel> postAbilities,
                                Map<String, Object> graphInfo) {
        LocalDateTime graphRefreshedAt = (LocalDateTime) graphInfo.get("refreshedAt");
        if (graphRefreshedAt == null) {
            ctx.setStatus(STATUS_UNAVAILABLE);
            ctx.setGraphVersion("graph-missing");
            log.warn("[agent.graph.freshness] 图谱节点缺失，图谱上下文不可用: empId={}, postId={}",
                    emp.getId(), post.getId());
            return;
        }
        ctx.setRefreshedAt(graphRefreshedAt);
        ctx.setGraphVersion(String.valueOf(graphInfo.get("graphVersion")));

        // 业务事实更新时间：员工/岗位/岗位模型/员工能力/画像/证据
        LocalDateTime factTime = latestFactTime(emp, post, postAbilities);
        if (factTime != null && graphRefreshedAt.isBefore(factTime)) {
            ctx.setStatus(STATUS_STALE);
            log.warn("[agent.graph.freshness] 图谱陈旧: refreshedAt={}, factTime={}, empId={}, postId={}",
                    graphRefreshedAt, factTime, emp.getId(), post.getId());
        } else {
            ctx.setStatus(STATUS_FRESH);
        }
    }

    private LocalDateTime latestFactTime(EmpEmployee emp, PostPost post,
                                          List<PostAbilityModel> models) {
        LocalDateTime latest = null;
        for (LocalDateTime time : List.of(
                emp.getUpdatedTime(), post.getUpdatedTime())) {
            if (time != null && (latest == null || time.isAfter(latest))) {
                latest = time;
            }
        }
        for (PostAbilityModel model : models) {
            if (model.getUpdatedTime() != null && (latest == null || model.getUpdatedTime().isAfter(latest))) {
                latest = model.getUpdatedTime();
            }
        }
        return latest;
    }

    // ==================== 排序与限额（方案第十章） ====================

    void sortAndLimit(AgentGraphContext ctx) {
        // 能力匹配事实排序：核心必填→必填→加分→权重降序→tagId 升序
        Comparator<AgentAbilityMatchFact> abilityComparator = Comparator
                .comparing(AgentAbilityMatchFact::isCore, Comparator.reverseOrder())
                .thenComparing(AgentAbilityMatchFact::isRequired, Comparator.reverseOrder())
                .thenComparing(f -> f.getWeight() != null ? f.getWeight() : BigDecimal.ZERO,
                        Comparator.reverseOrder())
                .thenComparing(AgentAbilityMatchFact::getAbilityTagId,
                        Comparator.nullsLast(Long::compareTo));
        List<AgentAbilityMatchFact> sortedMatches = new ArrayList<>(ctx.getAbilityMatches());
        sortedMatches.sort(abilityComparator);
        ctx.setAbilityMatches(sortedMatches);

        // 节点/边按 nodeKey 稳定排序
        List<AgentGraphNode> sortedNodes = new ArrayList<>(ctx.getNodes());
        sortedNodes.sort(Comparator.comparing(AgentGraphNode::getNodeKey,
                Comparator.nullsLast(String::compareTo)));
        ctx.setNodes(sortedNodes);
        List<AgentGraphEdge> sortedEdges = new ArrayList<>(ctx.getEdges());
        sortedEdges.sort(Comparator.comparing((AgentGraphEdge e) -> e.getSourceNodeKey(),
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(e -> e.getTargetNodeKey(), Comparator.nullsLast(String::compareTo))
                .thenComparing(AgentGraphEdge::getEdgeType, Comparator.nullsLast(String::compareTo)));
        ctx.setEdges(sortedEdges);

        // 限额：优先保留核心必填 → 差距能力 → 高权重能力，然后重建边
        if (ctx.getNodes().size() > MAX_NODES || ctx.getEdges().size() > MAX_EDGES) {
            truncateGraph(ctx);
        }
    }

    private void truncateGraph(AgentGraphContext ctx) {
        int beforeNodes = ctx.getNodes().size();
        int beforeEdges = ctx.getEdges().size();

        // 1. 保留固定锚点节点（EMPLOYEE/POST/INTERVIEW_SESSION）
        List<AgentGraphNode> anchors = ctx.getNodes().stream()
                .filter(n -> n.getNodeType().equals("EMPLOYEE")
                        || n.getNodeType().equals("POST")
                        || n.getNodeType().equals("INTERVIEW_SESSION"))
                .collect(Collectors.toList());
        // 2. 能力节点：核心必填 → 必填 → 权重降序 → tagId 升序
        List<AgentGraphNode> abilityNodes = ctx.getNodes().stream()
                .filter(n -> "ABILITY".equals(n.getNodeType()))
                .sorted(Comparator
                        .comparing((AgentGraphNode n) -> boolProp(n, "core"), Comparator.reverseOrder())
                        .thenComparing(n -> boolProp(n, "required"), Comparator.reverseOrder())
                        .thenComparing(n -> numProp(n, "weight"), Comparator.reverseOrder())
                        .thenComparing(AgentGraphNode::getNodeKey,
                                Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        // 3. 证据/知识/问题节点
        List<AgentGraphNode> others = ctx.getNodes().stream()
                .filter(n -> !"ABILITY".equals(n.getNodeType()))
                .filter(n -> !"EMPLOYEE".equals(n.getNodeType()))
                .filter(n -> !"POST".equals(n.getNodeType()))
                .filter(n -> !"INTERVIEW_SESSION".equals(n.getNodeType()))
                .collect(Collectors.toList());

        int remaining = MAX_NODES - anchors.size();
        List<AgentGraphNode> kept = new ArrayList<>(anchors);
        int abilitySlot = Math.max(0, Math.min(abilityNodes.size(), remaining));
        kept.addAll(abilityNodes.subList(0, abilitySlot));
        remaining -= abilitySlot;
        if (remaining > 0 && !others.isEmpty()) {
            kept.addAll(others.subList(0, Math.min(others.size(), remaining)));
        }

        Set<String> keptKeys = kept.stream().map(AgentGraphNode::getNodeKey)
                .collect(Collectors.toSet());
        List<AgentGraphEdge> keptEdges = ctx.getEdges().stream()
                .filter(e -> keptKeys.contains(e.getSourceNodeKey())
                        && keptKeys.contains(e.getTargetNodeKey()))
                .collect(Collectors.toList());
        // 边超限时优先保留关系边（REQUIRES/HAS_ABILITY/PREREQUISITE_OF）
        if (keptEdges.size() > MAX_EDGES) {
            Comparator<AgentGraphEdge> edgePriority = Comparator
                    .comparing((AgentGraphEdge e) -> edgeRank(e.getEdgeType()))
                    .thenComparing(AgentGraphEdge::getSourceNodeKey, Comparator.nullsLast(String::compareTo));
            keptEdges.sort(edgePriority);
            keptEdges = new ArrayList<>(keptEdges.subList(0, MAX_EDGES));
        }

        ctx.setNodes(kept);
        ctx.setEdges(keptEdges);
        meterRegistry.counter("agent.graph.assembler.truncated",
                "beforeNodes", String.valueOf(beforeNodes),
                "afterNodes", String.valueOf(ctx.getNodes().size()),
                "beforeEdges", String.valueOf(beforeEdges),
                "afterEdges", String.valueOf(ctx.getEdges().size())).increment();
        log.warn("[agent.graph.assembler] 图谱子图超限截断: nodes {}->{}, edges {}->{}",
                beforeNodes, ctx.getNodes().size(), beforeEdges, ctx.getEdges().size());
    }

    private int edgeRank(String edgeType) {
        return switch (edgeType == null ? "" : edgeType) {
            case "REQUIRES" -> 0;
            case "HAS_ABILITY" -> 1;
            case "PREREQUISITE_OF" -> 2;
            case "SUPPORTED_BY" -> 3;
            default -> 4;
        };
    }

    private boolean boolProp(AgentGraphNode node, String key) {
        Object value = node.getProperties().get(key);
        return Boolean.TRUE.equals(value)
                || Integer.valueOf(1).equals(value);
    }

    private BigDecimal numProp(AgentGraphNode node, String key) {
        Object value = node.getProperties().get(key);
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    // ==================== 辅助 ====================

    private List<PostAbilityModel> loadPostAbilities(Long postId) {
        List<PostAbilityModel> models = new ArrayList<>(postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getIsDeleted, 0)));
        models.sort(Comparator
                .comparing((PostAbilityModel m) -> Integer.valueOf(1).equals(m.getIsCore()),
                        Comparator.reverseOrder())
                .thenComparing(m -> Integer.valueOf(1).equals(m.getIsRequired()),
                        Comparator.reverseOrder())
                .thenComparing(m -> m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO,
                        Comparator.reverseOrder())
                .thenComparing(PostAbilityModel::getTagId, Comparator.nullsLast(Long::compareTo)));
        if (models.size() > MAX_POST_ABILITIES) {
            log.warn("[agent.graph.assembler] 岗位能力节点超限截断: postId={}, size={}->{}",
                    postId, models.size(), MAX_POST_ABILITIES);
            meterRegistry.counter("agent.graph.assembler.truncated",
                    "reason", "post_abilities").increment();
            return new ArrayList<>(models.subList(0, MAX_POST_ABILITIES));
        }
        return models;
    }

    private Map<Long, AbilityTag> loadTagMap(List<PostAbilityModel> models) {
        Set<Long> tagIds = models.stream().map(PostAbilityModel::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        return abilityTagMapper.selectList(
                        Wrappers.<AbilityTag>lambdaQuery().in(AbilityTag::getId, tagIds))
                .stream().collect(Collectors.toMap(AbilityTag::getId, t -> t, (a, b) -> a));
    }

    private String normalizeName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String matchState(Integer empLevel, int requiredLevel, boolean required) {
        if (!required) {
            return "BONUS";
        }
        if (empLevel == null || empLevel <= 0) {
            return "MISSING";
        }
        return empLevel >= requiredLevel ? "SATISFIED" : "LEVEL_GAP";
    }

    private Set<Long> parseExpectedTagIds(String expectedTagsJson) {
        if (expectedTagsJson == null || expectedTagsJson.isBlank()) {
            return Set.of();
        }
        try {
            List<?> tags = objectMapper.readValue(expectedTagsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<?>>() {
                    });
            Set<Long> ids = new LinkedHashSet<>();
            for (Object tag : tags) {
                Object id = tag instanceof Map<?, ?> map ? map.get("tagId") : tag;
                if (id instanceof Number n) {
                    ids.add(n.longValue());
                } else if (id instanceof String s) {
                    try {
                        ids.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed tag IDs.
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("解析题目 expectedTagsJson 失败: {}", e.getMessage());
            return Set.of();
        }
    }

    private String parseVersion(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(metadataJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            Object version = meta.get("graphVersion");
            return version != null ? String.valueOf(version) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseRefs(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(metadataJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            Object refs = meta.get("sourceRefs");
            if (refs instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Long refIdOf(String nodeKey) {
        if (nodeKey == null) {
            return null;
        }
        int idx = nodeKey.indexOf(':');
        if (idx < 0) {
            return null;
        }
        try {
            return Long.parseLong(nodeKey.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private AgentGraphContext unavailable(String reason) {
        AgentGraphContext ctx = new AgentGraphContext();
        ctx.setStatus(STATUS_UNAVAILABLE);
        ctx.setGraphVersion("unavailable");
        ctx.setRefreshedAt(LocalDateTime.now());
        log.warn("[agent.graph.assembler] 图谱上下文不可用: {}", reason);
        meterRegistry.counter("agent.graph.assembler.unavailable").increment();
        return ctx;
    }

    private AgentGraphContext failOpen(Exception e, String method) {
        log.warn("[agent.graph.assembler] {} 失败，返回 UNAVAILABLE（不阻断主业务）: {}",
                method, e.getMessage(), e);
        meterRegistry.counter("agent.graph.assembler.failures",
                "method", method).increment();
        return unavailable(e.getMessage());
    }

    /** 权威员工能力视图 */
    record EmpAbilityView(Long tagId, Long empAbilityId, String abilityName, Integer level, String source,
                          Double credibility, int evidenceCount) {
    }
}
