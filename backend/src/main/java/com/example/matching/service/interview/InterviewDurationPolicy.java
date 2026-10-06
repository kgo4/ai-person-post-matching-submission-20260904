package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Server-owned answer-window policy. A model never controls a candidate's time limit.
 */
@Component
public class InterviewDurationPolicy {

    private static final int MIN_QUESTION_SECONDS = 45;
    private static final int MAX_QUESTION_SECONDS = 300;
    private static final int MIN_FOLLOW_UP_SECONDS = 45;
    private static final int MAX_FOLLOW_UP_SECONDS = 120;
    private static final Set<String> DEEP_QUESTION_TYPES = Set.of(
            "SCENARIO", "CASE", "DESIGN", "COMPREHENSIVE", "PRACTICAL");
    private static final Set<String> EXPERIENCE_QUESTION_TYPES = Set.of(
            "BEHAVIORAL", "EXPERIENCE");
    private static final Set<String> EVIDENCE_VERIFICATION_QUESTION_TYPES = Set.of(
            "VERIFICATION", "RESUME_VERIFICATION");

    public int durationForQuestion(EmpVideoInterviewQuestion question) {
        if (question == null) {
            return 120;
        }
        if (question.getDurationSeconds() != null) {
            return clamp(question.getDurationSeconds(), MIN_QUESTION_SECONDS, MAX_QUESTION_SECONDS);
        }

        int duration = switch (normalize(question.getDifficulty())) {
            case "EASY" -> 90;
            case "HARD" -> 180;
            default -> 120;
        };
        String questionType = normalize(question.getQuestionType());
        if (DEEP_QUESTION_TYPES.contains(questionType)) {
            duration = Math.max(duration, 180);
        } else if (EVIDENCE_VERIFICATION_QUESTION_TYPES.contains(questionType)) {
            // Resume-anchored verification requires a concrete project, contribution and result.
            duration = Math.max(duration, 120);
        } else if (EXPERIENCE_QUESTION_TYPES.contains(questionType)) {
            duration = Math.max(duration, 120);
        }
        int textLength = question.getQuestionText() == null ? 0 : question.getQuestionText().length();
        if (textLength > 180) {
            duration += Math.min(60, ((textLength - 1) / 180) * 30);
        }
        return clamp(duration, MIN_QUESTION_SECONDS, MAX_QUESTION_SECONDS);
    }

    public int durationForFollowUp(InterviewFollowUpQuestion followUp) {
        if (followUp != null && followUp.getDurationSeconds() != null) {
            return clamp(followUp.getDurationSeconds(), MIN_FOLLOW_UP_SECONDS, MAX_FOLLOW_UP_SECONDS);
        }
        String type = followUp == null ? "" : normalize(followUp.getFollowUpType());
        String dimension = followUp == null ? "" : normalize(followUp.getTargetDimension());
        if ("RESUME_VERIFICATION".equals(type)
                || "PERSONAL_CONTRIBUTION".equals(type)
                || "SCENARIO".equals(dimension)
                || "PROBLEM_SOLVING".equals(dimension)) {
            return 120;
        }
        if ("STAR_MISSING".equals(type) || "ACTION".equals(dimension) || "RESULT".equals(dimension)) {
            return 90;
        }
        return 60;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
