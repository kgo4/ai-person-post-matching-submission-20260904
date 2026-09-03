package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.PostAbilityAgentResult;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PostAbilityAiService {

    @SystemMessage(fromResource = "ai/prompt/post-ability-system.txt")
    @UserMessage("""
        Analyze this post ability model context:

        {{context}}

        Follow the system output schema exactly.
        """)
    PostAbilityAgentResult analyze(@V("context") String context);

    @SystemMessage(fromResource = "ai/prompt/post-ability-extract-system.txt")
    @UserMessage("""
        Extract post ability claims from this source context:

        {{context}}

        Follow the system output schema exactly.
        """)
    PostAbilityExtractionResult extractAbilities(@V("context") String context);
}
