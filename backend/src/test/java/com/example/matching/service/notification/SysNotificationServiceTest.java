package com.example.matching.service.notification;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.notification.SysNotificationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 站内通知服务单测。
 *
 * 覆盖闭环设计（docs/hr-matching-closed-loop-design.md 3.1 节）的关键约束：
 * 防重幂等、通知失败不阻断主流程、归属校验静默忽略、**接收人隔离（通知绝不跨角色可见）**。
 */
class SysNotificationServiceTest {

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 在解析列名时需要 TableInfo 缓存；
     * 纯单测没有 Spring 容器，必须先手工初始化，否则断言 SQL 片段会抛
     * 「can not find lambda cache for this entity」。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
            SysNotification.class);
    }

    private final SysNotificationMapper notificationMapper = mock(SysNotificationMapper.class);
    private final EmpEmployeeMapper empEmployeeMapper = mock(EmpEmployeeMapper.class);
    private final SysNotificationService service = new SysNotificationService(notificationMapper, empEmployeeMapper);

    private static EmpEmployee employee(Long id, Long userId) {
        EmpEmployee employee = new EmpEmployee();
        employee.setId(id);
        employee.setUserId(userId);
        return employee;
    }

    @Test
    void sendInsertsNotificationWithDefaults() {
        when(notificationMapper.insert(any(SysNotification.class))).thenAnswer(invocation -> {
            SysNotification n = invocation.getArgument(0);
            n.setId(100L);
            return 1;
        });

        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(9L);
        notification.setType(SysNotification.TYPE_ASSESSMENT_REMINDER);
        notification.setTitle("能力评估提醒");
        notification.setBizType(SysNotification.BIZ_EMPLOYEE);
        notification.setBizId(1L);

        Long id = service.send(notification);

        assertThat(id).isEqualTo(100L);
        assertThat(notification.getReadStatus()).isEqualTo(SysNotification.READ_STATUS_UNREAD);
        verify(notificationMapper).insert(notification);
    }

    @Test
    void duplicateSendIsIdempotentAndReturnsExistingId() {
        SysNotification existing = new SysNotification();
        existing.setId(77L);
        when(notificationMapper.insert(any(SysNotification.class)))
            .thenThrow(new DuplicateKeyException("uk_notification_dedup"));
        when(notificationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(9L);
        notification.setType(SysNotification.TYPE_ASSESSMENT_REMINDER);
        notification.setBizType(SysNotification.BIZ_EMPLOYEE);
        notification.setBizId(1L);

        Long id = service.send(notification);

        // 防重唯一键命中：幂等返回已存在通知ID，不向调用方抛错
        assertThat(id).isEqualTo(77L);
        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    void sendFailureDoesNotThrowSoCallerTransactionIsNotBlocked() {
        when(notificationMapper.insert(any(SysNotification.class)))
            .thenThrow(new RuntimeException("db down"));

        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(9L);
        notification.setType(SysNotification.TYPE_REPORT_READY);
        notification.setBizType(SysNotification.BIZ_REPORT);
        notification.setBizId(2L);

        // 设计约束：通知失败只记日志，绝不阻断审核/匹配主事务
        Long id = service.send(notification);
        assertThat(id).isNull();
    }

    @Test
    void assessmentReminderResolvesReceiverFromEmployeeProfile() {
        when(empEmployeeMapper.selectById(5L)).thenReturn(employee(5L, 42L));
        when(notificationMapper.insert(any(SysNotification.class))).thenReturn(1);

        boolean sent = service.sendAssessmentReminder(5L, 7L);

        assertThat(sent).isTrue();
        verify(notificationMapper).insert(argThat((SysNotification n) ->
            n.getReceiverUserId().equals(42L)
                && SysNotification.TYPE_ASSESSMENT_REMINDER.equals(n.getType())
                && SysNotification.BIZ_EMPLOYEE.equals(n.getBizType())
                && n.getBizId().equals(5L)
                && n.getCreatedBy().equals(7L)));
    }

    @Test
    void assessmentReminderFailsGracefullyWhenEmployeeMissingOrUnbound() {
        when(empEmployeeMapper.selectById(404L)).thenReturn(null);
        when(empEmployeeMapper.selectById(6L)).thenReturn(employee(6L, null));

        assertThat(service.sendAssessmentReminder(404L, 7L)).isFalse();
        assertThat(service.sendAssessmentReminder(6L, 7L)).isFalse();
        verify(notificationMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    void markReadSilentlyIgnoresForeignOrMissingNotification() {
        SysNotification foreign = new SysNotification();
        foreign.setId(1L);
        foreign.setReceiverUserId(999L);
        foreign.setReadStatus(SysNotification.READ_STATUS_UNREAD);
        when(notificationMapper.selectById(1L)).thenReturn(foreign);
        when(notificationMapper.selectById(404L)).thenReturn(null);

        service.markRead(1L, 42L);
        service.markRead(404L, 42L);

        // 越权与不存在都静默忽略，不泄露通知存在性
        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    void markReadWritesReadStatusAndTimeForOwner() {
        SysNotification own = new SysNotification();
        own.setId(2L);
        own.setReceiverUserId(42L);
        own.setReadStatus(SysNotification.READ_STATUS_UNREAD);
        when(notificationMapper.selectById(2L)).thenReturn(own);

        service.markRead(2L, 42L);

        verify(notificationMapper).updateById(argThat((SysNotification u) ->
            u.getReadStatus() == SysNotification.READ_STATUS_READ && u.getReadTime() != null));
    }

    @Test
    void markReadIsNoOpWhenAlreadyRead() {
        SysNotification own = new SysNotification();
        own.setId(3L);
        own.setReceiverUserId(42L);
        own.setReadStatus(SysNotification.READ_STATUS_READ);
        when(notificationMapper.selectById(3L)).thenReturn(own);

        service.markRead(3L, 42L);

        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    /* ===================== 角色隔离（通知绝不能互相可见） =====================
     *
     * 用户口径：每个角色只能收到对应角色业务的通知，通知之间**绝不能互相可见**。
     *
     * 隔离靠两条同时成立：
     *   ① 读侧：pageMy / unreadCount / markAllRead 全部以 receiver_user_id 为唯一过滤条件；
     *   ② 写侧：每个投递点把 receiver 设成「具体的某个人」（员工档案绑定的 userId，
     *      或该业务负责的某个管理端 userId），且 sys_notification.receiver_user_id 为 NOT NULL
     *      —— 不存在 receiver 为空的广播行。
     *
     * 下面三条把读侧钉死。此前测试只覆盖了 send / markRead，**唯一没被锁的恰好是
     * 「分页与未读数是否按接收人过滤」**，而那正是产生「别人的通知出现在我铃铛里」的位置。
     */

    @Test
    void pageMyFiltersByReceiverSoNotificationsNeverCrossRoles() {
        when(notificationMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.pageMy(42L, 1, 10);

        verify(notificationMapper).selectPage(any(), argThat(wrapper ->
            filtersByReceiver(wrapper, 42L) && !bindsValue(wrapper, 999L)));
    }

    @Test
    void unreadCountFiltersByReceiverSoBadgeCountIsPersonal() {
        when(notificationMapper.selectCount(any())).thenReturn(3L);

        long unread = service.unreadCount(42L);

        assertThat(unread).isEqualTo(3L);
        verify(notificationMapper).selectCount(argThat(wrapper ->
            filtersByReceiver(wrapper, 42L)
                && wrapper.getSqlSegment().contains("read_status")));
    }

    @Test
    void markAllReadOnlyTouchesOwnNotifications() {
        when(notificationMapper.update(any(SysNotification.class), any())).thenReturn(1);

        service.markAllRead(42L);

        // 越权风险点：漏掉 receiver 条件会让「全部已读」把全员通知一起标掉
        verify(notificationMapper).update(any(SysNotification.class),
            argThat(wrapper -> filtersByReceiver(wrapper, 42L)));
    }

    /**
     * 包装器是否以 {@code receiver_user_id = 指定用户} 为过滤条件。
     *
     * <p>{@code getParamNameValuePairs()} 只在 {@code AbstractWrapper} 上，
     * 而 Mockito 回调拿到的是 {@code Wrapper} 接口 → 需要收窄类型。</p>
     */
    private static boolean filtersByReceiver(Wrapper<SysNotification> wrapper, Long receiverUserId) {
        return wrapper.getSqlSegment().contains("receiver_user_id") && bindsValue(wrapper, receiverUserId);
    }

    /** 包装器的绑定参数里是否出现了某个值（用于断言"确实按这个人过滤"以及"没有带上别人"） */
    private static boolean bindsValue(Wrapper<SysNotification> wrapper, Object value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }

    /* ============ 匹配结果推送通知：只发员工本人，撤回即消失 ============ */

    @Test
    void matchingPublishedGoesToTheEmployeesOwnAccountOnly() {
        // 记录说的是 empId=5 这名员工，其登录账号是 userId=42
        when(empEmployeeMapper.selectById(5L)).thenReturn(employee(5L, 42L));
        when(notificationMapper.insert(any(SysNotification.class))).thenReturn(1);

        service.sendMatchingPublished(5L, 900L, "后端工程师");

        // 接收人只能是该员工绑定的账号；HR（推送操作人）与其它角色都不在通知范围内
        verify(notificationMapper).insert(argThat((SysNotification n) ->
            n.getReceiverUserId().equals(42L)
                && SysNotification.TYPE_MATCHING_PUBLISHED.equals(n.getType())
                && SysNotification.BIZ_MATCHING_RECORD.equals(n.getBizType())
                && n.getBizId().equals(900L)
                && n.getContent().contains("后端工程师")
                // createdBy 留空 = 系统随推送动作触发，不冒充某个管理端用户发的
                && n.getCreatedBy() == null));
    }

    @Test
    void matchingPublishedSkipsWhenEmployeeHasNoBoundAccount() {
        when(empEmployeeMapper.selectById(7L)).thenReturn(employee(7L, null));
        when(empEmployeeMapper.selectById(404L)).thenReturn(null);

        service.sendMatchingPublished(7L, 900L, "后端工程师");
        service.sendMatchingPublished(404L, 901L, "后端工程师");

        // 无处可发：静默跳过（推送动作本身不该因此失败），更不能随便挑个人发出去
        verify(notificationMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    void matchingPublishedContentSurvivesMissingPostName() {
        when(empEmployeeMapper.selectById(5L)).thenReturn(employee(5L, 42L));
        when(notificationMapper.insert(any(SysNotification.class))).thenReturn(1);

        // 岗位名查不到时用占位文案，不能拼出「与「null」的匹配结果」这种话
        service.sendMatchingPublished(5L, 900L, null);

        verify(notificationMapper).insert(argThat((SysNotification n) ->
            n.getContent() != null && !n.getContent().contains("null")));
    }

    @Test
    void withdrawDeletesExactlyThatRecordsNotification() {
        when(notificationMapper.delete(any())).thenReturn(1);

        int deleted = service.withdrawMatchingPublished(900L);

        assertThat(deleted).isEqualTo(1);
        // 作用域必须被 bizId 精确限制在单条记录上：不按接收人过滤（员工账号可能已解绑，
        // 那会导致通知永远删不掉），但也绝不能变成"清空所有通知"。
        verify(notificationMapper).delete(argThat(wrapper ->
            wrapper.getSqlSegment().contains("type")
                && wrapper.getSqlSegment().contains("biz_type")
                && wrapper.getSqlSegment().contains("biz_id")
                && bindsValue(wrapper, SysNotification.TYPE_MATCHING_PUBLISHED)
                && bindsValue(wrapper, SysNotification.BIZ_MATCHING_RECORD)
                && bindsValue(wrapper, 900L)));
    }

    @Test
    void withdrawIsNoOpForNullRecordId() {
        assertThat(service.withdrawMatchingPublished(null)).isZero();
        verify(notificationMapper, never()).delete(any());
    }
}
