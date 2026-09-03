package com.example.matching.event;

public record LearningProjectApprovedEvent(
        Long submissionId,
        Long empId,
        Long tagId,
        String abilityName,
        Integer beforeLevel,
        Integer confirmedLevel
) {
}
