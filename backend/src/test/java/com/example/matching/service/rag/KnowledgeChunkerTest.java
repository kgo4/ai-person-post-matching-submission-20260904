package com.example.matching.service.rag;

import com.example.matching.service.rag.impl.SimpleKnowledgeChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkerTest {

    private SimpleKnowledgeChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new SimpleKnowledgeChunker();
    }

    @Test
    @DisplayName("长文本创建多个重叠分块")
    void chunk_longTextCreatesMultipleOverlappingChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是一段测试文本，用于验证分块器的功能是否正常工作。");
        }
        String text = sb.toString();

        List<String> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1, "长文本应该产生多个分块");
        for (String chunk : chunks) {
            assertFalse(chunk.isEmpty());
            assertTrue(chunk.length() >= 30, "每个分块长度应>=30");
        }
    }

    @Test
    @DisplayName("短文本低于30字符但作为唯一分块保留")
    void chunk_shortTextBelowMinCharsKeptAsSingleChunk() {
        String shortText = "短文本";
        List<String> chunks = chunker.chunk(shortText);

        assertFalse(chunks.isEmpty(), "短文本作为唯一分块应被保留");
        assertEquals("短文本", chunks.get(0));
    }

    @Test
    @DisplayName("空文本返回空列表")
    void chunk_emptyTextReturnsEmptyList() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    @DisplayName("正常长度文本返回一个分块")
    void chunk_normalTextReturnsSingleChunk() {
        String text = "这是一段正常长度的文本，用于测试分块器的基本功能。它应该被作为一个完整的分块返回。";
        List<String> chunks = chunker.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    @DisplayName("分块保留文本原有空格")
    void chunk_preservesOriginalWhitespace() {
        String text = "这是一段   包含多个   空白的文本，用于测试   分块器是否能正确处理。需要足够长以超过最小长度限制。";
        List<String> chunks = chunker.chunk(text);

        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue(chunk.length() > 0);
        }
    }

    @Test
    @DisplayName("超长文本分块大小合理")
    void chunk_veryLongTextHasReasonableChunkSizes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("Java是一种广泛使用的编程语言，具有跨平台、面向对象、泛型编程的特性。");
        }
        String text = sb.toString();

        List<String> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1);
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertTrue(chunks.get(i).length() <= 1000, "分块大小不应超过1000字符");
        }
    }
}
