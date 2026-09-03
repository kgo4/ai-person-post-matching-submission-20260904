package com.example.matching.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonNodeValueReader")
class JsonNodeValueReaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("getText")
    class GetText {
        @Test
        void returnsTextValue() {
            JsonNode node = mapper.createObjectNode().put("name", "hello");
            assertThat(JsonNodeValueReader.getText(node, "name")).isEqualTo("hello");
        }

        @Test
        void returnsNullForMissing() {
            JsonNode node = mapper.createObjectNode();
            assertThat(JsonNodeValueReader.getText(node, "missing")).isNull();
        }

        @Test
        void returnsNullForNullValue() {
            JsonNode node = mapper.createObjectNode().set("name", NullNode.getInstance());
            assertThat(JsonNodeValueReader.getText(node, "name")).isNull();
        }
    }

    @Nested
    @DisplayName("getInt")
    class GetInt {
        @Test
        void returnsIntValue() {
            JsonNode node = mapper.createObjectNode().put("level", 5);
            assertThat(JsonNodeValueReader.getInt(node, "level")).isEqualTo(5);
        }

        @Test
        void parsesTextualInt() {
            JsonNode node = mapper.createObjectNode().put("level", "3");
            assertThat(JsonNodeValueReader.getInt(node, "level")).isEqualTo(3);
        }

        @Test
        void clampsToMin() {
            JsonNode node = mapper.createObjectNode().put("level", -1);
            assertThat(JsonNodeValueReader.getInt(node, "level", 0, 5)).isEqualTo(0);
        }

        @Test
        void clampsToMax() {
            JsonNode node = mapper.createObjectNode().put("level", 10);
            assertThat(JsonNodeValueReader.getInt(node, "level", 0, 5)).isEqualTo(5);
        }

        @Test
        void returnsNullForNull() {
            JsonNode node = mapper.createObjectNode().set("level", NullNode.getInstance());
            assertThat(JsonNodeValueReader.getInt(node, "level")).isNull();
        }

        @Test
        void returnsNullForInvalidText() {
            JsonNode node = mapper.createObjectNode().put("level", "abc");
            assertThat(JsonNodeValueReader.getInt(node, "level")).isNull();
        }

        @Test
        void returnsNullForBoolean() {
            JsonNode node = mapper.createObjectNode().put("flag", true);
            assertThat(JsonNodeValueReader.getInt(node, "flag")).isNull();
        }
    }

    @Nested
    @DisplayName("getBigDecimal")
    class GetBigDecimal {
        @Test
        void returnsDecimal() {
            JsonNode node = mapper.createObjectNode().put("score", 75.5);
            assertThat(JsonNodeValueReader.getBigDecimal(node, "score"))
                    .isEqualByComparingTo(new BigDecimal("75.5"));
        }

        @Test
        void parsesTextualDecimal() {
            JsonNode node = mapper.createObjectNode().put("score", "80.5");
            assertThat(JsonNodeValueReader.getBigDecimal(node, "score"))
                    .isEqualByComparingTo(new BigDecimal("80.5"));
        }

        @Test
        void clampsToZeroAndHundred() {
            JsonNode lowNode = mapper.createObjectNode().put("score", -5);
            JsonNode highNode = mapper.createObjectNode().put("score", 150);
            assertThat(JsonNodeValueReader.getScore(lowNode, "score"))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(JsonNodeValueReader.getScore(highNode, "score"))
                    .isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        void returnsNullForNull() {
            JsonNode node = mapper.createObjectNode().set("score", NullNode.getInstance());
            assertThat(JsonNodeValueReader.getBigDecimal(node, "score")).isNull();
        }

        @Test
        void returnsNullForInvalidText() {
            JsonNode node = mapper.createObjectNode().put("score", "not-a-number");
            assertThat(JsonNodeValueReader.getBigDecimal(node, "score")).isNull();
        }
    }

    @Nested
    @DisplayName("getBoolean")
    class GetBoolean {
        @Test
        void returnsTrue() {
            JsonNode node = mapper.createObjectNode().put("required", true);
            assertThat(JsonNodeValueReader.getBoolean(node, "required")).isTrue();
        }

        @Test
        void returnsFalse() {
            JsonNode node = mapper.createObjectNode().put("required", false);
            assertThat(JsonNodeValueReader.getBoolean(node, "required")).isFalse();
        }

        @Test
        void parsesTextualTrue() {
            assertThat(JsonNodeValueReader.getBoolean(
                    mapper.createObjectNode().put("v", "true"), "v")).isTrue();
            assertThat(JsonNodeValueReader.getBoolean(
                    mapper.createObjectNode().put("v", "1"), "v")).isTrue();
            assertThat(JsonNodeValueReader.getBoolean(
                    mapper.createObjectNode().put("v", "yes"), "v")).isTrue();
        }

        @Test
        void parsesTextualFalse() {
            assertThat(JsonNodeValueReader.getBoolean(
                    mapper.createObjectNode().put("v", "false"), "v")).isFalse();
            assertThat(JsonNodeValueReader.getBoolean(
                    mapper.createObjectNode().put("v", "0"), "v")).isFalse();
        }

        @Test
        void returnsNullForUnknownText() {
            JsonNode node = mapper.createObjectNode().put("v", "maybe");
            assertThat(JsonNodeValueReader.getBoolean(node, "v")).isNull();
        }

        @Test
        void returnsNullForNull() {
            JsonNode node = mapper.createObjectNode().set("v", NullNode.getInstance());
            assertThat(JsonNodeValueReader.getBoolean(node, "v")).isNull();
        }
    }
}
