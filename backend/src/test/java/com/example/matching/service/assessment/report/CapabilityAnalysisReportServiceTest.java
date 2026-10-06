package com.example.matching.service.assessment.report;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.assessment.report.EmpCapabilityAnalysisReport;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.entity.workflow.AbilityHarnessBatchItem;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.mapper.assessment.EmpCapabilityAnalysisReportMapper;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.mapper.workflow.AbilityHarnessBatchItemMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.workflow.PersonAbilityLevelDecisionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全面能力分析报告服务单测。
 *
 * 覆盖闭环设计（docs/hr-matching-closed-loop-design.md 3.2 节）的关键约束：
 * 清空判定、无数据跳过、指纹幂等、版本递增、四区聚合、就绪通知。
 */
class CapabilityAnalysisReportServiceTest {

    private final PersonAbilityClaimGroupMapper claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
    private final PersonAbilityLevelDecisionMapper decisionMapper = mock(PersonAbilityLevelDecisionMapper.class);
    private final AbilityHarnessBatchItemMapper batchItemMapper = mock(AbilityHarnessBatchItemMapper.class);
    private final AiHarnessCheckLogMapper checkLogMapper = mock(AiHarnessCheckLogMapper.class);
    private final EmpCapabilityAnalysisReportMapper reportMapper = mock(EmpCapabilityAnalysisReportMapper.class);
    private final com.example.matching.mapper.employee.EmpEmployeeMapper empEmployeeMapper =
        mock(com.example.matching.mapper.employee.EmpEmployeeMapper.class);
    private final com.example.matching.service.notification.SysNotificationService notificationService =
        mock(com.example.matching.service.notification.SysNotificationService.class);
    private final CapabilityAnalysisReportService service = new CapabilityAnalysisReportService(
        claimGroupMapper, decisionMapper, batchItemMapper, checkLogMapper,
        reportMapper, empEmployeeMapper, notificationService, new ObjectMapper());

    {
        // 模拟 MyBatis-Plus insert 回填自增主键
        when(reportMapper.insert(any(EmpCapabilityAnalysisReport.class))).thenAnswer(invocation -> {
            EmpCapabilityAnalysisReport report = invocation.getArgument(0);
            report.setId(9001L);
            return 1;
        });
    }

    private static PersonAbilityClaimGroup group(Long id, Long empId, String name, String status) {
        PersonAbilityClaimGroup g = new PersonAbilityClaimGroup();
        g.setId(id);
        g.setEmpId(empId);
        g.setNormalizedAbilityName(name);
        g.setStatus(status);
        return g;
    }

    private static PersonAbilityLevelDecision decision(Long id, Long empId, Long groupId,
                                                       String status, Integer level, Long reviewedBy) {
        PersonAbilityLevelDecision d = new PersonAbilityLevelDecision();
        d.setId(id);
        d.setEmpId(empId);
        d.setClaimGroupId(groupId);
        d.setDecisionStatus(status);
        d.setFinalLevel(level);
        d.setReviewedBy(reviewedBy);
        d.setUpdatedTime(LocalDateTime.of(2026, 9, 30, 12, 0));
        return d;
    }

    private void cleared(Long empId) {
        when(claimGroupMapper.selectCount(any())).thenReturn(0L);
        when(decisionMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void skipsWhenReviewQueueNotEmpty() {
        when(claimGroupMapper.selectCount(any())).thenReturn(2L);

        assertThat(service.generateIfCleared(1L)).isNull();
        verify(reportMapper, never()).insert(any(EmpCapabilityAnalysisReport.class));
    }

    @Test
    void skipsWhenNoDecisionsExist() {
        cleared(1L);
        when(decisionMapper.selectList(any())).thenReturn(List.of());

        // 清空但没有任何等级决策（如未进入聚合审核链路），无可聚合内容
        assertThat(service.generateIfCleared(1L)).isNull();
        verify(reportMapper, never()).insert(any(EmpCapabilityAnalysisReport.class));
    }

    @Test
    void generatesVersionedReportWithFourSectionsAndNotifiesReviewer() throws Exception {
        Long empId = 1L;
        cleared(empId);
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(
            group(11L, empId, "Java 开发", EvidenceStatusEnum.CONFIRMED.getCode()),
            group(12L, empId, "系统设计", EvidenceStatusEnum.BLOCKED.getCode())));
        when(decisionMapper.selectList(any())).thenReturn(List.of(
            decision(101L, empId, 11L, DecisionStatusEnum.AUTO_CONFIRMED.name(), 4, null),
            decision(102L, empId, 12L, DecisionStatusEnum.HUMAN_CONFIRMED.name(), 3, 7L)));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.selectOne(any())).thenReturn(null);
        when(batchItemMapper.selectList(any())).thenReturn(List.of(
            batchItem(12L, 900L)));
        when(checkLogMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
            checkLog(900L, "能力项证据充分，人工确认通过")));
        when(notificationService.send(any())).thenReturn(1L);

        Long reportId = service.generateIfCleared(empId);

        assertThat(reportId).isNotNull();
        ArgumentCaptor<EmpCapabilityAnalysisReport> captor =
            ArgumentCaptor.forClass(EmpCapabilityAnalysisReport.class);
        verify(reportMapper).insert(captor.capture());
        EmpCapabilityAnalysisReport report = captor.getValue();

