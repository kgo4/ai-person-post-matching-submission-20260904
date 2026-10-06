package com.example.matching.service.assessment.report;

import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.application.employee.AiTestApiFacade;
import com.example.matching.application.employee.EmpAbilityApiFacade;
import com.example.matching.application.employee.ResumeParseApiFacade;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;
import com.example.matching.dto.employee.api.AiTestResponse;
import com.example.matching.dto.employee.api.ResumeParseResponse;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.employee.VideoInterviewService;
import com.example.matching.service.interview.persistence.InterviewSessionRepository;
import com.example.matching.service.system.SourceWeightResolver;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 全方位评估报告的事实聚合器。
 *
 * <p>按 empId 汇聚四类数据 + 能力画像，产出 {@link ComprehensiveAssessmentContext}：
 * 简历解析、AI 测试、AI 面试、人员最终等级决策。
 *
 * <p><b>两条硬约束</b>：
 * <ol>
 *   <li><b>零 AI 调用</b> —— 只读已落库的结构化事实。刻意不调用
 *       {@code AIInterviewAgent.generateCompetencyReport}（它会重新触发面试 AI 报告生成），
 *       而是直接读会话的 {@code overallScore} 与已落库的观察项，避免重复消耗模型。</li>
 *   <li><b>不臆造</b> —— 任一数据源缺失就留空/置 null，由算分引擎判定该维度
 *       {@code insufficient}，不得用 0 分或编造值填充。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprehensiveAssessmentContextAssembler {

    /** emp_resume_parse.status：2 表示解析成功 */
    private static final int RESUME_STATUS_SUCCESS = 2;

    private final ResumeParseApiFacade resumeParseApiFacade;
    private final AiTestApiFacade aiTestApiFacade;
    private final VideoInterviewService videoInterviewService;
    private final InterviewSessionRepository interviewSessionRepository;
    private final AbilityLevelConfirmationService levelConfirmationService;
    private final EmpAbilityApiFacade empAbilityApiFacade;
    private final SourceWeightResolver sourceWeightResolver;
    private final ObjectMapper objectMapper;

    /**
     * 聚合该员工的全方位评估事实包。
     *
     * @param empId 员工 ID
     * @return 事实包；任一数据源缺失只留空，不抛异常（只有 empId 非法才返回空包）
     */
    public ComprehensiveAssessmentContext assemble(Long empId) {
        ComprehensiveAssessmentContext context = new ComprehensiveAssessmentContext();
        context.setEmpId(empId);
        if (empId == null) {
            return context;
        }

        // 能力画像：同时提供 empName 与 tagId → 能力名的映射（供等级决策补名）
        Map<Long, String> abilityNameByTag = new HashMap<>();
        collectProfile(empId, context, abilityNameByTag);

        collectResume(empId, context);
        collectTests(empId, context);
        collectInterview(empId, context);
        collectDecisions(empId, context, abilityNameByTag);
        collectSourceWeights(context);

        return context;
    }

    /**
     * 各部分（来源）权重：只读既有「来源权重配置」（`source_weight_config`）。
     *
     * <p><b>本报告不新建权重体系</b> —— 这里仅把既有配置读出来供报告展示各部分占比；
     * 证据加权与最终等级的判定口径仍由 {@link SourceWeightResolver} 唯一负责。
     */
    private void collectSourceWeights(ComprehensiveAssessmentContext context) {
        try {
            List<ComprehensiveAssessmentContext.SourceWeightFact> weights = new ArrayList<>();
            addSourceWeight(weights, AbilitySourceType.RESUME_PARSE, "简历解析");
            addSourceWeight(weights, AbilitySourceType.AI_TEST, "AI 测试");
            addSourceWeight(weights, AbilitySourceType.AI_INTERVIEW, "AI 面试");
            context.setSourceWeights(weights);
        } catch (Exception e) {
            log.warn("读取来源权重配置失败，报告将不展示各部分权重: empId={}", context.getEmpId(), e);
        }
    }

    private void addSourceWeight(List<ComprehensiveAssessmentContext.SourceWeightFact> target,
                                 String sourceType, String sourceLabel) {
        BigDecimal weight = sourceWeightResolver.resolveEffectiveWeight(sourceType);
        if (weight != null) {
            target.add(new ComprehensiveAssessmentContext.SourceWeightFact(sourceType, sourceLabel, weight));
        }
    }

    /** 能力画像：已确立能力清单 + 员工姓名 */
    private void collectProfile(Long empId, ComprehensiveAssessmentContext context,
                                Map<Long, String> abilityNameByTag) {
        try {
            EmpAbilityProfileVO profile = empAbilityApiFacade.getProfile(empId);
            if (profile == null) {
                return;
            }
            context.setEmpName(profile.getRealName());
            List<EmpAbilityProfileVO.AbilityDetail> details = profile.getAbilityDetails();
            if (details == null || details.isEmpty()) {
                return;
            }
            List<ComprehensiveAssessmentContext.AbilityFact> abilities = new ArrayList<>();
            for (EmpAbilityProfileVO.AbilityDetail detail : details) {
                abilities.add(new ComprehensiveAssessmentContext.AbilityFact(
                        detail.getTagId(), detail.getTagName(), detail.getTagCategory(), detail.getMasteryLevel()));
                if (detail.getTagId() != null && StringUtils.hasText(detail.getTagName())) {
                    abilityNameByTag.putIfAbsent(detail.getTagId(), detail.getTagName());
                }
            }
            context.setAbilities(abilities);
        } catch (Exception e) {
            log.warn("聚合能力画像失败，该维度将判为数据不足: empId={}", empId, e);
        }
    }

    /**
     * 简历解析：取「解析成功且有 AI 分析结果」的最新一条，
     * 反序列化 aiAnalysisResult 得到学历/工作年限/当前职位与能力声明数。
     */
    private void collectResume(Long empId, ComprehensiveAssessmentContext context) {
        try {
            List<ResumeParseResponse> parses = resumeParseApiFacade.listByEmpId(empId);
            if (parses == null || parses.isEmpty()) {
                return;
            }
            ResumeParseResponse latest = parses.stream()
                    .filter(p -> StringUtils.hasText(p.aiAnalysisResult()))
                    .max(Comparator.comparing(
                            p -> p.createdTime() == null ? java.time.LocalDateTime.MIN : p.createdTime(),
                            Comparator.naturalOrder()))
                    .orElse(null);
            if (latest == null) {
                return;
            }
            String statusText = latest.status() != null && latest.status() == RESUME_STATUS_SUCCESS
                    ? "SUCCESS" : "INCOMPLETE";
            PersonAbilityExtractionResult parsed = objectMapper.readValue(
                    latest.aiAnalysisResult(), PersonAbilityExtractionResult.class);
            PersonAbilityExtractionResult.BasicInfo basic =
                    parsed == null ? null : parsed.getBasicInfo();
            int claimCount = parsed == null ? 0 : parsed.getClaimCount();
            context.setResume(new ComprehensiveAssessmentContext.ResumeFact(
                    basic == null ? null : basic.getDegree(),
                    basic == null ? null : basic.getYearsOfWork(),
                    basic == null ? null : basic.getCurrentTitle(),
                    claimCount,
                    statusText));
        } catch (Exception e) {
            log.warn("聚合简历解析失败，该维度将判为数据不足: empId={}", empId, e);
        }
    }

    /** AI 测试：只取已完成评分（score 非空）的测试记录 */
    private void collectTests(Long empId, ComprehensiveAssessmentContext context) {
        try {
            List<AiTestResponse> tests = aiTestApiFacade.listByEmpId(empId);
            if (tests == null || tests.isEmpty()) {
                return;
            }
            List<ComprehensiveAssessmentContext.TestFact> facts = tests.stream()
                    .filter(t -> t.score() != null)
                    .map(t -> new ComprehensiveAssessmentContext.TestFact(
                            t.abilityTagId(), t.abilityTagName(), t.score(), t.masteryLevel()))
                    .toList();
            context.setTests(new ArrayList<>(facts));
        } catch (Exception e) {
            log.warn("聚合 AI 测试失败，该维度将判为数据不足: empId={}", empId, e);
        }
    }

    /**
     * AI 面试：取最新会话的 overallScore 与已落库观察项。
     * 刻意不调用 generateCompetencyReport —— 那会重新触发一次面试 AI 报告生成。
     */
    private void collectInterview(Long empId, ComprehensiveAssessmentContext context) {
        try {
            List<EmpVideoInterviewSession> sessions = videoInterviewService.listByEmpId(empId);
            if (sessions == null || sessions.isEmpty()) {
                return;
            }
            EmpVideoInterviewSession latest = sessions.stream()
                    .max(Comparator.comparing(s -> s.getId() == null ? 0L : s.getId()))
                    .orElse(null);
            if (latest == null) {
                return;
            }
            List<InterviewAbilityObservation> observations =
                    interviewSessionRepository.findObservationsBySession(latest.getId());
            List<String> riskSignals = new ArrayList<>();
            List<String> conclusions = new ArrayList<>();
            for (InterviewAbilityObservation observation : observations) {
                riskSignals.addAll(parseStringArray(observation.getRiskSignalsJson()));
                if (StringUtils.hasText(observation.getInterviewConclusion())) {
                    conclusions.add(observation.getInterviewConclusion());
                }
            }
            context.setInterview(new ComprehensiveAssessmentContext.InterviewFact(
                    latest.getOverallScore(),
                    observations.size(),
                    new ArrayList<>(new LinkedHashSet<>(riskSignals)),
                    new ArrayList<>(new LinkedHashSet<>(conclusions))));
        } catch (Exception e) {
            log.warn("聚合 AI 面试失败，该维度将判为数据不足: empId={}", empId, e);
        }
    }

    /** 人员最终等级决策（同一能力只保留最新一条，去重逻辑在 service 内） */
    private void collectDecisions(Long empId, ComprehensiveAssessmentContext context,
                                  Map<Long, String> abilityNameByTag) {
        try {
            List<PersonAbilityLevelDecision> decisions = levelConfirmationService.listDecisionsByEmp(empId);
            if (decisions == null || decisions.isEmpty()) {
                return;
            }
            List<ComprehensiveAssessmentContext.DecisionFact> facts = decisions.stream()
                    .map(d -> new ComprehensiveAssessmentContext.DecisionFact(
                            d.getTagId(),
                            resolveAbilityName(d.getTagId(), abilityNameByTag),
                            d.getFinalLevel(),
                            d.getFinalConfidence(),
                            d.getDecisionStatus()))
                    .toList();
            context.setDecisions(new ArrayList<>(facts));
        } catch (Exception e) {
            log.warn("聚合等级决策失败，该维度将判为数据不足: empId={}", empId, e);
        }
    }

    /** 等级决策表只存 tagId，能力名从画像映射里补；缺失时留 null（不编造） */
    private String resolveAbilityName(Long tagId, Map<Long, String> abilityNameByTag) {
        return tagId == null ? null : abilityNameByTag.get(tagId);
    }

    /** 解析形如 ["a","b"] 的 JSON 数组字符串；失败时按空列表处理（不抛异常） */
    private List<String> parseStringArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.debug("风险信号 JSON 解析失败，按空处理: {}", json);
            return List.of();
        }
    }
}
