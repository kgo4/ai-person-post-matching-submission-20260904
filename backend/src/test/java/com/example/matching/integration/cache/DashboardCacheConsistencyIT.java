package com.example.matching.integration.cache;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.DashboardService;
import com.example.matching.service.matching.MatchingRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying Dashboard cache consistency with real Redis.
 * <p>
 * Validates that getDashboardStats() result is cached, and that mutations
 * (modifyResult, deleteRecord) properly evict the dashboard cache so the
 * next call returns fresh data from the database.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DashboardCacheConsistencyIT extends AbstractIntegrationTest {

    private static final Long RECORD_ID = 999002L;
    private static final Long EMP_ID = 99902L;
    private static final Long POST_ID = 99902L;

    @Autowired private DashboardService dashboardService;
    @Autowired private MatchingRecordService matchingRecordService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM matching_approval_flow WHERE matching_record_id = ?",
                RECORD_ID);
        jdbcTemplate.update(
                "DELETE FROM matching_feedback_dataset WHERE matching_record_id = ?",
                RECORD_ID);
        jdbcTemplate.update(
                "DELETE FROM matching_record WHERE id = ?", RECORD_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:dashboard:stats*"));
        redisTemplate.delete(redisTemplate.keys("matching:v2:matching:record*"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM matching_approval_flow WHERE matching_record_id = ?",
                RECORD_ID);
        jdbcTemplate.update(
                "DELETE FROM matching_feedback_dataset WHERE matching_record_id = ?",
                RECORD_ID);
        jdbcTemplate.update(
                "DELETE FROM matching_record WHERE id = ?", RECORD_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:dashboard:stats*"));
        redisTemplate.delete(redisTemplate.keys("matching:v2:matching:record*"));
    }

    private boolean dashboardCacheExists() {
        String redisKey = "matching:v2:" + RedisCacheNames.DASHBOARD_STATS + "::all";
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    // -- tests ----------------------------------------------------------------

    @Test
    @DisplayName("getDashboardStats populates cache; modifyResult evicts dashboard cache")
    void dashboardCache_modifyResultEvicts() {
        // 1. seed a record so stats are non-trivial
        jdbcTemplate.update(
                "INSERT INTO matching_record (id, batch_no, emp_id, post_id, ai_match_score, match_status, is_locked, is_deleted, version) "
                        + "VALUES (?, 'BATCH-DB', ?, ?, 80.00, 2, 0, 0, 0)",
                RECORD_ID, EMP_ID, POST_ID);

        // 2. populate cache
        Map<String, Object> stats = dashboardService.getDashboardStats();
        assertThat(stats).isNotEmpty();
        assertThat(dashboardCacheExists()).isTrue();

        long originalCount = (long) stats.get("recordCount");

        // 3. modify a record -> evicts dashboard cache
        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("95.00"));
        matchingRecordService.modifyResult(RECORD_ID, update);

        assertThat(dashboardCacheExists()).isFalse();

        // 4. next read hits DB and returns fresh stats
        Map<String, Object> freshStats = dashboardService.getDashboardStats();
        assertThat(freshStats).isNotEmpty();
        // record count should be the same (record was updated, not created)
        assertThat((long) freshStats.get("recordCount")).isEqualTo(originalCount);
    }

    @Test
    @DisplayName("getDashboardStats populates cache; deleteRecord evicts dashboard cache")
    void dashboardCache_deleteRecordEvicts() {
        // 1. seed a record
        jdbcTemplate.update(
                "INSERT INTO matching_record (id, batch_no, emp_id, post_id, ai_match_score, match_status, is_locked, is_deleted, version) "
                        + "VALUES (?, 'BATCH-DB2', ?, ?, 70.00, 1, 0, 0, 0)",
                RECORD_ID, EMP_ID, POST_ID);

        // 2. populate cache
        Map<String, Object> before = dashboardService.getDashboardStats();
        assertThat(dashboardCacheExists()).isTrue();
        long countBefore = (long) before.get("recordCount");

        // 3. delete record -> evicts dashboard cache
        matchingRecordService.deleteRecord(RECORD_ID);
        assertThat(dashboardCacheExists()).isFalse();

        // 4. next read returns fresh (decremented count)
        Map<String, Object> after = dashboardService.getDashboardStats();
        assertThat((long) after.get("recordCount")).isEqualTo(countBefore - 1);
    }

    @Test
    @DisplayName("cache eviction is idempotent: evictDashboardStats works explicitly")
    void explicitEvictDashboardStats() {
        dashboardService.getDashboardStats();
        assertThat(dashboardCacheExists()).isTrue();

        dashboardService.evictDashboardStats();
        assertThat(dashboardCacheExists()).isFalse();
    }
}
