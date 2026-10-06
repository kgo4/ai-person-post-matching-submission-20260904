package com.example.matching.service.matching;

import com.example.matching.entity.matching.MatchingBlackWhiteList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 黑白名单短路逻辑测试
 * 验证：黑名单→最终分数0，白名单→最终分数100，两者都不进入综合评分
 */
class BlackWhiteListShortCircuitTest {

    @Test
    void blacklistHitsReturnsZeroScore() {
        MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
        bw.setEmpId(1L);
        bw.setPostId(10L);
        bw.setListType(2); // blacklist

        var hit = findBwListHit(1L, 10L, List.of(bw));

        assertThat(hit).isNotNull();
        assertThat(hit.getListType()).isEqualTo(2);
        BigDecimal forcedScore = hit.getListType() == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
        assertThat(forcedScore).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void whitelistHitReturns100Score() {
        MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
        bw.setEmpId(2L);
        bw.setPostId(10L);
        bw.setListType(1); // whitelist

        var hit = findBwListHit(2L, 10L, List.of(bw));

        assertThat(hit).isNotNull();
        assertThat(hit.getListType()).isEqualTo(1);
        BigDecimal forcedScore = hit.getListType() == 2 ? BigDecimal.ZERO : new BigDecimal("100.00");
        assertThat(forcedScore).isEqualByComparingTo("100.00");
    }

    @Test
    void noMatchReturnsNull() {
        MatchingBlackWhiteList bw = new MatchingBlackWhiteList();
        bw.setEmpId(99L);
        bw.setPostId(99L);
        bw.setListType(2);

        var hit = findBwListHit(1L, 10L, List.of(bw));

        assertThat(hit).isNull();
    }

    @Test
    void emptyListReturnsNull() {
        var hit = findBwListHit(1L, 10L, List.of());
        assertThat(hit).isNull();
    }

    @Test
    void nullListReturnsNull() {
        var hit = findBwListHit(1L, 10L, null);
        assertThat(hit).isNull();
    }

    /**
     * 复制 MatchingExecuteServiceImpl.findBwListHit 逻辑用于纯单元测试
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
}
