package com.example.matching.service.interview.impl;

import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.service.interview.AIInterviewAgent;
import com.example.matching.service.interview.eligibility.InterviewEligibilityChecker;
import com.example.matching.service.interview.plan.InterviewPlanComposer;
import com.example.matching.service.interview.observation.InterviewObservationProcessor;
import com.example.matching.service.interview.report.CompetencyReportAssembler;
import com.example.matching.service.interview.persistence.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIInterviewAgentImpl implements AIInterviewAgent {

    private final InterviewEligibilityChecker eligibilityChecker;
    private final InterviewPlanComposer planComposer;
    private final InterviewObservationProcessor observationProcessor;
    private final CompetencyReportAssembler reportAssembler;
    private final InterviewSessionRepository sessionRepository;

    @Override
    public InterviewEligibilityCheck checkInterviewEligibility(Long empId) {
        return eligibilityChecker.checkInterviewEligibility(empId);
    }

    @Override
    public InterviewPlan generateInterviewPlan(InterviewPlanRequest request) {
        try {
            return planComposer.generateInterviewPlan(request);
        } catch (InterviewPlanComposer.PlanAlreadyExistsException e) {
            return e.existingPlan;
        }
    }

    @Override
    public List<InterviewAbilityObservation> conductInterviewAndObserve(Long sessionId) {
        return observationProcessor.conductInterviewAndObserve(sessionId);
    }

    @Override
    public CompetencyReport generateCompetencyReport(Long sessionId) {
        return reportAssembler.generateCompetencyReport(sessionId);
    }
}
