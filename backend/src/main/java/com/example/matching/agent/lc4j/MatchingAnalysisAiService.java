package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.MatchingAnalysisModelResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MatchingAnalysisAiService {

    @SystemMessage(fromResource = "ai/prompt/matching-analysis-system.txt")
    @UserMessage("""
        Analyze this person-post matching context:

        {{context}}

        Follow the system output schema exactly.
        """)
    MatchingAnalysisModelResult analyze(@V("context") String context);
}
