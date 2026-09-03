package com.example.matching.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LlmResponseParser 语料测试")
class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser(new ObjectMapper());

    @Test
    @DisplayName("纯 JSON 直接解析")
    void parsesPureJson() {
        String json = "{\"a\":1}";
        assertThat(parser.extractJson(json)).isEqualTo(json);
    }

    @Test
    @DisplayName("```json 围栏内 JSON 被提取")
    void extractsJsonFencedBlock() {
        String raw = "以下是结果：\n```json\n{\"claims\":[{\"abilityName\":\"Java\"}]}\n```\n结束";
        assertThat(parser.extractJson(raw)).contains("\"claims\"");
    }

    @Test
    @DisplayName("无标记围栏内的 JSON 被提取")
    void extractsPlainFencedBlock() {
        String raw = "```\n{\"x\":1}\n```";
        assertThat(parser.extractJson(raw)).isEqualTo("{\"x\":1}");
    }

    @Test
    @DisplayName("散文包裹的 JSON 通过首尾大括号提取")
    void extractsProseWrappedJson() {
        String raw = "分析结果如下：{\"overall\":\"good\",\"score\":85} 请查收。";
        assertThat(parser.extractJson(raw)).isEqualTo("{\"overall\":\"good\",\"score\":85}");
    }

    @Test
    @DisplayName("JSON 后的方括号注释不能混入提取结果")
    void ignoresBracketedProseAfterJson() {
        String raw = "结果：[{\"id\":1,\"note\":\"[kept]\"}] [模型说明]";

        assertThat(parser.extractJson(raw)).isEqualTo("[{\"id\":1,\"note\":\"[kept]\"}]");
    }

    @Test
    @DisplayName("散文中的非 JSON 花括号不能遮蔽后续 JSON")
    void skipsNonJsonBracesBeforeValidJson() {
        String raw = "说明 {这不是 JSON}，实际结果是 {\"decision\":\"PASS\"}";

        assertThat(parser.extractJson(raw)).isEqualTo("{\"decision\":\"PASS\"}");
    }

    @Test
    @DisplayName("嵌套数组保持完整（balanced 提取由调用方保证，此处验证共享 parser 取首尾）")
    void nestedArrayExtraction() {
        String raw = "结果：[{\"id\":1,\"tags\":[1,2]},{\"id\":2}] 完毕";
        String json = parser.extractJson(raw);
        assertThat(json).startsWith("[").endsWith("]");
        assertThat(parser.parseArray(raw).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("多个代码围栏取第一个 JSON 围栏")
    void multipleFencesFirstJsonWins() {
        String raw = "先看这个：```json\n{\"first\":true}\n```\n再看这个：```json\n{\"second\":true}\n```";
        assertThat(parser.extractJson(raw)).contains("\"first\"");
    }

    @Test
    @DisplayName("畸形输入抛出 ModelResponseParseException")
    void malformedInputThrows() {
        assertThatThrownBy(() -> parser.extractJson("完全没有 JSON 的纯文本"))
                .isInstanceOf(ModelResponseParseException.class);
        assertThatThrownBy(() -> parser.extractJson(""))
                .isInstanceOf(ModelResponseParseException.class);
        assertThatThrownBy(() -> parser.extractJson(null))
                .isInstanceOf(ModelResponseParseException.class);
    }

    @Test
    @DisplayName("parseObject 返回 JsonNode，parseArray 校验根为数组")
    void parseObjectAndArray() {
        JsonNode node = parser.parseObject("{\"k\":\"v\"}");
        assertThat(node.get("k").asText()).isEqualTo("v");

        JsonNode arr = parser.parseArray("[1,2,3]");
        assertThat(arr.size()).isEqualTo(3);

        assertThatThrownBy(() -> parser.parseArray("{\"k\":1}"))
                .isInstanceOf(ModelResponseParseException.class);
    }
}
