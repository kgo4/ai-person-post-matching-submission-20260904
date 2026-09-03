package com.example.matching.service.interview.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpVideoInterviewAbility;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.mapper.employee.EmpVideoInterviewAbilityMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.service.interview.AIInterviewAgent.InterviewQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewSessionRepository {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final EmpVideoInterviewAbilityMapper abilityMapper;
    private final InterviewAbilityObservationMapper observationMapper;
    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;

    private static final String MODE_POST_BASED = "POST_BASED";

    public EmpVideoInterviewSession findOrCreateSession(Long empId, Long postId) {
        EmpVideoInterviewSession existingSession = sessionMapper.selectOne(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .eq(EmpVideoInterviewSession::getEmpId, empId)
                        .eq(EmpVideoInterviewSession::getPostId, postId)
                        .in(EmpVideoInterviewSession::getStatus, 0, 1)
                        .orderByDesc(EmpVideoInterviewSession::getCreatedTime)
                        .last("LIMIT 1")
        );
        if (existingSession != null) {
            return existingSession;
        }

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setEmpId(empId);
        session.setPostId(postId);
        session.setInterviewMode(MODE_POST_BASED);
        session.setStatus(0);
        Long currentUserId = com.example.matching.utils.SecurityUtils.getCurrentUserId();
        session.setCreatedBy(currentUserId != null && currentUserId > 0 ? currentUserId : empId);
        session.setCreatedTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    public void saveQuestions(Long sessionId, List<InterviewQuestion> questions) {
        for (InterviewQuestion question : questions) {
            EmpVideoInterviewQuestion dbQuestion = new EmpVideoInterviewQuestion();
            dbQuestion.setSessionId(sessionId);
            dbQuestion.setQuestionOrder(question.order());
            dbQuestion.setQuestionText(question.text());
            dbQuestion.setQuestionType(question.type());
            dbQuestion.setDifficulty(question.difficulty());
            dbQuestion.setExpectedTagsJson(toJson(question.expectedTagIds()));
            questionMapper.insert(dbQuestion);
        }
    }

    public List<EmpVideoInterviewQuestion> findExistingQuestions(Long sessionId) {
        return questionMapper.selectList(
                Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
        );
    }

    public void upsertObservation(InterviewAbilityObservation observation) {
        InterviewAbilityObservation existing = observationMapper.selectOne(
                Wrappers.<InterviewAbilityObservation>lambdaQuery()
                        .eq(InterviewAbilityObservation::getSessionId, observation.getSessionId())
                        .eq(InterviewAbilityObservation::getTagId, observation.getTagId())
                        .eq(InterviewAbilityObservation::getIsDeleted, 0)
        );
        if (existing != null) {
            observation.setId(existing.getId());
            observationMapper.updateById(observation);
        } else {
            observationMapper.insert(observation);
        }
    }

    public List<InterviewAbilityObservation> findObservationsBySession(Long sessionId) {
        return observationMapper.selectList(
                Wrappers.<InterviewAbilityObservation>lambdaQuery()
                        .eq(InterviewAbilityObservation::getSessionId, sessionId)
                        .eq(InterviewAbilityObservation::getIsDeleted, 0)
        );
    }

    public List<InterviewFollowUpQuestion> findFollowUpsBySession(Long sessionId) {
        return followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getIsDeleted, 0)
        );
    }

    public List<EmpVideoInterviewAbility> findAbilitiesBySession(Long sessionId) {
        return abilityMapper.selectList(
                Wrappers.<EmpVideoInterviewAbility>lambdaQuery()
                        .eq(EmpVideoInterviewAbility::getSessionId, sessionId)
        );
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
