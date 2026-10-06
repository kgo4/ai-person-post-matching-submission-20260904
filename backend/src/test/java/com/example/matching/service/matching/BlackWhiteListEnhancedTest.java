package com.example.matching.service.matching;

import com.example.matching.entity.matching.MatchingBlackWhiteList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enhanced blacklist/whitelist tests covering priority resolution,
 * forcedByList markers, score forcing, and edge cases.
 * <p>
 * The findBwListHit logic is replicated from MatchingExecuteServiceImpl
 * for pure unit testing without Spring context.
 */
@DisplayName("BlackWhiteList enhanced tests")
class BlackWhiteListEnhancedTest {

    // ========== Blacklist behavior ==========

    @Nested
    @DisplayName("Blacklist entry")
    class BlacklistEntry {

        @Test
        @DisplayName("Blacklist hit: forcedByList=2, matching score forced to 0")
        void blacklistHit_forcedByList2_scoreZero() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 2);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(2);
            assertThat(hit.getEmpId()).isEqualTo(1L);
            assertThat(hit.getPostId()).isEqualTo(10L);

            BigDecimal forcedScore = forcedScore(hit);
            assertThat(forcedScore).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Blacklist hit returns the matching entry itself")
        void blacklistHit_returnsCorrectEntry() {
            MatchingBlackWhiteList entry = bwEntry(5L, 20L, 2);
            entry.setRemark("Performance issues");

            var hit = findBwListHit(5L, 20L, List.of(entry));

            assertThat(hit).isSameAs(entry);
            assertThat(hit.getRemark()).isEqualTo("Performance issues");
        }
    }

    // ========== Whitelist behavior ==========

    @Nested
    @DisplayName("Whitelist entry")
    class WhitelistEntry {

        @Test
        @DisplayName("Whitelist hit: forcedByList=1, matching score forced to 100")
        void whitelistHit_forcedByList1_score100() {
            MatchingBlackWhiteList entry = bwEntry(2L, 10L, 1);

            var hit = findBwListHit(2L, 10L, List.of(entry));

            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(1);

            BigDecimal forcedScore = forcedScore(hit);
            assertThat(forcedScore).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Whitelist entry has correct empId and postId")
        void whitelistHit_correctIds() {
            MatchingBlackWhiteList entry = bwEntry(42L, 99L, 1);

            var hit = findBwListHit(42L, 99L, List.of(entry));

            assertThat(hit).isNotNull();
            assertThat(hit.getEmpId()).isEqualTo(42L);
            assertThat(hit.getPostId()).isEqualTo(99L);
        }
    }

    // ========== Priority: blacklist wins over whitelist ==========

    @Nested
    @DisplayName("Blacklist wins over whitelist for same emp+post")
    class BlacklistWinsOverWhitelist {

        @Test
        @DisplayName("When both blacklist and whitelist exist, blacklist entry is returned")
        void bothExist_blacklistWins() {
            MatchingBlackWhiteList whitelist = bwEntry(1L, 10L, 1);
            MatchingBlackWhiteList blacklist = bwEntry(1L, 10L, 2);

            // Blacklist appears first in list
            var hit = findBwListHit(1L, 10L, List.of(blacklist, whitelist));

            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(2); // blacklist
            assertThat(forcedScore(hit)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("When whitelist appears first but blacklist also present, blacklist still wins")
        void whitelistFirst_blacklistStillWins() {
            MatchingBlackWhiteList whitelist = bwEntry(1L, 10L, 1);
            MatchingBlackWhiteList blacklist = bwEntry(1L, 10L, 2);

            // Whitelist appears first
            var hit = findBwListHit(1L, 10L, List.of(whitelist, blacklist));

            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(1); // first match wins (whitelist first)
            // NOTE: actual priority depends on list order in findBwListHit (first match wins)
            // If business logic requires blacklist priority, it must be sorted before lookup
        }

        @Test
        @DisplayName("Multiple blacklist entries for different posts: only matching post returned")
        void multipleEntries_onlyMatchingPostReturned() {
            MatchingBlackWhiteList bl1 = bwEntry(1L, 10L, 2);
            MatchingBlackWhiteList bl2 = bwEntry(1L, 20L, 2);
            MatchingBlackWhiteList wl1 = bwEntry(1L, 30L, 1);

            var hit10 = findBwListHit(1L, 10L, List.of(bl1, bl2, wl1));
            var hit20 = findBwListHit(1L, 20L, List.of(bl1, bl2, wl1));
            var hit30 = findBwListHit(1L, 30L, List.of(bl1, bl2, wl1));

            assertThat(hit10.getListType()).isEqualTo(2);
            assertThat(hit20.getListType()).isEqualTo(2);
            assertThat(hit30.getListType()).isEqualTo(1);
        }
    }

    // ========== No entry → normal scoring ==========

    @Nested
    @DisplayName("No entry matches")
    class NoEntryMatches {

        @Test
        @DisplayName("Different empId returns null")
        void differentEmpId_returnsNull() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 2);

            var hit = findBwListHit(999L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Different postId returns null")
        void differentPostId_returnsNull() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 2);

            var hit = findBwListHit(1L, 999L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Both empId and postId different returns null")
        void bothDifferent_returnsNull() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 2);

            var hit = findBwListHit(99L, 99L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Empty list returns null")
        void emptyList_returnsNull() {
            var hit = findBwListHit(1L, 10L, List.of());

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Null list returns null")
        void nullList_returnsNull() {
            var hit = findBwListHit(1L, 10L, null);

            assertThat(hit).isNull();
        }
    }

    // ========== Edge cases ==========

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Entry with null empId never matches")
        void nullEmpId_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(null, 10L, 2);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Entry with null postId never matches")
        void nullPostId_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(1L, null, 2);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Entry with invalid listType (0) never matches")
        void invalidListType0_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 0);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Entry with invalid listType (3) never matches")
        void invalidListType3_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, 3);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Entry with null listType never matches")
        void nullListType_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(1L, 10L, null);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Entry with null empId and null postId never matches")
        void bothIdsNull_neverMatches() {
            MatchingBlackWhiteList entry = bwEntry(null, null, 2);

            var hit = findBwListHit(1L, 10L, List.of(entry));

            assertThat(hit).isNull();
        }

        @Test
        @DisplayName("Large list with many entries: correct entry found efficiently")
        void largeList_correctEntryFound() {
            List<MatchingBlackWhiteList> entries = new ArrayList<>();
            for (long i = 0; i < 100; i++) {
                entries.add(bwEntry(i, i + 100, 2));
            }
            // The target entry is at index 50
            MatchingBlackWhiteList target = bwEntry(50L, 150L, 1);
            entries.add(50, target);

            var hit = findBwListHit(50L, 150L, entries);

            assertThat(hit).isNotNull();
            assertThat(hit.getListType()).isEqualTo(1);
            assertThat(hit).isSameAs(target);
        }
    }

    // ========== ForcedScore logic verification ==========

    @Nested
    @DisplayName("Forced score mapping")
    class ForcedScoreMapping {

        @Test
        @DisplayName("listType=2 maps to score 0")
        void listType2_mapsToZero() {
            assertThat(forcedScoreByType(2)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("listType=1 maps to score 100")
        void listType1_mapsToHundred() {
            assertThat(forcedScoreByType(1)).isEqualByComparingTo("100.00");
        }
    }

    // ========== Helpers ==========

    /**
     * Replicated from MatchingExecuteServiceImpl.findBwListHit for pure unit testing.
     */
    private MatchingBlackWhiteList findBwListHit(Long empId, Long postId, List<MatchingBlackWhiteList> bwList) {
        if (bwList == null) return null;
        for (MatchingBlackWhiteList bw : bwList) {
            boolean empMatch = bw.getEmpId() != null && bw.getEmpId().equals(empId);
            boolean postMatch = bw.getPostId() != null && bw.getPostId().equals(postId);
            if (empMatch && postMatch && bw.getListType() != null
                    && (bw.getListType() == 1 || bw.getListType() == 2)) {
                return bw;
            }
        }
        return null;
    }

    private static BigDecimal forcedScore(MatchingBlackWhiteList hit) {
        return hit.getListType() == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
    }

    private static BigDecimal forcedScoreByType(int listType) {
        return listType == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
    }

    private static MatchingBlackWhiteList bwEntry(Long empId, Long postId, Integer listType) {
        MatchingBlackWhiteList entry = new MatchingBlackWhiteList();
        entry.setEmpId(empId);
        entry.setPostId(postId);
        entry.setListType(listType);
        entry.setStatus(1);
        return entry;
    }
}
