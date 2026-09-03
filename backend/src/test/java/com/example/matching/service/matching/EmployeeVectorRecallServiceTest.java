package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.vector.MilvusVectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeVectorRecallServiceTest {

    private final MilvusVectorService milvusVectorService = mock(MilvusVectorService.class);
    private final com.example.matching.service.common.VectorRecallCacheEpoch epoch =
            mock(com.example.matching.service.common.VectorRecallCacheEpoch.class);
    private final EmployeeVectorRecallService service =
            new EmployeeVectorRecallService(
                    new StubObjectProvider<>(milvusVectorService),
                    new MatchingProfileTextBuilder(new ObjectMapper()),
                    epoch
            );

    @Test
    void recallsEmployeesForPostAndKeepsVectorScoreByEmployeeId() {
        MatchingRequirementSnapshot java = new MatchingRequirementSnapshot(
                10L, "Java开发", 4, null, null, null, null);
        MatchingPostProfile post = new MatchingPostProfile(
                1L, "P1", "Java后端工程师", null, "负责后端接口开发和业务系统建设", null, List.of(java));

        when(milvusVectorService.searchEmployeesForPost(contains("Java开发"), anyInt()))
                .thenReturn(List.of(
                        Map.of("refId", 1001L, "score", 92.36),
                        Map.of("refId", 1002L, "score", new BigDecimal("83.50"))
                ));

        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        assertThat(result).containsEntry(1001L, new BigDecimal("92.36"));
        assertThat(result).containsEntry(1002L, new BigDecimal("83.50"));
    }

    @Test
    void recallLoadsAllRequirementTagsFromSnapshots() {
        MatchingPostProfile post = new MatchingPostProfile(
                1L, "P1", "Backend Engineer", null, null, null, List.of(
                        new MatchingRequirementSnapshot(10L, "Java", 3, null, null, null, null),
                        new MatchingRequirementSnapshot(11L, "Spring", 3, null, null, null, null)));

        when(milvusVectorService.searchEmployeesForPost(contains("Spring"), anyInt())).thenReturn(List.of());

        service.recallEmployeesForPost(post, 20);
    }

    @Test
    void returnsEmptyRecallWhenVectorSearchFails() {
        MatchingPostProfile post = new MatchingPostProfile(
                1L, "P1", "Java后端工程师", null, null, null, List.of());

        when(milvusVectorService.searchEmployeesForPost(contains("Java后端工程师"), anyInt()))
                .thenThrow(new RuntimeException("milvus unavailable"));

        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        assertThat(result).isEmpty();
    }

    private record StubObjectProvider<T>(T instance) implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return instance;
        }

        @Override
        public T getIfAvailable() {
            return instance;
        }

        @Override
        public T getIfUnique() {
            return instance;
        }

        @Override
        public T getObject() {
            return instance;
        }
    }
}
