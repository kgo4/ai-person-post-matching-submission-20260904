package com.example.matching.service.rag;

import com.example.matching.service.rag.impl.SimpleKnowledgeChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpleKnowledgeChunker 来源分块配置")
class ChunkingProfileTest {

    private final SimpleKnowledgeChunker chunker = new SimpleKnowledgeChunker();

    @Test
    @DisplayName("JD 配置: 600 字窗口且保留句子边界")
    void jdProfileUses600CharWindow() {
        String longText = "岗位职责要求候选人熟练掌握Java并发编程、JVM调优、Spring Cloud微服务架构设计与实践。".repeat(30);
        List<String> chunks = chunker.chunk(longText, ChunkingProfile.JD);

        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(600);
        }
        // 边界保留句号结尾
        assertThat(chunks.get(0)).endsWith("。");
    }

    @Test
    @DisplayName("证据配置: 400 字窗口")
    void evidenceProfileUses400CharWindow() {
        String longText = "员工在项目中使用Redis实现了缓存一致性保障，并通过压测验证了方案的有效性。".repeat(25);
        List<String> chunks = chunker.chunk(longText, ChunkingProfile.EVIDENCE);

        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(400);
        }
    }

    @Test
    @DisplayName("学习资源配置: 700 字窗口")
    void learningProfileUses700CharWindow() {
        String longText = "本课程系统讲解Kubernetes容器编排、服务网格与可观测性的最佳实践。".repeat(25);
        List<String> chunks = chunker.chunk(longText, ChunkingProfile.LEARNING);

        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(700);
        }
    }

    @Test
    @DisplayName("通用配置: 800 字窗口（默认行为不变）")
    void generalProfileIsDefault() {
        String longText = "这是一段用于验证通用分块配置的文本内容，包含足够多的字数来触发滑动窗口切分逻辑。".repeat(30);
        List<String> defaultChunks = chunker.chunk(longText);
        List<String> generalChunks = chunker.chunk(longText, ChunkingProfile.GENERAL);

        assertThat(defaultChunks).isEqualTo(generalChunks);
    }

    @Test
    @DisplayName("短文本不切分")
    void shortTextNotSplit() {
        String shortText = "熟悉Java基础语法。";
        assertThat(chunker.chunk(shortText, ChunkingProfile.JD)).containsExactly(shortText);
    }
}
