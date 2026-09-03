package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.InterviewFollowUpQuestionDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.MemoryId;

public interface InterviewFollowUpAiService {

    @SystemMessage(fromResource = "ai/prompt/interview-followup-system.txt")
    @UserMessage("""
        Generate one follow-up question from this context:

        {{context}}

        Follow the system output schema exactly.
        """)
    InterviewFollowUpQuestionDTO generate(@MemoryId Long sessionId, @V("context") String context);
}
