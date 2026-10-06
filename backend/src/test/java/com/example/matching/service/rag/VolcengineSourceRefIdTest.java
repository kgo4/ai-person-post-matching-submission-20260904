package com.example.matching.service.rag;

import com.example.matching.service.rag.impl.VolcengineKnowledgeSearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M25 行为测试：火山 Provider 将云端响应携带的来源引用 ID 写入 metadata，
 * 云端无法提供时显式写入 null（不伪造）；sourceRefIdOrNull 支持 String 数字解析，
 * 使云知识命中可回链业务实体。
 */
class VolcengineSourceRefIdTest {

    @Test
    void writesSourceRefIdFromItemMapWhenPresent() throws Exception {
        List<KnowledgeSearchHit> hits = parseHits(
                "{\"data\":{\"result\":[{\"id\":\"p1\",\"content\":\"内容\",\"source_ref_id\":\"101\"}]}}");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceRefIdOrNull()).isEqualTo(101L);
    }

    @Test
    void writesNullSourceRefIdWhenCloudCannotProvide() throws Exception {
        List<KnowledgeSearchHit> hits = parseHits("{\"data\":{\"result\":[{\"id\":\"p1\",\"content\":\"内容\"}]}}");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceRefIdOrNull()).isNull();
    }

    @Test
    void parsesStringSourceRefIdToLong() {
        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                "p1", "volcengine-doc", "VOLCENGINE_KB", "t", "c", 0.9f,
                java.util.Map.of("sourceRefId", "202"), 0.9d, "RERANK", "RANK_BASED", 0.1d);

        assertThat(hit.sourceRefIdOrNull()).isEqualTo(202L);
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeSearchHit> parseHits(String responseJson) throws Exception {
        VolcengineKnowledgeSearchProvider provider = new VolcengineKnowledgeSearchProvider(
                null, new com.fasterxml.jackson.databind.ObjectMapper(), null);
        Method method = VolcengineKnowledgeSearchProvider.class
                .getDeclaredMethod("parseSearchHits", String.class);
        method.setAccessible(true);
        return (List<KnowledgeSearchHit>) method.invoke(provider, responseJson);
    }
}
