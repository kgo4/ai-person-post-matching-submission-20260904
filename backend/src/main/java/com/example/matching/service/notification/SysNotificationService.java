package com.example.matching.service.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.notification.SysNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内通知服务（HR 匹配闭环 P1 基础设施）。
 *
 * <p>设计约束（docs/hr-matching-closed-loop-design.md 3.1 节）：
 * <ul>
 *   <li>防重依赖唯一键 receiver_user_id + type + biz_type + biz_id，重复发送视为幂等成功；</li>
 *   <li>通知发送失败只记日志，绝不抛出阻断调用方主事务（审核/匹配等）；</li>
 *   <li>接收人只能读写自己的通知，归属校验收口在本服务。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNotificationService {

    private final SysNotificationMapper sysNotificationMapper;
    private final EmpEmployeeMapper empEmployeeMapper;

    /**
     * 核心发送：补默认值后落库。
     *
     * @return 通知ID（重复发送时返回已存在通知的ID，幂等）
     */
    public Long send(SysNotification notification) {
        try {
            if (notification.getReadStatus() == null) {
                notification.setReadStatus(SysNotification.READ_STATUS_UNREAD);
            }
            sysNotificationMapper.insert(notification);
            return notification.getId();
        } catch (DuplicateKeyException e) {
            // 同接收人+同类型+同业务的通知已存在：防重唯一键命中，视为幂等成功
            log.debug("通知重复发送被唯一键拦截，按幂等处理: type={}, bizType={}, bizId={}, receiver={}",
                notification.getType(), notification.getBizType(), notification.getBizId(),
                notification.getReceiverUserId());
            SysNotification existing = sysNotificationMapper.selectOne(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getReceiverUserId, notification.getReceiverUserId())
                .eq(SysNotification::getType, notification.getType())
                .eq(SysNotification::getBizType, notification.getBizType())
                .eq(SysNotification::getBizId, notification.getBizId())
                .last("LIMIT 1"));
            return existing == null ? null : existing.getId();
        } catch (Exception e) {
            // 通知失败绝不阻断主事务（设计文档风险条目 3）
            log.warn("站内通知发送失败（不阻断主流程）: type={}, receiver={}", notification.getType(),
                notification.getReceiverUserId(), e);
            return null;
        }
    }

    /**
     * HR 提醒员工进行能力评估。
     *
     * @param empId          员工档案ID
     * @param operatorUserId 发起的 HR 用户ID
     * @return 是否实际发出（false = 员工不存在 / 无绑定账号）
     */
    public boolean sendAssessmentReminder(Long empId, Long operatorUserId) {
        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        if (employee == null || employee.getUserId() == null) {
            log.warn("提醒评估失败：员工不存在或未绑定账号 empId={}", empId);
            return false;
        }
        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(employee.getUserId());
        notification.setType(SysNotification.TYPE_ASSESSMENT_REMINDER);
        notification.setTitle("能力评估提醒");
        notification.setContent("HR 提醒你尽快完成能力评估（简历解析、AI 测试与 AI 面试），完成后即可发起岗位匹配。");
        notification.setBizType(SysNotification.BIZ_EMPLOYEE);
        notification.setBizId(empId);
        notification.setCreatedBy(operatorUserId);
        send(notification);
        return true;
    }

    /**
     * HR 推送匹配结果后，通知**该记录对应的员工本人**。
     *
     * <p>角色隔离（关键）：接收人由 {@code empId → emp_employee.user_id} 解析，
     * 即「这条匹配记录说的是谁，就只发给谁」。HR（推送动作的发起人）<b>不发</b> ——
     * 他是操作者，推送成功与否在页面上已有反馈；其他角色更不会收到。
     * 这与 {@code sys_notification.receiver_user_id NOT NULL} 的库约束一起，
     * 保证不存在「一条通知被多个角色看到」的可能。</p>
     *
     * <p>幂等：唯一键 (receiver, type, biz_type, biz_id) 含 bizId=匹配记录ID，
     * 因此同一记录重复推送不会产生第二条通知 —— 这也意味着撤回后必须真的把通知删掉
     * （见 {@link #withdrawMatchingPublished}），否则员工撤回再推送时收不到新通知。</p>
     *
     * @param empId            员工档案ID（该匹配记录的 empId）
     * @param matchingRecordId 匹配记录ID
     * @param postName         岗位名称，用于让通知本身可读（拿不到时用占位文案）
     */
    public void sendMatchingPublished(Long empId, Long matchingRecordId, String postName) {
        if (empId == null || matchingRecordId == null) {
            return;
        }
        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        if (employee == null || employee.getUserId() == null) {
            // 员工未绑定账号：无处可发。不抛错 —— 推送动作本身应当成功。
            log.warn("匹配结果推送通知跳过：员工不存在或未绑定账号 empId={}, recordId={}",
                empId, matchingRecordId);
            return;
        }
        String postLabel = (postName == null || postName.isBlank()) ? "目标岗位" : postName;
        SysNotification notification = new SysNotification();
        notification.setReceiverUserId(employee.getUserId());
        notification.setType(SysNotification.TYPE_MATCHING_PUBLISHED);
        notification.setTitle("收到新的匹配结果");
        notification.setContent("HR 已推送你与「" + postLabel
            + "」的匹配结果，请到「人岗匹配 → 我的匹配结果」查看匹配得分与差距诊断。");
        notification.setBizType(SysNotification.BIZ_MATCHING_RECORD);
        notification.setBizId(matchingRecordId);
        // createdBy 留空：系统随推送动作自动触发，不是某个管理端用户手动发的
        send(notification);
    }

    /**
     * 收回某条匹配记录的通知（HR 撤回推送 / 记录被删除时调用）。
     *
     * <p>不变式：<b>员工持有该通知 ⟺ 该记录当前处于「已推送」且未被删除</b>。
     * 撤回后通知必须消失，否则员工会看到一条点进去空空如也的通知
     * （员工侧只读页只展示已推送记录），且「没推送就没有通知」的口径会被破坏。</p>
     *
     * <p>按 (type, bizType, bizId) 删除而<b>不</b>按接收人过滤，是刻意的：
     * 这类通知的 bizId 就是匹配记录ID，语义上「关于这条记录的通知」本就只应发给该员工；
     * 若按接收人过滤，员工账号在推送后被解绑就会导致通知永远删不掉（解不出 receiver）。
     * 作用域仍被 bizId 精确限制在单条记录上，不会误伤其它通知。</p>
     *
     * @param matchingRecordId 匹配记录ID
     * @return 实际删除的条数（0 = 本来就没什么可收回的）
     */
    public int withdrawMatchingPublished(Long matchingRecordId) {
        if (matchingRecordId == null) {
            return 0;
        }
        int deleted = sysNotificationMapper.delete(new LambdaQueryWrapper<SysNotification>()
            .eq(SysNotification::getType, SysNotification.TYPE_MATCHING_PUBLISHED)
            .eq(SysNotification::getBizType, SysNotification.BIZ_MATCHING_RECORD)
            .eq(SysNotification::getBizId, matchingRecordId));
        if (deleted > 0) {
            log.info("已收回匹配结果推送通知: recordId={}, deleted={}", matchingRecordId, deleted);
        }
        return deleted;
    }

    /** 本人通知分页：未读优先，其余按发送时间倒序 */
    public Page<SysNotification> pageMy(Long receiverUserId, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return sysNotificationMapper.selectPage(new Page<>(safeCurrent, safeSize),
            new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getReceiverUserId, receiverUserId)
                .orderByAsc(SysNotification::getReadStatus)
                .orderByDesc(SysNotification::getCreatedTime));
    }

    /** 本人未读数（顶栏红点轮询） */
    public long unreadCount(Long receiverUserId) {
        return sysNotificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
            .eq(SysNotification::getReceiverUserId, receiverUserId)
            .eq(SysNotification::getReadStatus, SysNotification.READ_STATUS_UNREAD));
    }

    /**
     * 标记单条已读。只能标记自己的通知：归属不符时静默忽略（不泄露存在性）。
     */
    public void markRead(Long id, Long receiverUserId) {
        SysNotification notification = sysNotificationMapper.selectById(id);
        if (notification == null || !notification.getReceiverUserId().equals(receiverUserId)) {
            return;
        }
        if (notification.getReadStatus() != null && notification.getReadStatus() == SysNotification.READ_STATUS_READ) {
            return;
        }
        SysNotification update = new SysNotification();
        update.setId(id);
        update.setReadStatus(SysNotification.READ_STATUS_READ);
        update.setReadTime(LocalDateTime.now());
        sysNotificationMapper.updateById(update);
    }

    /** 全部已读 */
    public void markAllRead(Long receiverUserId) {
        SysNotification update = new SysNotification();
        update.setReadStatus(SysNotification.READ_STATUS_READ);
        update.setReadTime(LocalDateTime.now());
        sysNotificationMapper.update(update, new LambdaQueryWrapper<SysNotification>()
            .eq(SysNotification::getReceiverUserId, receiverUserId)
            .eq(SysNotification::getReadStatus, SysNotification.READ_STATUS_UNREAD));
    }
}


