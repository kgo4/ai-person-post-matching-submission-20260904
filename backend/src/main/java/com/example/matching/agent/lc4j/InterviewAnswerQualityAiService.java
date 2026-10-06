package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.InterviewAnswerQualityDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.MemoryId;

public interface InterviewAnswerQualityAiService {

    @SystemMessage(fromResource = "ai/prompt/interview-answer-quality-system.txt")
    @UserMessage("""
        Evaluate this answer:

        {{context}}

        Required JSON format:
        {
          "starCompleteness": {
            "situation": true,
            "task": true,
            "action": true,
            "result": false
          },
          "specificityScore": 60,
          "evidenceScore": 55,
          "personalContributionScore": 50,
          "logicConsistencyScore": 70,
          "needFollowUp": true,
          "followUpReason": "missing quantified result",
          "targetDimension": "result",
          "suggestedFollowUpType": "STAR_MISSING",
          "missingEvidence": ["quantified result"],
          "logicRisks": [],
          "conclusion": "answer is partially supported"
        }

        `suggestedFollowUpType` allowed values only: STAR_MISSING, PERSONAL_CONTRIBUTION,
        RESUME_VERIFICATION, SCENARIO_SIMULATION. Use null when needFollowUp is false.
        """)
    InterviewAnswerQualityDTO evaluate(@MemoryId Long sessionId, @V("context") String context);
}
