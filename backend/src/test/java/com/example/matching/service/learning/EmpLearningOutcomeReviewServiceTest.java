package com.example.matching.service.learning;

import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.learning.LearningOutcomeSubmissionResponse;
import com.example.matching.dto.learning.LearningOutcomeSubmitDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.notification.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习成果 HR 复核闭环测试（人岗匹配闭环设计 P4）。
 *
 * <p>锁定的核心不变式：</p>
 * <ul>
 *   <li>员工提交只落「待复核」，**不回写能力证据**；</li>
 *   <li>只有 HR 复核通过才回调 {@code onLearningOutcomeConfirmed}；</li>
 *   <li>同一能力项存在待复核单时拒绝重复提交；</li>
 *   <li>驳回必须带理由；非待复核状态不能被再次复核。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmpLearningOutcomeReviewServiceTest {

    @Mock
    private EmpLearningOutcomeSubmissionMapper submissionMapper;

    @Mock
    private EmpEmployeeMapper empEmployeeMapper;

    @Mock
    private CapabilityClosureService capabilityClosureService;

    @Mock
    private SysNotificationService notificationService;

    @Mock
    private MatchingRecordService matchingRecordService;

    private EmpLearningOutcomeReviewService service;

    @BeforeEach
    void setUp() {
        service = new EmpLearningOutcomeReviewService(
                submissionMapper, empEmployeeMapper, capabilityClosureService,
                notificationService, matchingRecordService);
    }

    private static LearningOutcomeSubmitDTO submitDto() {
        LearningOutcomeSubmitDTO dto = new LearningOutcomeSubmitDTO();
        dto.setTagId(55L);
        dto.setConfirmedLevel(4);
        dto.setBeforeLevel(2);
        dto.setNote("完成课程并通过练习");
        return dto;
    }

    private static CapabilityClosureResult closureResult(String businessKey) {
        CapabilityClosureResult result = new CapabilityClosureResult();
        result.setEventType("LEARNING_OUTCOME_CONFIRMED");
        result.setSourceType("LEARNING");
        result.setBusinessKey(businessKey);
        result.setClosureStatus("SUCCESS");
        result.setEvidenceCount(1);
        result.setKnowledgeDocCount(0);
        result.setGraphRefreshStatus("SUCCESS");
        result.setMessage("ok");
        return result;
    }

    private static EmpLearningOutcomeSubmission pending(Long id) {
        EmpLearningOutcomeSubmission entity = new EmpLearningOutcomeSubmission();
        entity.setId(id);
        entity.setEmpId(7L);
        entity.setTagId(55L);
        entity.setConfirmedLevel(4);
        entity.setReviewStatus(EmpLearningOutcomeSubmission.STATUS_PENDING);
        return entity;
    }

    /* ===================== 提交 ===================== */

    @Test
    @DisplayName("提交：未绑定人员档案时拒绝")
    void submitRejectsUnboundAccount() {
        assertThatThrownBy(() -> service.submit(null, submitDto()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未绑定人员档案");

        verify(submissionMapper, never()).insert(any(EmpLearningOutcomeSubmission.class));
        verify(capabilityClosureService, never()).onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class));
    }

    @Test
    @DisplayName("提交：只落待复核，不回写能力证据")
    void submitOnlyCreatesPendingWithoutWritingBack() {
        LearningOutcomeSubmitDTO dto = submitDto();
        when(submissionMapper.selectCount(any())).thenReturn(0L);
        when(submissionMapper.insert(any(EmpLearningOutcomeSubmission.class))).thenReturn(1);

        service.submit(7L, dto);

        ArgumentCaptor<EmpLearningOutcomeSubmission> captor =
                ArgumentCaptor.forClass(EmpLearningOutcomeSubmission.class);
        verify(submissionMapper).insert(captor.capture());
        EmpLearningOutcomeSubmission saved = captor.getValue();
        assertThat(saved.getEmpId()).isEqualTo(7L);
        assertThat(saved.getReviewStatus()).isEqualTo(EmpLearningOutcomeSubmission.STATUS_PENDING);
        assertThat(saved.getConfirmedLevel()).isEqualTo(4);

        // 关键不变式：提交阶段绝不能触发回写链路
        verify(capabilityClosureService, never()).onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class));
    }

    @Test
    @DisplayName("提交：自评等级越界被拒绝")
    void submitRejectsIllegalLevel() {
        LearningOutcomeSubmitDTO dto = submitDto();
        dto.setConfirmedLevel(6);

        assertThatThrownBy(() -> service.submit(7L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1-5");

        verify(submissionMapper, never()).insert(any(EmpLearningOutcomeSubmission.class));
    }

    @Test
    @DisplayName("提交：未指定能力项被拒绝")
    void submitRejectsMissingAbility() {
        LearningOutcomeSubmitDTO dto = submitDto();
        dto.setTagId(null);
        dto.setAbilityName(null);

        assertThatThrownBy(() -> service.submit(7L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力项");

        verify(submissionMapper, never()).insert(any(EmpLearningOutcomeSubmission.class));
    }

    @Test
    @DisplayName("提交：同能力项已有待复核单时拒绝重复提交")
    void submitRejectsDuplicatePending() {
        when(submissionMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(7L, submitDto()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待复核");

        verify(submissionMapper, never()).insert(any(EmpLearningOutcomeSubmission.class));
    }

    /* ===================== 复核 ===================== */

    @Test
    @DisplayName("通过：回调既有回写链路并置为已通过")
    void approveTriggersWriteBackAndMarksApproved() {
        when(submissionMapper.selectById(1L)).thenReturn(pending(1L));
        when(capabilityClosureService.onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class)))
                .thenReturn(closureResult("LEARNING_OUTCOME_CONFIRMED:EMP:7:TAG:55:RESOURCE:0"));
        when(submissionMapper.updateById(any(EmpLearningOutcomeSubmission.class))).thenReturn(1);

        service.approve(1L, "材料充分", 99L);

        ArgumentCaptor<LearningOutcomeConfirmDTO> dtoCaptor =
                ArgumentCaptor.forClass(LearningOutcomeConfirmDTO.class);
        verify(capabilityClosureService).onLearningOutcomeConfirmed(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getEmpId()).isEqualTo(7L);
        assertThat(dtoCaptor.getValue().getTagId()).isEqualTo(55L);
        assertThat(dtoCaptor.getValue().getConfirmedLevel()).isEqualTo(4);
        // 来源标记便于区分「HR 复核通过」与历史「员工直接确认」
        assertThat(dtoCaptor.getValue().getConfirmationSource()).isEqualTo("HR_REVIEW");

        ArgumentCaptor<EmpLearningOutcomeSubmission> updateCaptor =
                ArgumentCaptor.forClass(EmpLearningOutcomeSubmission.class);
        verify(submissionMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getReviewStatus())
                .isEqualTo(EmpLearningOutcomeSubmission.STATUS_APPROVED);
        assertThat(updateCaptor.getValue().getReviewComment()).isEqualTo("材料充分");
        assertThat(updateCaptor.getValue().getReviewedBy()).isEqualTo(99L);
        assertThat(updateCaptor.getValue().getClosureBusinessKey())
                .isEqualTo("LEARNING_OUTCOME_CONFIRMED:EMP:7:TAG:55:RESOURCE:0");
    }

    @Test
    @DisplayName("通过：非待复核状态被拒绝（防重复复核）")
    void approveRejectsAlreadyReviewed() {
        EmpLearningOutcomeSubmission approved = pending(1L);
        approved.setReviewStatus(EmpLearningOutcomeSubmission.STATUS_APPROVED);
        when(submissionMapper.selectById(1L)).thenReturn(approved);

        assertThatThrownBy(() -> service.approve(1L, null, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被复核");

        verify(capabilityClosureService, never()).onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class));
        verify(submissionMapper, never()).updateById(any(EmpLearningOutcomeSubmission.class));
    }

    @Test
    @DisplayName("驳回：必须带理由")
    void rejectRequiresComment() {
        assertThatThrownBy(() -> service.reject(1L, "  ", 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回理由不能为空");

        verify(submissionMapper, never()).updateById(any(EmpLearningOutcomeSubmission.class));
    }

    @Test
    @DisplayName("驳回：置为已驳回且不回写能力证据")
    void rejectMarksRejectedWithoutWriteBack() {
        when(submissionMapper.selectById(1L)).thenReturn(pending(1L));
        when(submissionMapper.updateById(any(EmpLearningOutcomeSubmission.class))).thenReturn(1);

        service.reject(1L, "证据不足", 99L);

        ArgumentCaptor<EmpLearningOutcomeSubmission> captor =
                ArgumentCaptor.forClass(EmpLearningOutcomeSubmission.class);
        verify(submissionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReviewStatus())
                .isEqualTo(EmpLearningOutcomeSubmission.STATUS_REJECTED);
        assertThat(captor.getValue().getReviewComment()).isEqualTo("证据不足");

        verify(capabilityClosureService, never()).onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class));
    }

    /* ===================== 列表 ===================== */

    @Test
    @DisplayName("本人列表：未绑定档案时返回空页，不查库")
    void pageMyReturnsEmptyWhenUnbound() {
        PageResponse<LearningOutcomeSubmissionResponse> page = service.pageMy(null, 1, 10);

        assertThat(page.records()).isEmpty();
    }

    @Test
    @DisplayName("复核列表：补全员工姓名")
    void pageForReviewEnrichesEmpName() {
        EmpLearningOutcomeSubmission record = pending(1L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<EmpLearningOutcomeSubmission> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
        page.setRecords(List.of(record));
        when(submissionMapper.selectPage(any(), any())).thenReturn(page);

        EmpEmployee employee = new EmpEmployee();
        employee.setId(7L);
        employee.setRealName("张三");
        when(empEmployeeMapper.selectBatchIds(any())).thenReturn(List.of(employee));

        PageResponse<LearningOutcomeSubmissionResponse> result = service.pageForReview(1, 10, null);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).empName()).isEqualTo("张三");
        assertThat(result.records().get(0).reviewStatus()).isEqualTo(EmpLearningOutcomeSubmission.STATUS_PENDING);
    }

    @Test
    @DisplayName("待复核计数：透传 mapper 计数")
    void countByReviewStatusDelegates() {
        when(submissionMapper.selectCount(any())).thenReturn(3L);

        assertThat(service.countByReviewStatus(EmpLearningOutcomeSubmission.STATUS_PENDING)).isEqualTo(3L);
    }

    @Test
    @DisplayName("通知失败不影响复核主流程（由通知服务内部兜底）")
    void notificationFailureDoesNotBlockApprove() {
        when(submissionMapper.selectById(1L)).thenReturn(pending(1L));
        when(capabilityClosureService.onLearningOutcomeConfirmed(any(LearningOutcomeConfirmDTO.class)))
                .thenReturn(closureResult("biz-key"));
        when(submissionMapper.updateById(any(EmpLearningOutcomeSubmission.class))).thenReturn(1);
        // 员工存在且已绑定账号 → 会真实走到通知发送，并被异常打断（由服务层兜底捕获）
        EmpEmployee employee = new EmpEmployee();
        employee.setId(7L);
        employee.setUserId(1234L);
        when(empEmployeeMapper.selectById(anyLong())).thenReturn(employee);
        when(notificationService.send(any())).thenThrow(new RuntimeException("mq down"));

        service.approve(1L, null, 99L);

        verify(submissionMapper).updateById(any(EmpLearningOutcomeSubmission.class));
    }

    @Test
    @DisplayName("复核：提交单不存在时报错")
    void approveRejectsUnknownSubmission() {
        when(submissionMapper.selectById(eq(404L))).thenReturn(null);

        assertThatThrownBy(() -> service.approve(404L, null, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }
}
