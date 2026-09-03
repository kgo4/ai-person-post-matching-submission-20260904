package com.example.matching.dto.matching;

import java.math.BigDecimal;

/**
 * Unified match override object for blacklist/whitelist enforcement.
 * <p>
 * All matching entry points (execute, preview, detail display) MUST go through
 * this override check. It ensures consistent semantics across:
 * <ul>
 *   <li>Final score: blacklist→0, whitelist→100</li>
 *   <li>Vector score: resolved separately from override score</li>
 *   <li>Status: blacklist→4(not suitable), whitelist→1(strong match)</li>
 *   <li>Detail display: report includes override reason even for whitelist</li>
 * </ul>
 *
 * @param enforced       whether this record is under list enforcement
 * @param listType       1=whitelist, 2=blacklist
 * @param forcedScore    the score forced by the list
 * @param forceReason    human-readable reason
 */
public record MatchOverride(
        boolean enforced,
        Integer listType,
        BigDecimal forcedScore,
        String forceReason
) {
    public static final MatchOverride NONE = new MatchOverride(false, null, null, null);

    public static final BigDecimal WHITELIST_SCORE = new BigDecimal("100.00");
    public static final BigDecimal BLACKLIST_SCORE = BigDecimal.ZERO;

    public static MatchOverride whitelist() {
        return new MatchOverride(true, 1, WHITELIST_SCORE, "白名单强制通过");
    }

    public static MatchOverride blacklist() {
        return new MatchOverride(true, 2, BLACKLIST_SCORE, "黑名单排除");
    }

    public boolean isWhitelist() {
        return enforced && listType != null && listType == 1;
    }

    public boolean isBlacklist() {
        return enforced && listType != null && listType == 2;
    }

    /**
     * The match status forced by the list.
     */
    public int forcedMatchStatus() {
        if (!enforced) {
            throw new IllegalStateException("Not enforced");
        }
        return isBlacklist() ? 4 : 1;
    }
}