        assertThat(report.getVersionNo()).isEqualTo(1);
        assertThat(report.getEmpId()).isEqualTo(empId);
        assertThat(report.getCreatedBy()).isEqualTo(7L);
        assertThat(report.getSourceFingerprint()).startsWith("v1-");

        List<?> autoPassed = new ObjectMapper().readValue(report.getAutoPassedJson(), List.class);
        List<?> manualConfirmed = new ObjectMapper().readValue(report.getManualConfirmedJson(), List.class);
        List<?> manualRejected = new ObjectMapper().readValue(report.getManualRejectedJson(), List.class);
        List<?> finalLevels = new ObjectMapper().readValue(report.getFinalLevelsJson(), List.class);
        assertThat(autoPassed).hasSize(1);
        assertThat(manualConfirmed).hasSize(1);
        assertThat(manualRejected).isEmpty();
        assertThat(finalLevels).hasSize(2);
        assertThat(report.getSummary()).contains("自动通过 1", "人工确认通过 1", "人工拒绝 0");

        // 就绪通知发给最后审核人
        verify(notificationService).send(argThatNotification(7L, report.getId()));
    }

    private AbilityHarnessBatchItem batchItem(Long claimGroupId, Long harnessLogId) {
        AbilityHarnessBatchItem item = new AbilityHarnessBatchItem();
        item.setClaimGroupId(claimGroupId);
        item.setHarnessLogId(harnessLogId);
        return item;
    }

    private AiHarnessCheckLog checkLog(Long id, String comment) {
        AiHarnessCheckLog logEntity = new AiHarnessCheckLog();
        logEntity.setId(id);
        logEntity.setReviewComment(comment);
        return logEntity;
    }

    private com.example.matching.entity.notification.SysNotification argThatNotification(
        Long receiverUserId, Long bizId) {
        return org.mockito.ArgumentMatchers.argThat(n ->
            n != null
                && receiverUserId.equals(n.getReceiverUserId())
                && com.example.matching.entity.notification.SysNotification.TYPE_REPORT_READY.equals(n.getType())
                && bizId.equals(n.getBizId()));
    }

    @Test
    void identicalFingerprintSkipsDuplicateGeneration() {
        Long empId = 1L;
        cleared(empId);
        when(decisionMapper.selectList(any())).thenReturn(List.of(
            decision(101L, empId, 11L, DecisionStatusEnum.AUTO_CONFIRMED.name(), 4, null)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(
            group(11L, empId, "Java 开发", EvidenceStatusEnum.CONFIRMED.getCode())));

        // 第一次调用：可生成
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(reportMapper.selectOne(any())).thenReturn(null);
        Long first = service.generateIfCleared(empId);
        assertThat(first).isNotNull();

        // 同指纹已存在：第二次幂等跳过（模拟重放同一审核状态）
        when(reportMapper.selectCount(any())).thenReturn(1L);
        assertThat(service.generateIfCleared(empId)).isNull();
        verify(reportMapper, org.mockito.Mockito.times(1)).insert(any(EmpCapabilityAnalysisReport.class));
    }

    @Test
    void secondGenerationIncrementsVersion() {
        Long empId = 1L;
        cleared(empId);
        when(decisionMapper.selectList(any())).thenReturn(List.of(
            decision(101L, empId, 11L, DecisionStatusEnum.HUMAN_CONFIRMED.name(), 3, 7L)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(
            group(11L, empId, "Java 开发", EvidenceStatusEnum.CONFIRMED.getCode())));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(batchItemMapper.selectList(any())).thenReturn(List.of());
        when(notificationService.send(any())).thenReturn(1L);

        // 上一版是 v2 → 新版本应为 v3（重审出新版本，不覆盖旧版本）
        EmpCapabilityAnalysisReport previous = new EmpCapabilityAnalysisReport();
        previous.setVersionNo(2);
        when(reportMapper.selectOne(any())).thenReturn(previous);

        service.generateIfCleared(empId);

        ArgumentCaptor<EmpCapabilityAnalysisReport> captor =
            ArgumentCaptor.forClass(EmpCapabilityAnalysisReport.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(3);
    }

    /* ============ 手动补生成（HR 兜底，2026-09-04 新增） ============ */

    @Test
    void generateManuallyReportsConcreteBlockersWhenStillPending() {
        // 仍有待审：能力声明组 1 个 + 等级决策 2 条
        // → 明确回报数量，而不是静默返回 null（HR 需要知道还差什么）
        when(claimGroupMapper.selectCount(any())).thenReturn(1L);
        when(decisionMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.generateManually(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("待审能力声明组 1 个")
            .hasMessageContaining("待人工确认的等级决策 2 条");

        verify(reportMapper, never()).insert(any(EmpCapabilityAnalysisReport.class));
    }

    @Test
    void generateManuallyCreatesReportWhenQueueCleared() {
        Long empId = 1L;
        cleared(empId);
        when(decisionMapper.selectList(any())).thenReturn(List.of(
            decision(101L, empId, 11L, DecisionStatusEnum.HUMAN_CONFIRMED.name(), 3, 7L)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(
            group(11L, empId, "Java 开发", EvidenceStatusEnum.CONFIRMED.getCode())));
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(batchItemMapper.selectList(any())).thenReturn(List.of());
        when(notificationService.send(any())).thenReturn(1L);
        when(reportMapper.selectOne(any())).thenReturn(null);

        Long reportId = service.generateManually(empId);

        assertThat(reportId).isEqualTo(9001L);
    }
}
