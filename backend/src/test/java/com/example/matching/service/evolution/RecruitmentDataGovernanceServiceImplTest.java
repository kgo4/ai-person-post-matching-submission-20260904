package com.example.matching.service.evolution;

import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.service.evolution.impl.RecruitmentDataGovernanceServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecruitmentDataGovernanceServiceImplTest {

    private final MarketJdDataMapper marketJdDataMapper = mock(MarketJdDataMapper.class);
    private final RecruitmentDataGovernanceServiceImpl service =
            new RecruitmentDataGovernanceServiceImpl(marketJdDataMapper);

    private RecruitmentDataGovernanceService.FreshnessScore freshnessFor(LocalDateTime publishedTime) {
        MarketJdData jd = new MarketJdData();
        jd.setId(1L);
        jd.setPublishedTime(publishedTime);
        when(marketJdDataMapper.selectById(1L)).thenReturn(jd);
        return service.calculateFreshnessScore(1L);
    }

    @Test
    void justPublishedJdGetsFullScoreAndFreshLevel() {
        RecruitmentDataGovernanceService.FreshnessScore freshness = freshnessFor(LocalDateTime.now());

        assertEquals(100.0, freshness.score(), 1.0);
        assertEquals("FRESH", freshness.freshnessLevel());
        assertEquals(0, freshness.daysSincePublished());
    }

    @Test
    void thirtyDaysOldJdDecaysButStaysFreshLevel() {
        RecruitmentDataGovernanceService.FreshnessScore freshness =
                freshnessFor(LocalDateTime.now().minusDays(30));

        // score = 100 * exp(-30/90) ≈ 71.65
        assertEquals(71.65, freshness.score(), 1.0);
        assertEquals("FRESH", freshness.freshnessLevel());
    }

    @Test
    void ninetyDaysOldJdFallsToMediumLevel() {
        RecruitmentDataGovernanceService.FreshnessScore freshness =
                freshnessFor(LocalDateTime.now().minusDays(90));

        // score = 100 * exp(-1) ≈ 36.79
        assertEquals(36.79, freshness.score(), 1.0);
        assertEquals("MEDIUM", freshness.freshnessLevel());
    }

    @Test
    void oldJdFallsToOldLevelWithLowScore() {
        RecruitmentDataGovernanceService.FreshnessScore freshness =
                freshnessFor(LocalDateTime.now().minusDays(200));

        // score = 100 * exp(-200/90) ≈ 10.84
        assertEquals(10.84, freshness.score(), 1.0);
        assertEquals("OLD", freshness.freshnessLevel());
    }

    @Test
    void nullPublishedTimeYieldsUnknown() {
        RecruitmentDataGovernanceService.FreshnessScore freshness = freshnessFor(null);

        assertEquals(50.0, freshness.score(), 0.001);
        assertEquals("UNKNOWN", freshness.freshnessLevel());
    }

    @Test
    void scoreDecreasesContinuouslyWithAge() {
        double d30 = freshnessFor(LocalDateTime.now().minusDays(30)).score();
        double d60 = freshnessFor(LocalDateTime.now().minusDays(60)).score();
        double d90 = freshnessFor(LocalDateTime.now().minusDays(90)).score();
        double d200 = freshnessFor(LocalDateTime.now().minusDays(200)).score();

        assertTrue(d30 > d60, "30 天应高于 60 天");
        assertTrue(d60 > d90, "60 天应高于 90 天");
        assertTrue(d90 > d200, "90 天应高于 200 天");
        assertTrue(d30 < 100.0, "非当日 JD 不应是满分");
    }
}
