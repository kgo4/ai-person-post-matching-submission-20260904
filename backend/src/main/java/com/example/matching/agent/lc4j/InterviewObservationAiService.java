package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.InterviewObservationDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.MemoryId;

public interface InterviewObservationAiService {

    @SystemMessage(fromResource = "ai/prompt/interview-observation-system.txt")
    @UserMessage("""
        Extract ability observations from this interview context:

        {{context}}

        Follow the system output schema exactly.
        """)
    InterviewObservationDTO extractObservations(@MemoryId Long sessionId, @V("context") String context);
}
