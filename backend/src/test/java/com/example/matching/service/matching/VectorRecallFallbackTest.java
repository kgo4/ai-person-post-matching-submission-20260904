package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.vector.MilvusVectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmployeeVectorRecallService} fallback behavior:
 * Milvus empty results, exceptions, non-numeric score values,
 * score/toLong parsing edge cases, and snapshot-based post profile text.
 */
@DisplayName("EmployeeVectorRecallService fallback")
class VectorRecallFallbackTest {

    private final MilvusVectorService milvusVectorService = mock(MilvusVectorService.class);
    private final com.example.matching.service.common.VectorRecallCacheEpoch epoch =
            mock(com.example.matching.service.common.VectorRecallCacheEpoch.class);
    private final EmployeeVectorRecallService service =
            new EmployeeVectorRecallService(
                    new StubObjectProvider<>(milvusVectorService),
                    new MatchingProfileTextBuilder(new ObjectMapper()),
                    epoch
            );

    private static MatchingPostProfile samplePost() {
        return new MatchingPostProfile(
                1L, "P1", "Java后端工程师", null, "负责后端接口开发", null, List.of(
                        new MatchingRequirementSnapshot(10L, "Java", 3, null, null, null, null)));
    }

    // ========== Milvus returns empty results ==========

    @Nested
    @DisplayName("Milvus returns empty results")
    class EmptyResults {

