package com.example.matching.integration.fault;

import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.service.matching.EmployeeVectorRecallService;
import com.example.matching.service.matching.MatchingProfileTextBuilder;
import com.example.matching.vector.MilvusVectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Milvus Failure Injection Tests")
class MilvusFailureInjectionTest {

    @Mock
    private MilvusVectorService milvusVectorService;

    private EmployeeVectorRecallService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeVectorRecallService(
                new StubObjectProvider<>(milvusVectorService),
                new MatchingProfileTextBuilder(new ObjectMapper()),
                mock(com.example.matching.service.common.VectorRecallCacheEpoch.class)
        );
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when Milvus throws RuntimeException")
    void recall_returnsEmptyMapOnMilvusRuntimeException() {
        MatchingPostProfile post = buildPost();

        when(milvusVectorService.searchEmployeesForPost(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Milvus connection pool exhausted"));

        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when Milvus throws IllegalStateException")
    void recall_returnsEmptyMapOnMilvusIllegalState() {
        MatchingPostProfile post = buildPost();

        when(milvusVectorService.searchEmployeesForPost(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("Collection not loaded"));

        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when Milvus returns null search results")
    void recall_returnsEmptyMapWhenSearchReturnsNull() {
        MatchingPostProfile post = buildPost();

        when(milvusVectorService.searchEmployeesForPost(anyString(), anyInt()))
                .thenReturn(null);

        // EmployeeVectorRecallService iterates results -- null will cause NPE caught by the catch block
        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when ObjectProvider returns null (Milvus unavailable)")
    void recall_returnsEmptyMapWhenMilvusVectorServiceIsNull() {
        EmployeeVectorRecallService serviceNoMilvus =
                new EmployeeVectorRecallService(
                        new StubObjectProvider<>(null),
                        new MatchingProfileTextBuilder(new ObjectMapper()),
                        mock(com.example.matching.service.common.VectorRecallCacheEpoch.class)
                );

        MatchingPostProfile post = buildPost();

        Map<Long, BigDecimal> result = serviceNoMilvus.recallEmployeesForPost(post, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when post is null")
    void recall_returnsEmptyMapWhenPostIsNull() {
        Map<Long, BigDecimal> result = service.recallEmployeesForPost(null, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("recallEmployeesForPost returns empty map when Milvus search throws and does not propagate exception")
    void recall_doesNotPropagateException() {
        MatchingPostProfile post = buildPost();

        when(milvusVectorService.searchEmployeesForPost(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Network unreachable"));

        assertThatNoException()
                .isThrownBy(() -> service.recallEmployeesForPost(post, 20));
    }

    @Test
    @DisplayName("recallEmployeesForPost with malformed Milvus results -- missing refId or score")
    void recall_handlesMalformedSearchResults() {
        MatchingPostProfile post = buildPost();

        when(milvusVectorService.searchEmployeesForPost(anyString(), anyInt()))
                .thenReturn(List.of(
                        Map.of("refId", "not-a-number", "score", 80.0),  // invalid refId
                        Map.of("score", 90.0),                           // missing refId
                        Map.of("refId", 1001L, "score", "bad-score"),    // invalid score
                        Map.of("refId", 1002L, "score", 85.5)            // valid
                ));

        Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

        // Only valid entry should survive
        assertThat(result).hasSize(1);
        assertThat(result).containsKey(1002L);
    }

    // ==================== helpers ====================

    private MatchingPostProfile buildPost() {
        return new MatchingPostProfile(
                1L, "P1", "Java后端工程师", null, "负责后端接口开发", null, List.of(
                        new MatchingRequirementSnapshot(10L, "Java", 3, null, null, null, null)));
    }

    private record StubObjectProvider<T>(T instance) implements ObjectProvider<T> {
        @Override public T getObject(Object... args) { return instance; }
        @Override public T getIfAvailable() { return instance; }
        @Override public T getIfUnique() { return instance; }
        @Override public T getObject() { return instance; }
    }
}
