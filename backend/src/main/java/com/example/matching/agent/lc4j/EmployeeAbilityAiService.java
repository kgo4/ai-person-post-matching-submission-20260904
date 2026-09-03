package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.EmployeeAbilityAgentResult;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Employee ability AI service for LangChain4j AiServices.
 */
public interface EmployeeAbilityAiService {

    @SystemMessage(fromResource = "ai/prompt/employee-ability-system.txt")
    @UserMessage("""
        Analyze this employee ability profile context and return JSON:

        {{context}}

        Follow the system output schema exactly.
        """)
    EmployeeAbilityAgentResult analyze(@V("context") String context);

    @SystemMessage(fromResource = "ai/prompt/employee-ability-extract-system.txt")
    @UserMessage("""
        Extract employee ability claims from the sourceText field in this JSON context:

        {{context}}

        Follow the system output schema exactly.
        """)
    PersonAbilityExtractionResult extractAbilities(@V("context") String context);
}
