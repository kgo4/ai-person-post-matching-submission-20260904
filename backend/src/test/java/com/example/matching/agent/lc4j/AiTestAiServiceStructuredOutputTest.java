package com.example.matching.agent.lc4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiTestAiServiceStructuredOutputTest {

    @Test
    void parsesQuestionSetObjectFromTheModelResponse() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.aiMessage("""
                {"questions":[{"question":"What is dependency injection?","type":"SHORT_ANSWER","difficulty":"EASY","options":[],"referenceAnswer":"Dependencies are supplied by a container.","score":5,"tagId":1,"sourceRefs":["fact:POST_ABILITY_MODEL:1"]}]}
                """))
                        .build();
            }
        };
        AiTestAiService service = AiServices.builder(AiTestAiService.class)
                .chatModel(model)
                .build();

        var response = service.generateQuestions("{}", 1);

        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getQuestions().get(0).getQuestion())
                .isEqualTo("What is dependency injection?");
    }
}
