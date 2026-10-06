package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewDurationPolicyTest {

    private final InterviewDurationPolicy policy = new InterviewDurationPolicy();

    @Test
    void givesScenarioAndDesignQuestionsEnoughTimeWhenNoDurationWasPersisted() {
        EmpVideoInterviewQuestion scenario = question("SCENARIO", "MEDIUM", "请描述一次复杂故障的排查过程。", null);
        EmpVideoInterviewQuestion design = question("DESIGN", "HARD", "请设计一个可扩展的服务方案。", null);

        assertThat(policy.durationForQuestion(scenario)).isEqualTo(180);
        assertThat(policy.durationForQuestion(design)).isEqualTo(180);
    }

    @Test
    void givesResumeVerificationQuestionsEnoughTimeForProjectEvidence() {
        EmpVideoInterviewQuestion verification = question(
                "VERIFICATION", "EASY", "请结合简历中的项目说明你如何使用这项能力。", null);
        EmpVideoInterviewQuestion simpleFact = question("FACT", "EASY", "说明这个概念。", null);

        assertThat(policy.durationForQuestion(verification)).isEqualTo(120);
        assertThat(policy.durationForQuestion(simpleFact)).isEqualTo(90);
    }

    @Test
    void respectsPersistedQuestionDurationWithinSafeBounds() {
        assertThat(policy.durationForQuestion(question("FACT", "EASY", "说明概念。", 240))).isEqualTo(240);
        assertThat(policy.durationForQuestion(question("FACT", "EASY", "说明概念。", 10))).isEqualTo(45);
        assertThat(policy.durationForQuestion(question("FACT", "EASY", "说明概念。", 600))).isEqualTo(300);
    }

    @Test
    void givesEvidenceSeekingFollowUpsEnoughTimeAndDoesNotShortenSecondFollowUpByOrder() {
        InterviewFollowUpQuestion star = followUp("STAR_MISSING", "action", 2);
        InterviewFollowUpQuestion clarification = followUp("UNCLEAR_ANSWER", "detail", 2);

        assertThat(policy.durationForFollowUp(star)).isEqualTo(90);
        assertThat(policy.durationForFollowUp(clarification)).isEqualTo(60);
    }

    private EmpVideoInterviewQuestion question(String type, String difficulty, String text, Integer duration) {
        EmpVideoInterviewQuestion question = new EmpVideoInterviewQuestion();
        question.setQuestionType(type);
        question.setDifficulty(difficulty);
        question.setQuestionText(text);
        question.setDurationSeconds(duration);
        return question;
    }

    private InterviewFollowUpQuestion followUp(String type, String dimension, Integer order) {
        InterviewFollowUpQuestion followUp = new InterviewFollowUpQuestion();
        followUp.setFollowUpType(type);
        followUp.setTargetDimension(dimension);
        followUp.setFollowUpOrder(order);
        return followUp;
    }
}
