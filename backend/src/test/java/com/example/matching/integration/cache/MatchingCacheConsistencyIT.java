package com.example.matching.integration.cache;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.infra.AbstractIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying matching record cache consistency with real Redis.
 * <p>
 * Validates that @Cacheable populates the cache and @CacheEvict(allEntries=true)
 * correctly clears all matching record cache entries on mutations (modify, lock,
 * unlock, delete).
 * <p>
 * Propagation.NOT_SUPPORTED ensures service-level transactions commit immediately,
 * making Redis cache operations visible during the test (the base class's
 * @Transactional + transactionAware() cache manager would otherwise defer them).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchingCacheConsistencyIT extends AbstractIntegrationTest {

    private static final Long RECORD_ID = 999001L;
    private static final Long EMP_ID = 99901L;
    private static final Long POST_ID = 99900L;

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
        redisTemplate.delete(redisTemplate.keys("matching:v3:matching:record*"));
        redisTemplate.delete(redisTemplate.keys("matching:v3:dashboard:stats*"));

        jdbcTemplate.update(
                "INSERT INTO matching_record (id, batch_no, emp_id, post_id, ai_match_score, match_status, is_locked, is_deleted, version) "
                        + "VALUES (?, 'BATCH-IT', ?, ?, 85.00, 2, 0, 0, 0)",
                RECORD_ID, EMP_ID, POST_ID);
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
        redisTemplate.delete(redisTemplate.keys("matching:v3:matching:record*"));
        redisTemplate.delete(redisTemplate.keys("matching:v3:dashboard:stats*"));
    }

    // -- helpers --------------------------------------------------------------

    private boolean cacheKeyExists(String cacheName, Object key) {
        String redisKey = "matching:v3:" + cacheName + "::" + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    // -- detail cache tests ---------------------------------------------------

    @Test
    @DisplayName("getDetailById populates cache; modifyResult evicts it")
    void detailCache_modifyEvicts() {
        // 1. populate cache
        MatchingRecord first = matchingRecordService.getDetailById(RECORD_ID);
        assertThat(first).isNotNull();
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_DETAIL, RECORD_ID)).isTrue();

        // 2. mutation evicts ALL matching:record entries
        MatchingRecord update = new MatchingRecord();
        update.setFinalMatchScore(new BigDecimal("90.00"));
        matchingRecordService.modifyResult(RECORD_ID, update);

        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_DETAIL, RECORD_ID)).isFalse();

        // 3. next read hits DB
        MatchingRecord fresh = matchingRecordService.getDetailById(RECORD_ID);
        assertThat(fresh).isNotNull();
        assertThat(fresh.getFinalMatchScore()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("getDetailById populates cache; deleteRecord evicts it")
    void detailCache_deleteEvicts() {
        matchingRecordService.getDetailById(RECORD_ID);
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_DETAIL, RECORD_ID)).isTrue();

        matchingRecordService.deleteRecord(RECORD_ID);

        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_DETAIL, RECORD_ID)).isFalse();
    }

    // -- page cache tests -----------------------------------------------------

    @Test
    @DisplayName("pageRecords caches by page conditions; lock/unlock/delete evicts all page entries")
    void pageCache_mutationsEvictAll() {
        // 1. populate page caches with two different conditions
        IPage<MatchingRecord> page1 = matchingRecordService.pageRecords(
                new Page<>(1, 10), POST_ID, null, null);
        String pageKey1 = "page:1:10:" + POST_ID + "::";
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey1)).isTrue();

        IPage<MatchingRecord> page2 = matchingRecordService.pageRecords(
                new Page<>(1, 10), null, EMP_ID, null);
        String pageKey2 = "page:1:10::" + EMP_ID + ":";
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey2)).isTrue();

        // 2. lock evicts all matching:record entries
        matchingRecordService.lockResult(RECORD_ID);
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey1)).isFalse();
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey2)).isFalse();

        // 3. re-populate then unlock evicts again
        matchingRecordService.pageRecords(new Page<>(1, 10), POST_ID, null, null);
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey1)).isTrue();
        matchingRecordService.unlockResult(RECORD_ID);
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, pageKey1)).isFalse();
    }

    @Test
    @DisplayName("different page conditions produce distinct cache keys")
    void pageCache_distinctKeys() {
        matchingRecordService.pageRecords(new Page<>(1, 10), POST_ID, null, null);
        matchingRecordService.pageRecords(new Page<>(2, 10), POST_ID, null, null);

        String key1 = "page:1:10:" + POST_ID + "::";
        String key2 = "page:2:10:" + POST_ID + "::";
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, key1)).isTrue();
        assertThat(cacheKeyExists(RedisCacheNames.MATCHING_RECORD_PAGE, key2)).isTrue();
        assertThat(key1).isNotEqualTo(key2);
    }
}
