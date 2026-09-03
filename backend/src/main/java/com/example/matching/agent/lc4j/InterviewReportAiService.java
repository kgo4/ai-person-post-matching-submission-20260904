package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.InterviewReportDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.MemoryId;

public interface InterviewReportAiService {

    @SystemMessage(fromResource = "ai/prompt/interview-report-system.txt")
    @UserMessage("""
        Generate report text from this context:

        {{context}}

        Follow the system output schema exactly.
        """)
    InterviewReportDTO generateReport(@MemoryId Long sessionId, @V("context") String context);
}
