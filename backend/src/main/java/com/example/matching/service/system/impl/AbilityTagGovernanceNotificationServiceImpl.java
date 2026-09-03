package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagGovernanceNotification;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagGovernanceNotificationMapper;
import com.example.matching.service.system.AbilityTagGovernanceNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbilityTagGovernanceNotificationServiceImpl implements AbilityTagGovernanceNotificationService {

    private final AbilityTagGovernanceNotificationMapper notificationMapper;
    private final AbilityTagCandidateMapper candidateMapper;

    @Override
    @Transactional
    public void notifyProposalReady(Long candidateId, Long proposalId) {
        AbilityTagCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) return;
        Long recipientId = candidate.getCreatedBy() == null ? 0L : candidate.getCreatedBy();
        boolean exists = notificationMapper.exists(Wrappers.<AbilityTagGovernanceNotification>lambdaQuery()
                .eq(AbilityTagGovernanceNotification::getProposalId, proposalId)
                .eq(AbilityTagGovernanceNotification::getRecipientUserId, recipientId));
        if (exists) return;

        AbilityTagGovernanceNotification notification = new AbilityTagGovernanceNotification();
        notification.setRecipientUserId(recipientId);
        notification.setCandidateId(candidateId);
        notification.setProposalId(proposalId);
        notification.setTitle("标签挂载建议已生成");
        notification.setContent("候选能力「" + candidate.getCandidateName() + "」已生成标签树挂载建议，请在候选审核中处理。");
        notification.setStatus("UNREAD");
        notificationMapper.insert(notification);
    }

    @Override
    public List<AbilityTagGovernanceNotification> listUnread(Long recipientUserId) {
        return notificationMapper.selectList(Wrappers.<AbilityTagGovernanceNotification>lambdaQuery()
                .eq(AbilityTagGovernanceNotification::getRecipientUserId, recipientUserId == null ? 0L : recipientUserId)
                .eq(AbilityTagGovernanceNotification::getStatus, "UNREAD")
                .orderByDesc(AbilityTagGovernanceNotification::getCreatedTime)
                .last("LIMIT 20"));
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, Long recipientUserId) {
        AbilityTagGovernanceNotification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !java.util.Objects.equals(notification.getRecipientUserId(), recipientUserId == null ? 0L : recipientUserId)) return;
        if (!"UNREAD".equals(notification.getStatus())) return;
        notification.setStatus("READ");
        notification.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }
}
