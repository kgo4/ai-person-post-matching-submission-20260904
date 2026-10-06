package com.example.matching.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KnowledgeDocumentDeduplicator")
class KnowledgeDocumentDeduplicatorTest {

    private final KnowledgeDocumentDeduplicator deduplicator = new KnowledgeDocumentDeduplicator();

    @Test
    @DisplayName("相同内容不同空白/大小写/标点 -> 同一规范哈希")
    void canonicalHashIgnoresWhitespaceCaseAndPunctuation() {
        String a = "Java开发工程师需要掌握Spring Boot";
        String b = "java开发工程师需要掌握spring boot!";
        String c = "JAVA开发工程师需要掌握SPRING BOOT.";

        String hashA = deduplicator.canonicalHash(a);
        String hashB = deduplicator.canonicalHash(b);
        String hashC = deduplicator.canonicalHash(c);

        assertThat(hashA).hasSize(64).isEqualTo(hashB);
        // 标点被移除后同样规范化 -> 三者一致（这就是"仅用于比较"的语义）
        assertThat(hashA).isEqualTo(hashC);
    }

    @Test
    @DisplayName("null 内容安全处理")
    void nullContentIsSafe() {
        assertThat(deduplicator.canonicalHash(null)).hasSize(64);
        assertThat(deduplicator.canonicalize(null)).isEmpty();
    }

    @Test
    @DisplayName("来源分组：POST_ABILITY_MODEL/JD_IMPORT/POST_PROTOTYPE -> POST_REQUIREMENT")
    void postRequirementGrouping() {
        assertThat(deduplicator.sourceGroup("POST_ABILITY_MODEL")).isEqualTo("POST_REQUIREMENT");
        assertThat(deduplicator.sourceGroup("JD_IMPORT")).isEqualTo("POST_REQUIREMENT");
        assertThat(deduplicator.sourceGroup("POST_PROTOTYPE")).isEqualTo("POST_REQUIREMENT");
    }

    @Test
    @DisplayName("来源分组：证据类 -> EVIDENCE；学习资源 -> LEARNING")
    void evidenceAndLearningGrouping() {
        assertThat(deduplicator.sourceGroup("CONTEST_EVIDENCE")).isEqualTo("EVIDENCE");
        assertThat(deduplicator.sourceGroup("EMP_ABILITY")).isEqualTo("EVIDENCE");
        assertThat(deduplicator.sourceGroup("LEARNING_RESOURCE")).isEqualTo("LEARNING");
    }

    @Test
    @DisplayName("来源分组：未知类型 -> GENERAL")
    void unknownGrouping() {
        assertThat(deduplicator.sourceGroup("MANUAL_TEXT")).isEqualTo("GENERAL");
        assertThat(deduplicator.sourceGroup(null)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("不同来源分组允许相同文本（跨组不视为重复）")
    void sameTextDifferentGroupsAreNotDuplicates() {
        String content = "团队协作能力";
        assertThat(deduplicator.sourceGroup("LEARNING_RESOURCE"))
                .isNotEqualTo(deduplicator.sourceGroup("JD_IMPORT"));
        // 两组相同文本的哈希一致，但分组不同 -> 不作为规范重复
        String hash = deduplicator.canonicalHash(content);
        assertThat(hash).isEqualTo(deduplicator.canonicalHash(content));
    }
}
