package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.interview.AiTestEvaluationResultDTO;
import com.example.matching.agent.dto.interview.AiTestQuestionSetDTO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiTestAiService {

    @SystemMessage(fromResource = "ai/prompt/ai-test-generate-system.txt")
    @UserMessage("""
        Generate exactly {{questionCount}} AI test questions from this context:

        {{context}}

        Follow the system output schema exactly.
        """)
    AiTestQuestionSetDTO generateQuestions(@V("context") String context, @V("questionCount") int questionCount);

    @SystemMessage(fromResource = "ai/prompt/ai-test-evaluate-system.txt")
    @UserMessage("""
        Evaluate answers (each answer max 500 chars) from this context:

        {{context}}

        Follow the system output schema exactly.
        """)
    AiTestEvaluationResultDTO evaluateAnswers(@V("context") String context);
}
