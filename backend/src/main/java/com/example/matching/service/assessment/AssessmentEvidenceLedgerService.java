package com.example.matching.service.assessment;

import com.example.matching.entity.ability.PersonAbilityClaim;

/** Writes auditable assessment evidence without projecting it to a formal profile. */
public interface AssessmentEvidenceLedgerService {
    void record(PersonAbilityClaim claim, Long assessmentAbilityId, Long canonicalTagId, Long questionId);
}
