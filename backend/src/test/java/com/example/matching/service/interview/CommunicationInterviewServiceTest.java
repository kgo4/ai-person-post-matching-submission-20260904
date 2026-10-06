package com.example.matching.service.interview;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.interview.CreateInterviewRequest;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.interview.EmpCommunicationInterview;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.event.CommunicationInterviewCreatedEvent;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.interview.EmpCommunicationInterviewMapper;
import com.example.matching.mapper.learning.EmpLearningOutcomeSubmissionMapper;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import com.example.matching.service.closure.ComprehensiveDiagnosisService;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.notification.SysNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频终面服务测试（人岗匹配闭环设计 P5）。
 *
 * <p>锁定的核心口径：</p>
 * <ul>
 *   <li>会议链接只做「格式 + 域名白名单」校验，不探活；</li>
 *   <li>关联匹配记录只做**存在性**校验，不要求已通过 —— 匹配通过只作「推荐」标记，不构成发起前置；</li>
 *   <li>结论「待定」保持待沟通（可改期再谈），其余置为已完成；</li>
 *   <li>已处理的终面不可重复录入；邀请通知与结论通知的 type 必须不同（否则被防重唯一键拦掉）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CommunicationInterviewServiceTest {

    @Mock
    private EmpCommunicationInterviewMapper interviewMapper;
    @Mock
    private EmpEmployeeMapper empEmployeeMapper;
    @Mock
    private MatchingRecordService matchingRecordService;
    @Mock
    private MatchingDataQueryService dataQuery;
    @Mock
    private CapabilityAnalysisReportService reportService;
    @Mock
    private ComprehensiveDiagnosisService comprehensiveDiagnosisService;
    @Mock
    private EmpLearningOutcomeSubmissionMapper outcomeMapper;
    @Mock
    private SysNotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CommunicationInterviewService service;

    @BeforeEach
    void setUp() {
        service = new CommunicationInterviewService(
                interviewMapper, empEmployeeMapper, matchingRecordService, dataQuery,
                reportService, comprehensiveDiagnosisService, outcomeMapper,
                notificationService, new ObjectMapper(), eventPublisher);
        ReflectionTestUtils.setField(service, "allowedMeetingHosts", "meeting.iflyrec.com");
    }

    private static EmpEmployee employee() {
        EmpEmployee e = new EmpEmployee();
        e.setId(7L);
        e.setRealName("张三");
        e.setUserId(1234L);
        return e;
    }

    private static MatchingRecord record(Integer matchStatus) {
        MatchingRecord r = new MatchingRecord();
        r.setId(100L);
        r.setEmpId(7L);
        r.setPostId(20L);
        r.setMatchStatus(matchStatus);
        return r;
    }

    private static EmpCommunicationInterview pending(Long id) {
        EmpCommunicationInterview e = new EmpCommunicationInterview();
        e.setId(id);
        e.setEmpId(7L);
        e.setStatus(EmpCommunicationInterview.STATUS_PENDING);
        e.setCreatedBy(99L);
        return e;
    }

    private static CreateInterviewRequest createRequest(String url) {
        CreateInterviewRequest req = new CreateInterviewRequest();
        req.setEmpId(7L);
        req.setMatchingRecordId(100L);
        req.setMeetingUrl(url);
        return req;
    }

    /* ===================== 发起 ===================== */

    @Test
    @DisplayName("发起：员工不存在时报错")
    void createRejectsUnknownEmployee() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createRequest("https://meeting.iflyrec.com/join?1"), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工不存在");
        verify(interviewMapper, never()).insert(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("发起：会议域名不在白名单时拒绝")
    void createRejectsHostOutsideWhitelist() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        assertThatThrownBy(() -> service.create(createRequest("https://evil.example.com/join?1"), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("域名不在允许列表");
        verify(interviewMapper, never()).insert(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("发起：非 http/https 链接被拒绝")
    void createRejectsNonHttpScheme() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        assertThatThrownBy(() -> service.create(createRequest("javascript:alert(1)"), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http://");
        verify(interviewMapper, never()).insert(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("发起：匹配未通过也能发起（匹配通过只作推荐，不作前置）")
    void createAllowsUnpassedMatchingRecord() {
        // 2026-09-04 口径变更：原实现会抛「尚未通过」，导致 HR 想先聊一聊再定的人被拦在门外。
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(100L)).thenReturn(record(3));
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenReturn(1);

        service.create(createRequest("https://meeting.iflyrec.com/join?1"), 99L);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getMatchingRecordId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo(EmpCommunicationInterview.STATUS_PENDING);
    }

    @Test
    @DisplayName("发起：关联的匹配记录不存在仍拒绝（避免脏引用）")
    void createRejectsUnknownMatchingRecord() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createRequest("https://meeting.iflyrec.com/join?1"), 99L))
                .isInstanceOf(BusinessException.class);
        verify(interviewMapper, never()).insert(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("发起：合法请求落库为待沟通并发出邀请通知")
    void createPersistsPendingAndNotifiesInvite() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(100L)).thenReturn(record(1));
        // insert 不会回填自增 ID，手工塞一个，便于断言事件里带的是真实记录ID
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, EmpCommunicationInterview.class).setId(500L);
            return 1;
        });

        service.create(createRequest("https://meeting.iflyrec.com/meeting/join?abc"), 99L);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).insert(captor.capture());
        EmpCommunicationInterview saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmpCommunicationInterview.STATUS_PENDING);
        assertThat(saved.getMeetingSource()).isEqualTo("MANUAL");
        assertThat(saved.getPostId()).isEqualTo(20L);
        assertThat(saved.getMatchingRecordId()).isEqualTo(100L);

        ArgumentCaptor<SysNotification> notificationCaptor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationService).send(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(SysNotification.TYPE_INVITE_MEETING);
        assertThat(notificationCaptor.getValue().getReceiverUserId()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("发起：沟通要点快照不在主流程内同步生成，改为发布事件异步回写")
    void createDefersBriefingSnapshotToAsyncEventListener() {
        // 口径：发起邀约是用户可感知的主流程，必须立刻返回。
        // 沟通要点要跨模块读报告、跑差距诊断，改为事务提交后由监听器异步生成，
        // 否则它一慢，HR 就会以为「发起」卡住了。
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(100L)).thenReturn(record(1));
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenReturn(1);

        service.create(createRequest("https://meeting.iflyrec.com/meeting/join?abc"), 99L);

        ArgumentCaptor<EmpCommunicationInterview> entityCaptor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getBriefingSnapshot()).isNull();

        ArgumentCaptor<CommunicationInterviewCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(CommunicationInterviewCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().empId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().matchingRecordId()).isEqualTo(100L);

        // 主流程内绝不能触发要点构建（否则同步阻塞又回来了）
        verify(reportService, never()).latestByEmp(anyLong());
    }

    @Test
    @DisplayName("异步回写：正常生成快照并 update 回写")
    void writeBriefingSnapshotPersistsWhenAvailable() {
        when(reportService.latestByEmp(7L)).thenReturn(null);
        when(interviewMapper.updateById(any(EmpCommunicationInterview.class))).thenReturn(1);

        service.writeBriefingSnapshotQuietly(500L, 7L, 100L);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(500L);
        assertThat(captor.getValue().getBriefingSnapshot()).contains("能力概况");
    }

    @Test
    @DisplayName("异步回写：要点生成异常时静默降级，不得影响已发起的终面")
    void writeBriefingSnapshotSwallowsFailure() {
        when(reportService.latestByEmp(7L)).thenThrow(new IllegalStateException("报告库不可用"));

        // 不抛出 —— 快照是辅助材料，失败不能反过来把已完成的主流程判成失败
        service.writeBriefingSnapshotQuietly(500L, 7L, 100L);

        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    /* ===================== 录入结论 ===================== */

    @Test
    @DisplayName("录入结论：非法结论被拒绝")
    void recordResultRejectsIllegalValue() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));

        assertThatThrownBy(() -> service.recordResult(1L, 9, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结论只能是");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("录入结论：通过 → 置为已完成并用 INTERVIEW_RESULT 类型发通知")
    void recordResultPassFinishesAndNotifies() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));
        when(interviewMapper.updateById(any(EmpCommunicationInterview.class))).thenReturn(1);
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        service.recordResult(1L, EmpCommunicationInterview.RESULT_PASS, "沟通顺畅");

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmpCommunicationInterview.STATUS_FINISHED);
        assertThat(captor.getValue().getResult()).isEqualTo(EmpCommunicationInterview.RESULT_PASS);
        assertThat(captor.getValue().getFinishedTime()).isNotNull();

        ArgumentCaptor<SysNotification> notificationCaptor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationService).send(notificationCaptor.capture());
        // 必须与邀请通知的 type 不同，否则会被 (receiver, type, bizType, bizId) 防重唯一键拦掉
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(SysNotification.TYPE_INTERVIEW_RESULT);
        assertThat(notificationCaptor.getValue().getContent()).contains("沟通顺畅");
    }

    @Test
    @DisplayName("录入结论：待定 → 保持待沟通，可改期再谈")
    void recordResultUndecidedKeepsPending() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));
        when(interviewMapper.updateById(any(EmpCommunicationInterview.class))).thenReturn(1);
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        service.recordResult(1L, EmpCommunicationInterview.RESULT_UNDECIDED, "需再谈一次");

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmpCommunicationInterview.STATUS_PENDING);
        assertThat(captor.getValue().getResult()).isEqualTo(EmpCommunicationInterview.RESULT_UNDECIDED);
    }

    @Test
    @DisplayName("录入结论：已完成的终面不可重复录入")
    void recordResultRejectsAlreadyFinished() {
        EmpCommunicationInterview finished = pending(1L);
        finished.setStatus(EmpCommunicationInterview.STATUS_FINISHED);
        when(interviewMapper.selectById(1L)).thenReturn(finished);

        assertThatThrownBy(() -> service.recordResult(1L, EmpCommunicationInterview.RESULT_PASS, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已处理");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    /* ===================== 员工响应（接受 / 放弃） ===================== */

    @Test
    @DisplayName("员工响应：不能响应他人的终面")
    void respondRejectsOtherEmployee() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));

        assertThatThrownBy(() -> service.respond(1L, 8L, EmpCommunicationInterview.RESPONSE_ACCEPTED, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权响应");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("员工响应：非法取值被拒绝")
    void respondRejectsIllegalValue() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));

        assertThatThrownBy(() -> service.respond(1L, 7L, 9, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("响应只能是");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("员工响应：接受只记录意愿、状态仍为待沟通，并给 HR 发 INTERVIEW_RESPONSE 通知")
    void respondAcceptedKeepsPendingAndNotifiesHr() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));
        when(interviewMapper.updateById(any(EmpCommunicationInterview.class))).thenReturn(1);
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        service.respond(1L, 7L, EmpCommunicationInterview.RESPONSE_ACCEPTED, null);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        EmpCommunicationInterview saved = captor.getValue();
        assertThat(saved.getEmployeeResponse()).isEqualTo(EmpCommunicationInterview.RESPONSE_ACCEPTED);
        assertThat(saved.getEmployeeRespondedTime()).isNotNull();
        // 接受不改状态：等 HR 面试完再录入结论
        assertThat(saved.getStatus()).isNull();

        ArgumentCaptor<SysNotification> notificationCaptor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationService).send(notificationCaptor.capture());
        SysNotification sent = notificationCaptor.getValue();
        // 收件人是发起该场终面的 HR，类型必须独立（否则会被防重唯一键拦掉）
        assertThat(sent.getReceiverUserId()).isEqualTo(99L);
        assertThat(sent.getType()).isEqualTo(SysNotification.TYPE_INTERVIEW_RESPONSE);
        assertThat(sent.getBizType()).isEqualTo(SysNotification.BIZ_INTERVIEW);
        assertThat(sent.getBizId()).isEqualTo(1L);
        assertThat(sent.getContent()).contains("张三").contains("接受");
    }

    @Test
    @DisplayName("员工响应：放弃 → 该场终面置为已取消，并保留放弃原因一起通知 HR")
    void respondDeclinedCancelsInterviewWithReason() {
        when(interviewMapper.selectById(1L)).thenReturn(pending(1L));
        when(interviewMapper.updateById(any(EmpCommunicationInterview.class))).thenReturn(1);
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        service.respond(1L, 7L, EmpCommunicationInterview.RESPONSE_DECLINED, "  已接受其他 offer  ");

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).updateById(captor.capture());
        EmpCommunicationInterview saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EmpCommunicationInterview.STATUS_CANCELLED);
        // 原因去空白后落库
        assertThat(saved.getEmployeeResponseComment()).isEqualTo("已接受其他 offer");

        ArgumentCaptor<SysNotification> notificationCaptor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationService).send(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getTitle()).contains("放弃");
        assertThat(notificationCaptor.getValue().getContent()).contains("已接受其他 offer");
    }

    @Test
    @DisplayName("员工响应：响应一次后不可重复响应（避免状态反复与通知被防重键吞掉）")
    void respondRejectsSecondResponse() {
        EmpCommunicationInterview responded = pending(1L);
        responded.setEmployeeResponse(EmpCommunicationInterview.RESPONSE_ACCEPTED);
        when(interviewMapper.selectById(1L)).thenReturn(responded);

        assertThatThrownBy(() -> service.respond(1L, 7L, EmpCommunicationInterview.RESPONSE_DECLINED, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已响应过");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("员工响应：已取消 / 已完成的终面不能再响应")
    void respondRejectsNonPendingInterview() {
        EmpCommunicationInterview cancelled = pending(1L);
        cancelled.setStatus(EmpCommunicationInterview.STATUS_CANCELLED);
        when(interviewMapper.selectById(1L)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.respond(1L, 7L, EmpCommunicationInterview.RESPONSE_ACCEPTED, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结束或已取消");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    /* ===================== 取消 ===================== */

    @Test
    @DisplayName("取消：非待沟通状态被拒绝")
    void cancelRejectsNonPending() {
        EmpCommunicationInterview cancelled = pending(1L);
        cancelled.setStatus(EmpCommunicationInterview.STATUS_CANCELLED);
        when(interviewMapper.selectById(1L)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有待沟通");
        verify(interviewMapper, never()).updateById(any(EmpCommunicationInterview.class));
    }

    /* ===================== 候选池 ===================== */

    @Test
    @DisplayName("候选池：无匹配记录时返回空列表")
    void listCandidatesReturnsEmptyWhenNoRecord() {
        when(matchingRecordService.listRecentRecords(any(Integer.class))).thenReturn(List.of());

        assertThat(service.listCandidates(100)).isEmpty();
        verify(interviewMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("候选池：未通过的匹配结果也在池中，只是不标推荐")
    void listCandidatesIncludesUnpassedRecordWithoutRecommendFlag() {
        MatchingRecord unpassed = record(4);
        unpassed.setEmpName("李四");
        unpassed.setPostName("前端工程师");
        when(matchingRecordService.listRecentRecords(any(Integer.class))).thenReturn(List.of(unpassed));
        when(interviewMapper.selectList(any())).thenReturn(List.of(), List.of());

        var candidates = service.listCandidates(100);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).recommended()).isFalse();
        assertThat(candidates.get(0).matchingRecordId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("候选池：推荐项排在前面，且标记已有待沟通终面与最近结论")
    void listCandidatesMarksPendingAndLatestResult() {
        MatchingRecord passed = record(1);
        passed.setEmpName("张三");
        passed.setPostName("后端工程师");
        MatchingRecord unpassed = record(4);
        unpassed.setId(101L);
        unpassed.setEmpId(8L);
        unpassed.setEmpName("李四");
        unpassed.setPostName("前端工程师");
        // 数据源顺序：未通过在前（updatedTime 倒序），服务层应把它排到推荐项之后
        when(matchingRecordService.listRecentRecords(any(Integer.class)))
                .thenReturn(List.of(unpassed, passed));

        EmpCommunicationInterview pendingItem = new EmpCommunicationInterview();
        pendingItem.setEmpId(7L);
        pendingItem.setStatus(EmpCommunicationInterview.STATUS_PENDING);
        EmpCommunicationInterview finishedItem = new EmpCommunicationInterview();
        finishedItem.setEmpId(7L);
        finishedItem.setStatus(EmpCommunicationInterview.STATUS_FINISHED);
        finishedItem.setResult(EmpCommunicationInterview.RESULT_PASS);
        finishedItem.setFinishedTime(LocalDateTime.now());
        when(interviewMapper.selectList(any())).thenReturn(List.of(pendingItem), List.of(finishedItem));

        var candidates = service.listCandidates(100);

        assertThat(candidates).hasSize(2);
        // 推荐（匹配通过）排前，未通过的靠后
        assertThat(candidates.get(0).recommended()).isTrue();
        assertThat(candidates.get(0).postName()).isEqualTo("后端工程师");
        assertThat(candidates.get(0).hasPendingInterview()).isTrue();
        assertThat(candidates.get(0).latestResult()).isEqualTo(EmpCommunicationInterview.RESULT_PASS);
        assertThat(candidates.get(1).recommended()).isFalse();
    }

    @Test
    @DisplayName("员工侧：未绑定档案时返回空页")
    void pageMyReturnsEmptyWhenUnbound() {
        assertThat(service.pageMy(null, 1, 10).records()).isEmpty();
        verify(interviewMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("待沟通计数：透传 mapper")
    void countPendingDelegates() {
        when(interviewMapper.selectCount(any())).thenReturn(2L);

        assertThat(service.countPending()).isEqualTo(2L);
    }

    /* ===================== 会议链接抽取（整段邀请原文） ===================== */

    @Test
    @DisplayName("会议链接：整段粘贴讯飞邀请原文时抽取入会链接，而不是客户端下载地址")
    void extractsJoinLinkFromPastedInvitationText() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(anyLong())).thenReturn(record(2));
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenReturn(1);

        String pasted = """
                王永峰 邀请您加入【20260904-2104远程会议】
                点击链接直接加入会议: https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e
                会议号: 11626686 会议密码: 123456
                最新版本下载地址: https://meeting.iflyrec.com/download.html""";

        service.create(createRequest(pasted), 99L);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).insert(captor.capture());
        // 同一域名下有两条链接，必须靠路径特征挑中入会链接
        assertThat(captor.getValue().getMeetingUrl())
                .isEqualTo("https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e");
    }

    @Test
    @DisplayName("会议链接：链接后紧跟中文说明时不会把中文吞进链接，并剥掉尾部标点")
    void stripsTrailingChineseContextAndPunctuation() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(anyLong())).thenReturn(record(2));
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenReturn(1);

        service.create(createRequest("请点击 https://meeting.iflyrec.com/meeting/join?abc，准时参会。"), 99L);

        ArgumentCaptor<EmpCommunicationInterview> captor =
                ArgumentCaptor.forClass(EmpCommunicationInterview.class);
        verify(interviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getMeetingUrl())
                .isEqualTo("https://meeting.iflyrec.com/meeting/join?abc");
    }

    @Test
    @DisplayName("会议链接：整段文本里没有任何链接时，提示可直接粘贴邀请信息并给出示例")
    void rejectsTextWithoutAnyLink() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());

        assertThatThrownBy(() -> service.create(createRequest("下周一上午十点，会议室见"), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有识别到会议链接")
                .hasMessageContaining("https://");
        verify(interviewMapper, never()).insert(any(EmpCommunicationInterview.class));
    }

    @Test
    @DisplayName("会议链接域名匹配大小写不敏感")
    void meetingHostMatchIsCaseInsensitive() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee());
        when(matchingRecordService.getById(anyLong())).thenReturn(record(2));
        when(interviewMapper.insert(any(EmpCommunicationInterview.class))).thenReturn(1);

        service.create(createRequest("https://Meeting.iFlyRec.com/meeting/join?abc"), 99L);

        verify(interviewMapper).insert(any(EmpCommunicationInterview.class));
    }
}
