package com.example.matching.service.governance;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.dto.governance.GovernanceAdmission;

public interface GovernedAdmissionService {

    GovernanceAdmission admitPersonAbility(PersonAbilityClaim claim);

    /**
     * Persist an already evaluated retryable person claim so the governed-admission
     * scheduler can retry it after the normal backoff interval.
     */
    GovernanceAdmission deferPersonAbilityRetry(PersonAbilityClaim claim, String reason);

    GovernanceAdmission admitPostAbility(PostAbilityClaim claim);

    /**
     * 重试一条 RETRYABLE 准入记录：重新执行 Harness 校验并在原记录上落地结果。
     *
     * @param recordId 准入记录ID
     * @return 落地后的结果；记录不存在/未到期/非 RETRYABLE/次数耗尽时返回 null
     */
    GovernanceAdmission retryDueAdmission(Long recordId);
}
