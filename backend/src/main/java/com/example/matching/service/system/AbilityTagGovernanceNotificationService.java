package com.example.matching.service.system;

import com.example.matching.entity.system.AbilityTagGovernanceNotification;

import java.util.List;

public interface AbilityTagGovernanceNotificationService {
    void notifyProposalReady(Long candidateId, Long proposalId);

    List<AbilityTagGovernanceNotification> listUnread(Long recipientUserId);

    void markRead(Long notificationId, Long recipientUserId);
}
