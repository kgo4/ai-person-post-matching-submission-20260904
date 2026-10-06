package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PmsAbilityAnalysisAiService {

    @SystemMessage(fromResource = "ai/prompt/pms-ability-analysis-system.txt")
    @UserMessage("""
        Extract ability claims from this PMS context:

        {{context}}

        Follow the system output schema exactly.
        """)
    PersonAbilityExtractionResult extractAbilities(@V("context") String context);
}
