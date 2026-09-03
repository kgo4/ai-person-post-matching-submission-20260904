package com.example.matching.agent.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonExtractorTest {

    @Test
    void stripsMarkdownFence() {
        assertEquals("{\"a\":1}",
                JsonExtractor.clean("```json\n{\"a\":1}\n```"));
    }

    @Test
    void stripsLeadingProse() {
        assertEquals("{\"a\":1}", JsonExtractor.clean("Here is the result: {\"a\":1}"));
    }

    @Test
    void repairsTrailingComma() {
        assertEquals("{\"a\":1}", JsonExtractor.clean("{\"a\":1,}"));
    }

    @Test
    void repairsSingleQuotes() {
        assertEquals("{\"a\":\"x\"}", JsonExtractor.clean("{'a':'x'}"));
    }

    @Test
    void stripsLineComments() {
        assertEquals("{\"a\":1}", JsonExtractor.clean("{\"a\":1 // comment\n}"));
    }

    @Test
    void mapsNaNToNull() {
        assertEquals("{\"a\":null}", JsonExtractor.clean("{\"a\":NaN}"));
    }

    @Test
    void balancesNestedBracketsInsideStrings() {
        assertEquals("{\"a\":\"{\\\"x\\\":1}\",\"b\":2}",
                JsonExtractor.clean("{\"a\":\"{\\\"x\\\":1}\",\"b\":2} trailing"));
    }

    @Test
    void returnsNullOnNoJson() {
        assertNull(JsonExtractor.clean("no json here"));
    }

    // ---- 以下为修复缺陷的回归测试 ----

    @Test
    void returnsNullOnMismatchedBrackets() {
        // [ 用 } 闭合：类型不匹配，无后续合法候选
        assertNull(JsonExtractor.clean("[1,2}"));
    }

    @Test
    void skipsUnbalancedPrefixBrackets() {
        // 前缀废话含不平衡 {，滑动重试应提取后续合法 JSON
        assertEquals("{\"a\":1}", JsonExtractor.clean("Error: { incomplete {\"a\":1}"));
    }

    @Test
    void preservesNaNInsideStrings() {
        assertEquals("{\"msg\":\"NaN\"}", JsonExtractor.clean("{\"msg\":\"NaN\"}"));
    }

    @Test
    void extractsJsonArrays() {
        assertEquals("[1,2,3]", JsonExtractor.clean("[1,2,3]"));
    }

    @Test
    void returnsNullForNullOrBlank() {
        assertNull(JsonExtractor.clean(null));
        assertNull(JsonExtractor.clean(""));
        assertNull(JsonExtractor.clean("   "));
    }

    @Test
    void returnsNullOnUnclosedBracket() {
        assertNull(JsonExtractor.clean("{\"a\":1"));
    }

    @Test
    void handlesEscapedSingleQuote() {
        // {'a':'it\'s'} → {"a":"it's"}：转义撇号保留原值，产物为合法 JSON
        assertEquals("{\"a\":\"it's\"}", JsonExtractor.clean("{'a':'it\\'s'}"));
    }

    @Test
    void replacesSignedInfinityWithNull() {
        // -Infinity 的符号须一并吞掉，避免产出 {"a":-null} 非法 JSON
        assertEquals("{\"a\":null}", JsonExtractor.clean("{\"a\":-Infinity}"));
        assertEquals("{\"a\":null}", JsonExtractor.clean("{\"a\":+NaN}"));
    }

    @Test
    void ignoresBracketsInsideSingleQuotedStrings() {
        // 单引号字符串内的 { 不被当作候选起点，整段作为 JSON 值提取
        assertEquals("{\"a\":\"x{\"}", JsonExtractor.clean("{'a':'x{'}"));
    }

    @Test
    void preservesUrlsAndPunctuationInsideSingleQuotes() {
        // 单引号字符串内的 // 与 ,} 不被当作注释/尾逗号处理
        assertEquals("{\"url\":\"http://x\"}", JsonExtractor.clean("{'url':'http://x'}"));
        assertEquals("{\"a\":\"x,}\"}", JsonExtractor.clean("{'a':'x,}'}"));
    }

    @Test
    void normalizesEscapedSingleQuoteInsideDoubleQuotedStrings() {
        // {"a":"it\'s"} → {"a":"it's"}：双引号字符串内的 \' 是非法 JSON 转义，
        // 须撤销反斜杠且数据保真，产物必须是合法 JSON
        String cleaned = JsonExtractor.clean("{\"a\":\"it\\'s\"}");
        assertEquals("{\"a\":\"it's\"}", cleaned);
        try {
            JsonNode node = new ObjectMapper().readTree(cleaned);
            assertEquals("it's", node.get("a").asText());
        } catch (Exception e) {
            fail("产物不是合法 JSON: " + cleaned, e);
        }
    }
}
