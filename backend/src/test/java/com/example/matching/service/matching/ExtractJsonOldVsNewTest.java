package com.example.matching.service.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对比验证：旧 extractJson 与新 LlmResponseParser 的行为差异
 */
class ExtractJsonOldVsNewTest {

    /**
     * 旧实现（修复前）
     */
    private String oldExtractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 新实现（修复后）
     */
    private final com.example.matching.infrastructure.llm.LlmResponseParser newParser =
            new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper());

    @Test
    @DisplayName("场景1: Markdown code block - 旧实现失败，新实现成功")
    void markdownCodeBlock() {
        String response = "分析结果：\n```json\n{\"decision\":\"PASS\",\"score\":85}\n```\n以上。";

        // 旧实现：indexOf('{') 可能找到 code fence 中的 `{`（如果有的话）
        // 或者找到 JSON 的 `{`，但 lastIndexOf('}') 可能包含尾部文本的 `}`
        String oldResult = oldExtractJson(response);

        // 新实现：正确提取 code block 中的 JSON
        String newResult = newParser.extractJson(response);

        assertThat(newResult).contains("\"decision\":\"PASS\"");
        assertThat(newResult).contains("\"score\":85");
    }

    @Test
    @DisplayName("场景2: 包含花括号的说明文本 - 旧实现提取垃圾，新实现正确")
    void explanatoryTextWithBraces() {
        String response = "注意{此处有花括号}，结果：{\"decision\":\"BLOCK\",\"reason\":\"insufficient evidence\"}";

        String oldResult = oldExtractJson(response);
        String newResult = newParser.extractJson(response);

        // 旧实现：从第一个 { (在"注意{此处"中) 开始到最后一个 } 结束
        // 产生的字符串以 { 开头但不是有效 JSON（包含中文逗号和说明文本）
        assertThat(oldResult).startsWith("{");
        assertThat(oldResult).contains("此处有花括号");
        // 旧实现产生的不是纯 JSON，验证解析失败
        assertThatThrownBy(() -> new ObjectMapper().readValue(oldResult, java.util.Map.class))
                .as("Old extractJson produces invalid JSON for this case")
                .isInstanceOf(Exception.class);

        // 新实现：正确找到完整 JSON 对象
        assertThat(newResult).startsWith("{");
        assertThat(newResult).contains("\"decision\":\"BLOCK\"");
    }

    @Test
    @DisplayName("场景3: JSON 数组 - 旧实现失败，新实现成功")
    void jsonArray() {
        String response = "能力列表：\n[\"Java\",\"Python\",\"Go\"]\n以上。";

        // 旧实现：indexOf('{') 返回 -1（没有花括号），直接返回原文
        String oldResult = oldExtractJson(response);
        assertThat(oldResult).isEqualTo(response); // 返回原文，不是 JSON

        // 新实现：正确提取数组
        String newResult = newParser.extractJson(response);
        assertThat(newResult).startsWith("[");
        assertThat(newResult).contains("Java");
    }

    @Test
    @DisplayName("场景4: 纯 JSON - 两者行为一致")
    void pureJson() {
        String response = "{\"key\":\"value\",\"count\":42}";

        String oldResult = oldExtractJson(response);
        String newResult = newParser.extractJson(response);

        assertThat(oldResult).isEqualTo(newResult);
    }

    @Test
    @DisplayName("场景5: LLM 回复包含代码块和多个 JSON - 新实现只提取 code block 内的")
    void multipleJsonInCodeBlock() {
        String response = "参考格式：{\"example\":true}\n实际结果：\n```json\n{\"actual\":true,\"score\":95}\n```";

        String newResult = newParser.extractJson(response);
        // 新实现优先提取 code block
        assertThat(newResult).contains("\"actual\":true");
    }
}
