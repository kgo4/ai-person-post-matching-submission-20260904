package com.example.matching.controller.notification;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.notification.api.AssessmentReminderRequest;
import com.example.matching.dto.notification.api.NotificationVO;
import com.example.matching.entity.notification.SysNotification;
import com.example.matching.service.notification.SysNotificationService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口。
 *
 * <p>发送侧只开放「提醒评估」一个业务入口（EMPLOYEE:READ 收口）；
 * 通用 send 留在 Service 层供后续阶段（报告就绪/学习复核/会议邀请）内部调用。
 * 接收侧全部按登录身份收口，只能读写自己的通知。</p>
 *
 * @author system
 */
@Tag(name = "站内通知", description = "通知查收、已读与 HR 提醒评估")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SysNotificationService sysNotificationService;

    @Operation(summary = "提醒员工进行能力评估", description = "HR 侧发送；同一员工同一批次的重复提醒被幂等拦截")
    @PostMapping("/assessment-reminder")
    public R<Void> sendAssessmentReminder(@RequestBody @Valid AssessmentReminderRequest request) {
        boolean sent = sysNotificationService.sendAssessmentReminder(
            request.getEmpId(), SecurityUtils.getCurrentUserId());
        if (!sent) {
            return R.fail("员工不存在或未绑定账号，无法提醒");
        }
        return R.ok("提醒已发送", null);
    }

    @Operation(summary = "我的通知分页", description = "未读优先，其余按发送时间倒序")
    @GetMapping("/my")
    public R<PageResponse<NotificationVO>> my(
        @RequestParam(defaultValue = "1") long current,
        @RequestParam(defaultValue = "10") long size) {
        Page<SysNotification> page = sysNotificationService.pageMy(
            SecurityUtils.getCurrentUserId(), current, size);
        return R.ok(PageResponse.from(page, NotificationVO::from));
    }

    @Operation(summary = "我的未读通知数", description = "顶栏红点轮询接口")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(sysNotificationService.unreadCount(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "标记通知已读", description = "只能标记本人通知；越权与不存在静默忽略")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        sysNotificationService.markRead(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        sysNotificationService.markAllRead(SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
