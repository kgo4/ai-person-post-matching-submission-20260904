package com.example.matching.event;

/**
 * 岗位能力写入后的旁路标签治理事件。
 * 不参与岗位能力表和全景图谱主流程，仅用于后台标准化系统标签库。
 */
public record PostAbilityTagGovernanceRequestedEvent(
        Long postId,
        String abilityName,
        String tagCategory,
        String sourceType,
        Long sourceRefId,
        String evidenceText,
        String reasoning) {
}
