package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface EvidenceGovernanceAiService {

    @SystemMessage(fromResource = "ai/prompt/evidence-governance-system.txt")
    @UserMessage("""
        Review this claim and evidence context:

        {{context}}

        Follow the system output schema exactly.
        """)
    EvidenceGovernanceAgentResult review(@V("context") String context);
}
