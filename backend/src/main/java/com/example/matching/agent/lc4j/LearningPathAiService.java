package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.LearningPathAgentResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LearningPathAiService {

    @SystemMessage(fromResource = "ai/prompt/learning-path-system.txt")
    @UserMessage("""
        Generate a learning path from this person-post matching context:

        {{context}}

        Follow the system output schema exactly.
        """)
    LearningPathAgentResult generatePath(@V("context") String context);
}
