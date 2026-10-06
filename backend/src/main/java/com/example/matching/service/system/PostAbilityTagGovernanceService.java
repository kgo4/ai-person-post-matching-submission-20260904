package com.example.matching.service.system;

import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;

/** 后台岗位能力标签标准化旁路服务。 */
public interface PostAbilityTagGovernanceService {
    void govern(PostAbilityTagGovernanceRequestedEvent event);
}
