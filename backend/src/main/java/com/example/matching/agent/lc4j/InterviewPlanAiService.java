package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.InterviewPlanDTO;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewPlanAiService {

    @SystemMessage(fromResource = "ai/prompt/interview-plan-system.txt")
    @UserMessage("""
        Generate an interview plan from this context:

        {{context}}

        Follow the system output schema exactly.
        """)
    InterviewPlanDTO generatePlan(@MemoryId Long sessionId, @V("context") String context);
}
