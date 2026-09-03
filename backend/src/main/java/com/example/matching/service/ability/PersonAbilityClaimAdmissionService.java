package com.example.matching.service.ability;

import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;

public interface PersonAbilityClaimAdmissionService {

    /**
     * @deprecated 已废弃。能力正式准入统一走 GovernedAdmissionServiceImpl.admitPersonAbility，
     * 此旧治理入口（admit/admitWithoutSideEffects）仅被已废弃的 Excel 批量导入使用，保留仅供兼容，勿新增调用。
     */
    @Deprecated
    PersonAbilityClaim admit(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                             AiHarnessDecisionDTO decision);

    /**
     * Persists a claim and its evidence, but defers the employee-wide profile refresh
     * and change event until the caller finishes a batch.
     *
     * @deprecated 已废弃，理由同 {@link #admit}。
     */
    @Deprecated
    PersonAbilityClaim admitWithoutSideEffects(com.example.matching.agent.dto.person.PersonAbilityClaim source,
                                               AiHarnessDecisionDTO decision);

    /** Completes the employee-wide work deferred by batch claim admission. */
    void completeBatchForEmployee(Long empId);

    boolean acceptReview(Long harnessLogId);

    boolean rejectReview(Long harnessLogId);
}
