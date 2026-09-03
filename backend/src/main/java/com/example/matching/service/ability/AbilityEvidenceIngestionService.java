package com.example.matching.service.ability;

public interface AbilityEvidenceIngestionService {

    void ingestEmployeeAbility(Long abilityId, String sourceType);

    void ingestPostAbilityModel(Long modelId, String sourceType);
}
