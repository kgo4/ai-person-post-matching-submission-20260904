package com.example.matching.agent.lc4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingAnalysisAiServiceStructuredOutputTest {

    @Test
    void acceptsStructuredDimensionScoresWithoutNestedGenericReflectionFailure() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.aiMessage("""
                {"suggestedLlmScore":80,"conclusion":"Suitable","dimensionScores":[{"dimension":"technical","score":80,"weight":1}],"findings":[]}
                """))
                        .build();
            }
        };
        MatchingAnalysisAiService service = AiServices.builder(MatchingAnalysisAiService.class)
                .chatModel(model)
                .build();

        var response = service.analyze("{}");

        assertThat(response.getConclusion()).isEqualTo("Suitable");
        assertThat(response.getDimensionScores()).singleElement().satisfies(score -> {
            assertThat(score.getDimension()).isEqualTo("technical");
            assertThat(score.getScore()).isEqualByComparingTo("80");
            assertThat(score.getWeight()).isEqualByComparingTo("1");
        });
    }
}
