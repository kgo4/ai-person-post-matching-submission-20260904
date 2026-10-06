package com.example.matching.service.assessment.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.assessment.report.EmpCapabilityAnalysisReport;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.mapper.assessment.EmpCapabilityAnalysisReportMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.service.notification.SysNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 员工全面能力分析报告服务（HR 匹配闭环 P2）。
 *
 * <p>设计约束（docs/hr-matching-closed-loop-design.md 3.2 节）：
 * <ul>
 *   <li>触发：HR 完成该员工全部待审 harness 能力项后（事件驱动 + 清空检查）自动聚合；</li>
 *   <li>幂等：sourceFingerprint = 审核数据指纹，同指纹重复触发直接跳过；</li>
 *   <li>版本化：重审出新版本（version_no 递增），不覆盖旧版本；</li>
 *   <li>四区数据全部来自 person_ability_level_decision × person_ability_claim_group，
 *       人工审核理由沿 decision → batch_item → ai_harness_check_log.review_comment 反查。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityAnalysisReportService {

    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final PersonAbilityLevelDecisionMapper decisionMapper;
    private final AbilityHarnessBatchItemMapper batchItemMapper;
    private final AiHarnessCheckLogMapper checkLogMapper;
    private final EmpCapabilityAnalysisReportMapper reportMapper;
    private final com.example.matching.mapper.employee.EmpEmployeeMapper empEmployeeMapper;
    private final SysNotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 清空检查 + 生成报告。供事件监听器在事务提交后调用。
     *
     * @return 报告ID；未清空 / 无可聚合数据 / 幂等跳过时返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long generateIfCleared(Long empId) {
        if (empId == null) {
            return null;
        }
        try {
            if (!isReviewQueueCleared(empId)) {
                log.debug("员工仍有待审能力项，暂不生成分析报告: empId={}", empId);
                return null;
            }
            List<PersonAbilityLevelDecision> decisions = listDecisions(empId);
            if (decisions.isEmpty()) {
                // 没有任何等级决策（例如员工从未进入聚合审核链路），无可聚合内容
                log.debug("员工无等级决策记录，跳过分析报告: empId={}", empId);
                return null;
            }
            String fingerprint = buildFingerprint(empId, decisions);
            if (fingerprintExists(empId, fingerprint)) {
                log.debug("分析报告指纹未变化，幂等跳过: empId={}", empId);
                return null;
            }
            return generateReport(empId, decisions, fingerprint);
        } catch (Exception e) {
            // 报告生成失败只记日志，绝不影响审核主链路
            log.warn("全面能力分析报告生成失败（不阻断主流程）: empId={}", empId, e);
            return null;
        }
    }

    /**
     * 员工是否还有待审能力项。
     *
     * 【2026-09-04 修复】原实现把 {@code READY_FOR_AGGREGATE_HARNESS}（待聚合审核）也算作
     * 未完成，导致报告永远生成不出来：HR 采纳聚合 Harness 审核后
     * （{@code AggregateAbilityHarnessReviewServiceImpl.acceptAndProject}）会把 claim group
     * 置为 {@code READY_FOR_AGGREGATE_HARNESS}，而随后的 {@code finalizeIfAllReviewsResolved}
     * 只记日志、**不再推进状态**，即采纳后的终态就停在这个值；聚合与等级确认已在
     * acceptAndProject 内同步完成（confirmLevels + projectConfirmed）。
     * 因此这里只认 {@code PENDING_MANUAL_REVIEW} 为未完成。
     */
    private boolean isReviewQueueCleared(Long empId) {
        Long pendingGroups = claimGroupMapper.selectCount(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
            .eq(PersonAbilityClaimGroup::getEmpId, empId)
            .eq(PersonAbilityClaimGroup::getStatus,
                EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode()));
        if (pendingGroups != null && pendingGroups > 0) {
            return false;
        }
        Long pendingDecisions = decisionMapper.selectCount(new LambdaQueryWrapper<PersonAbilityLevelDecision>()
            .eq(PersonAbilityLevelDecision::getEmpId, empId)
            .eq(PersonAbilityLevelDecision::getDecisionStatus, DecisionStatusEnum.PENDING_MANUAL_REVIEW.name()));
        return pendingDecisions == null || pendingDecisions == 0;
    }

    private List<PersonAbilityLevelDecision> listDecisions(Long empId) {
        return decisionMapper.selectList(new LambdaQueryWrapper<PersonAbilityLevelDecision>()
            .eq(PersonAbilityLevelDecision::getEmpId, empId));
    }

    /** 指纹 = 决策数量 + 各决策状态/等级/更新时间聚合；审核数据一变指纹即变 */
    private String buildFingerprint(Long empId, List<PersonAbilityLevelDecision> decisions) {
        String digest = decisions.stream()
            .sorted(Comparator.comparing(PersonAbilityLevelDecision::getId))
            .map(d -> d.getId() + ":" + d.getDecisionStatus() + ":" + d.getFinalLevel()
                + ":" + (d.getUpdatedTime() == null ? "-" : d.getUpdatedTime()))
            .collect(Collectors.joining("|"));
        return "v1-" + Integer.toHexString((empId + "#" + digest).hashCode());
    }

    private boolean fingerprintExists(Long empId, String fingerprint) {
        return reportMapper.selectCount(new LambdaQueryWrapper<EmpCapabilityAnalysisReport>()
            .eq(EmpCapabilityAnalysisReport::getEmpId, empId)
            .eq(EmpCapabilityAnalysisReport::getSourceFingerprint, fingerprint)) > 0;
    }

    private Long generateReport(Long empId, List<PersonAbilityLevelDecision> decisions, String fingerprint) {
        Map<Long, PersonAbilityClaimGroup> groupsById = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                    .eq(PersonAbilityClaimGroup::getEmpId, empId))
            .stream()
            .collect(Collectors.toMap(PersonAbilityClaimGroup::getId, g -> g, (a, b) -> a));
        Map<Long, String> commentsByGroupId = loadReviewComments(decisions);

        List<Map<String, Object>> autoPassed = collectByStatus(decisions, groupsById, commentsByGroupId,
            DecisionStatusEnum.AUTO_CONFIRMED.name());
        List<Map<String, Object>> manualConfirmed = collectByStatus(decisions, groupsById, commentsByGroupId,
            DecisionStatusEnum.HUMAN_CONFIRMED.name());
        List<Map<String, Object>> manualRejected = collectByStatus(decisions, groupsById, commentsByGroupId,
            DecisionStatusEnum.REJECTED.name());
        List<Map<String, Object>> finalLevels = collectByStatus(decisions, groupsById, commentsByGroupId, null);

        EmpCapabilityAnalysisReport report = new EmpCapabilityAnalysisReport();
        report.setEmpId(empId);
        report.setVersionNo(nextVersion(empId));
        report.setAutoPassedJson(toJson(autoPassed));
        report.setManualConfirmedJson(toJson(manualConfirmed));
        report.setManualRejectedJson(toJson(manualRejected));
        report.setFinalLevelsJson(toJson(finalLevels));
        report.setSummary(buildSummary(autoPassed, manualConfirmed, manualRejected));
        report.setSourceFingerprint(fingerprint);
        report.setCreatedBy(decisions.stream()
            .map(PersonAbilityLevelDecision::getReviewedBy)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null));
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            // 并发触发同指纹：幂等跳过
            log.info("并发生成同指纹分析报告，幂等跳过: empId={}, fingerprint={}", empId, fingerprint);
            return null;
        }

        notifyReviewer(report);
        log.info("全面能力分析报告已生成: empId={}, version={}, id={}", empId, report.getVersionNo(), report.getId());
        return report.getId();
    }

    /** 人工审核理由：decision.claimGroupId → batch_item.harness_log_id → check_log.review_comment */
    private Map<Long, String> loadReviewComments(List<PersonAbilityLevelDecision> decisions) {
        List<Long> groupIds = decisions.stream()
            .map(PersonAbilityLevelDecision::getClaimGroupId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> groupToLog = batchItemMapper.selectList(new LambdaQueryWrapper<AbilityHarnessBatchItem>()
                .in(AbilityHarnessBatchItem::getClaimGroupId, groupIds))
            .stream()
            .filter(item -> item.getHarnessLogId() != null)
            .collect(Collectors.toMap(AbilityHarnessBatchItem::getClaimGroupId,
                AbilityHarnessBatchItem::getHarnessLogId, (a, b) -> a));
        if (groupToLog.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> comments = new HashMap<>();
        Map<Long, Long> logIdToGroup = groupToLog.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));
        checkLogMapper.selectBatchIds(groupToLog.values()).forEach(checkLog -> {
            Long groupId = logIdToGroup.get(checkLog.getId());
            if (groupId != null && checkLog.getReviewComment() != null) {
                comments.put(groupId, checkLog.getReviewComment());
            }
        });
        return comments;
    }

    /**
     * 按 decisionStatus 收集条目；statusFilter 为 null 时收集全部（用于 final_levels 汇总）。
     * 条目结构：{abilityName, finalLevel, confidence, reviewedBy, reviewedTime, comment}
     */
    private List<Map<String, Object>> collectByStatus(List<PersonAbilityLevelDecision> decisions,
                                                      Map<Long, PersonAbilityClaimGroup> groupsById,
                                                      Map<Long, String> commentsByGroupId,
                                                      String statusFilter) {
        return decisions.stream()
            .filter(d -> statusFilter == null || statusFilter.equals(d.getDecisionStatus()))
            .map(d -> {
                PersonAbilityClaimGroup group = d.getClaimGroupId() == null ? null : groupsById.get(d.getClaimGroupId());
                Map<String, Object> item = new HashMap<>();
                item.put("abilityName", group != null && group.getNormalizedAbilityName() != null
                    ? group.getNormalizedAbilityName() : (group != null ? group.getTaxonomyPath() : null));
                item.put("finalLevel", d.getFinalLevel());
                item.put("finalConfidence", d.getFinalConfidence());
                item.put("decisionStatus", d.getDecisionStatus());
                item.put("reviewedBy", d.getReviewedBy());
                item.put("reviewedTime", d.getReviewedTime() == null ? null : d.getReviewedTime().toString());
                item.put("comment", d.getClaimGroupId() == null ? null : commentsByGroupId.get(d.getClaimGroupId()));
                return item;
            })
            .toList();
    }

    private String buildSummary(List<Map<String, Object>> autoPassed,
                                List<Map<String, Object>> manualConfirmed,
                                List<Map<String, Object>> manualRejected) {
        int total = autoPassed.size() + manualConfirmed.size() + manualRejected.size();
        return "共 " + total + " 项能力完成最终确认：harness 自动通过 " + autoPassed.size()
            + " 项，人工确认通过 " + manualConfirmed.size() + " 项，人工拒绝 " + manualRejected.size()
            + " 项。各能力项最终等级与审核理由详见下方明细。";
    }

    private Integer nextVersion(Long empId) {
        EmpCapabilityAnalysisReport latest = reportMapper.selectOne(
            new LambdaQueryWrapper<EmpCapabilityAnalysisReport>()
                .eq(EmpCapabilityAnalysisReport::getEmpId, empId)
                .orderByDesc(EmpCapabilityAnalysisReport::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /** 报告就绪通知：发给最后审核的 HR（无审核人时跳过，系统全自动场景无 HR 可通知） */
    private void notifyReviewer(EmpCapabilityAnalysisReport report) {
        if (report.getCreatedBy() == null) {
            return;
        }
        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(report.getCreatedBy());
        notification.setType(SysNotification.TYPE_REPORT_READY);
        notification.setTitle("全面能力分析报告已生成");
        notification.setContent("该员工全部能力项审核已完成，系统已生成全面能力分析报告（v" + report.getVersionNo()
            + "），可在员工档案中查看。");
        notification.setBizType(SysNotification.BIZ_REPORT);
        notification.setBizId(report.getId());
        notification.setCreatedBy(report.getCreatedBy());
        notificationService.send(notification);
    }

    private String toJson(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("分析报告区块序列化失败", e);
            return "[]";
        }
    }

    /* ===================== 查询（供 Controller 使用，架构规则：Controller 不碰 Mapper） ===================== */

    /** 查询员工报告全部版本（版本号倒序） */
    public List<EmpCapabilityAnalysisReport> listByEmp(Long empId) {
        return reportMapper.selectList(new LambdaQueryWrapper<EmpCapabilityAnalysisReport>()
                .eq(EmpCapabilityAnalysisReport::getEmpId, empId))
            .stream()
            .sorted(Comparator.comparing(EmpCapabilityAnalysisReport::getVersionNo).reversed())
            .toList();
    }

    /** 最新版报告；无报告返回 null */
    public EmpCapabilityAnalysisReport latestByEmp(Long empId) {
        List<EmpCapabilityAnalysisReport> reports = listByEmp(empId);
        return reports.isEmpty() ? null : reports.get(0);
    }

    /**
     * 该员工是否已生成过任意版本的能力分析报告。
     *
     * <p>员工自助发起人岗匹配的准入闸门（闭环设计 S1/G5）：报告是「全部能力项审核完成」
     * 的产物，只有拿到它才代表员工能力画像已定稿，此时自助匹配才有意义。
     * 只做存在性计数，不加载报告内容。</p>
     */
    public boolean existsLatest(Long empId) {
        if (empId == null) {
            return false;
        }
        return reportMapper.selectCount(new LambdaQueryWrapper<EmpCapabilityAnalysisReport>()
            .eq(EmpCapabilityAnalysisReport::getEmpId, empId)) > 0;
    }

    /**
     * HR 手动补生成（兜底）。
     *
     * <p>自动生成依赖审核链路的 AFTER_COMMIT 事件（{@code AbilityChangeEvent} /
     * {@code CapabilityStageLifecycleEvent}）。若最后一步审核没有产生对应事件、
     * 或审核发生在监听器上线之前，报告就不会落库 —— 表现为「能力都审核通过了却仍显示暂无报告」。
     * 本方法让 HR 手动补一次，并**明确回报还差什么**，而不是静默失败。</p>
     *
     * @return 报告ID
     * @throws BusinessException 仍有待审项（附具体数量），或确实无可聚合数据
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long generateManually(Long empId) {
        if (empId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "员工档案ID不能为空");
        }
        String blocker = describeBlocker(empId);
        if (blocker != null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), blocker);
        }
        Long reportId = generateIfCleared(empId);
        if (reportId != null) {
            return reportId;
        }
        // generateIfCleared 返回 null 有两种可能：指纹幂等跳过（报告已存在）或无可聚合数据
        EmpCapabilityAnalysisReport latest = latestByEmp(empId);
        if (latest != null) {
            return latest.getId();
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(),
            "该员工暂无可聚合的能力审核数据（没有等级决策记录），无法生成分析报告");
    }

    /** 描述仍有待审的阻塞项；已清空返回 null（口径与 isReviewQueueCleared 保持一致） */
    private String describeBlocker(Long empId) {
        Long pendingGroups = claimGroupMapper.selectCount(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
            .eq(PersonAbilityClaimGroup::getEmpId, empId)
            .eq(PersonAbilityClaimGroup::getStatus,
                EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode()));
        Long pendingDecisions = decisionMapper.selectCount(new LambdaQueryWrapper<PersonAbilityLevelDecision>()
            .eq(PersonAbilityLevelDecision::getEmpId, empId)
            .eq(PersonAbilityLevelDecision::getDecisionStatus, DecisionStatusEnum.PENDING_MANUAL_REVIEW.name()));
        long groups = pendingGroups == null ? 0 : pendingGroups;
        long decisions = pendingDecisions == null ? 0 : pendingDecisions;
        if (groups == 0 && decisions == 0) {
            return null;
        }
        return "仍有待审内容，报告暂不能生成：待审能力声明组 " + groups
            + " 个、待人工确认的等级决策 " + decisions
            + " 条。请先在「人员 Harness 审核」中处理完，再生成报告。";
    }

    /**
     * 访问控制：HR（EMPLOYEE:READ）可看全员；员工本人（ASSESSMENT:SELF 登录）只能查
     * 自己绑定档案的报告。报告接口挂在 /api/employee/** 下，登录与基础权限已由
     * SecurityConfig 收口，这里补足「本人 vs 全员」的归属判定。
     */
    public boolean canAccess(Long empId, Long currentUserId, boolean hasEmployeeRead) {
        if (hasEmployeeRead) {
            return true;
        }
        if (currentUserId == null || empId == null) {
            return false;
        }
        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        return employee != null && currentUserId.equals(employee.getUserId());
    }
}