        @Test
        @DisplayName("Empty search results returns empty map")
        void emptyResults_returnsEmptyMap() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of());

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Null post returns empty map without calling Milvus")
        void nullPost_returnsEmptyMap() {
            Map<Long, BigDecimal> result = service.recallEmployeesForPost(null, 20);

            assertThat(result).isEmpty();
            verifyNoInteractions(milvusVectorService);
        }
    }

    // ========== Milvus throws exception ==========

    @Nested
    @DisplayName("Milvus throws exception")
    class MilvusException {

        @Test
        @DisplayName("RuntimeException from Milvus returns empty map, no exception propagated")
        void runtimeException_returnsEmptyMap() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenThrow(new RuntimeException("milvus unavailable"));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Connection timeout from Milvus returns empty map")
        void connectionTimeout_returnsEmptyMap() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenThrow(new RuntimeException("Connection timed out"));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("IllegalStateException from Milvus returns empty map")
        void illegalState_returnsEmptyMap() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenThrow(new IllegalStateException("Collection not found"));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }
    }

    // ========== Non-numeric score values ==========

    @Nested
    @DisplayName("Non-numeric score values")
    class NonNumericScores {

        @Test
        @DisplayName("String score that is non-numeric is skipped (parsed as null)")
        void nonNumericStringScore_skipped() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", "not_a_number"),
                            Map.of("refId", 1002L, "score", 85.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).doesNotContainKey(1001L);
            assertThat(result).containsEntry(1002L, new BigDecimal("85.00"));
        }

        @Test
        @DisplayName("Empty string score is skipped")
        void emptyStringScore_skipped() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", "")
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Blank string score is skipped")
        void blankStringScore_skipped() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", "   ")
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Null score value is skipped")
        void nullScore_skipped() {
            MatchingPostProfile post = samplePost();
            Map<String, Object> item = new HashMap<>();
            item.put("refId", 1001L);
            item.put("score", null);
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(item));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Null refId is skipped even with valid score")
        void nullRefId_skipped() {
            MatchingPostProfile post = samplePost();
            Map<String, Object> item = new HashMap<>();
            item.put("refId", null);
            item.put("score", 85.0);
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(item));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }
    }

    // ========== Score parsing ==========

    @Nested
    @DisplayName("Score parsing")
    class ScoreParsing {

        @Test
        @DisplayName("BigDecimal score is scaled to 2 decimal places")
        void bigDecimalScore_scaledToTwoPlaces() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", new BigDecimal("92.367"))
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsEntry(1001L, new BigDecimal("92.37"));
        }

        @Test
        @DisplayName("Double score is converted to BigDecimal with 2 decimal places")
        void doubleScore_convertedToBigDecimal() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", 92.36)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsEntry(1001L, new BigDecimal("92.36"));
        }

        @Test
        @DisplayName("Integer score is converted to BigDecimal")
        void integerScore_convertedToBigDecimal() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", 85)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsEntry(1001L, new BigDecimal("85.00"));
        }

        @Test
        @DisplayName("Valid numeric string score is parsed correctly")
        void validStringScore_parsedCorrectly() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", "92.50")
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsEntry(1001L, new BigDecimal("92.50"));
        }
    }

    // ========== toLong parsing ==========

    @Nested
    @DisplayName("toLong parsing")
    class ToLongParsing {

        @Test
        @DisplayName("Number refId (Long) is parsed correctly")
        void longRefId_parsedCorrectly() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", 90.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsKey(1001L);
        }

        @Test
        @DisplayName("Number refId (Integer) is parsed correctly")
        void integerRefId_parsedCorrectly() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 42, "score", 90.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsKey(42L);
        }

        @Test
        @DisplayName("Valid string refId is parsed correctly")
        void validStringRefId_parsedCorrectly() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", "1001", "score", 90.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).containsKey(1001L);
        }

        @Test
        @DisplayName("Invalid string refId is skipped")
        void invalidStringRefId_skipped() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", "not_a_number", "score", 90.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Blank string refId is skipped")
        void blankStringRefId_skipped() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", "  ", "score", 90.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Mixed valid and invalid refIds: only valid entries returned")
        void mixedRefIds_onlyValidReturned() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of(
                            Map.of("refId", 1001L, "score", 90.0),
                            Map.of("refId", "invalid", "score", 85.0),
                            Map.of("refId", 1003L, "score", 80.0)
                    ));

            Map<Long, BigDecimal> result = service.recallEmployeesForPost(post, 20);

            assertThat(result).hasSize(2);
            assertThat(result).containsKeys(1001L, 1003L);
        }
    }

    // ========== Requirement snapshot text ==========

    @Nested
    @DisplayName("Requirement snapshots feed recall text")
    class RequirementSnapshots {

        @Test
        @DisplayName("Requirement names from snapshots appear in the recall query text")
        void snapshotNames_feedRecallText() {
            MatchingPostProfile post = new MatchingPostProfile(
                    1L, "P1", "Java后端工程师", null, null, null, List.of(
                            new MatchingRequirementSnapshot(10L, "Java", 3, null, null, null, null),
                            new MatchingRequirementSnapshot(11L, "Spring", 3, null, null, null, null)));

            when(milvusVectorService.searchEmployeesForPost(contains("Spring"), anyInt()))
                    .thenReturn(List.of());

            service.recallEmployeesForPost(post, 20);
        }

        @Test
        @DisplayName("Empty requirements skip name resolution entirely")
        void emptyRequirements_skipsTagLookup() {
            MatchingPostProfile post = samplePost();
            when(milvusVectorService.searchEmployeesForPost(contains("Java"), anyInt()))
                    .thenReturn(List.of());

            service.recallEmployeesForPost(post, 20);
        }
    }

    // ========== ObjectProvider returning null ==========

    @Nested
    @DisplayName("MilvusVectorService unavailable")
    class MilvusUnavailable {

        @Test
        @DisplayName("ObjectProvider returns null: returns empty map without exception")
        void nullProvider_returnsEmptyMap() {
            EmployeeVectorRecallService serviceWithNullMilvus =
                    new EmployeeVectorRecallService(
                            new StubObjectProvider<>(null),
                            new MatchingProfileTextBuilder(new ObjectMapper()),
                            epoch
                    );

            Map<Long, BigDecimal> result = serviceWithNullMilvus.recallEmployeesForPost(samplePost(), 20);

            assertThat(result).isEmpty();
        }
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
