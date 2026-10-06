package com.example.matching.controller.system;

import com.example.matching.common.result.R;
import com.example.matching.entity.system.AbilityTagGovernanceNotification;
import com.example.matching.service.system.AbilityTagGovernanceNotificationService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/tag-candidate/placement-notifications")
@RequiredArgsConstructor
public class AbilityTagGovernanceNotificationController {
    private final AbilityTagGovernanceNotificationService notificationService;

    @GetMapping
    public R<List<AbilityTagGovernanceNotification>> listUnread() {
        return R.ok(notificationService.listUnread(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
