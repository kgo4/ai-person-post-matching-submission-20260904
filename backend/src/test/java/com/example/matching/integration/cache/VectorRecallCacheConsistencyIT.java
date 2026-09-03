package com.example.matching.integration.cache;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.matching.EmployeeVectorRecallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying vector recall cache consistency with real Redis.
 * <p>
 * Validates:
 * <ul>
 *   <li>recallEmployeesForPost result is cached under VECTOR_RECALL</li>
 *   <li>EMP_VECTOR_CACHE_EPOCH is incremented when an AbilityChangeEvent fires</li>
 *   <li>VECTOR_RECALL cache can be evicted when the post model changes</li>
 * </ul>
 * <p>
 * Milvus is disabled in the integration profile; recall returns an empty map
 * which is still cached by the @Cacheable annotation.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VectorRecallCacheConsistencyIT extends AbstractIntegrationTest {

    private static final Long POST_ID = 999003L;

    @Autowired private EmployeeVectorRecallService vectorRecallService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM matching_record WHERE post_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM post_ability_model WHERE post_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM post_post WHERE id = ?", POST_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v3:vector:recall*"));
        redisTemplate.delete(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
        jdbcTemplate.update(
                "INSERT INTO post_post (id, post_code, post_name, status, is_deleted) "
                        + "VALUES (?, 'IT_VR_001', 'VectorRecallTestPost', 1, 0)",
                POST_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM matching_record WHERE post_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM post_ability_model WHERE post_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM post_post WHERE id = ?", POST_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v3:vector:recall*"));
        redisTemplate.delete(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
    }

    private boolean recallCacheExists(Long postId, int topK) {
        String pattern = "matching:v3:" + RedisCacheNames.VECTOR_RECALL + "::" + postId + ":" + topK + ":e*";
        return !redisTemplate.keys(pattern).isEmpty();
    }

    // -- tests ----------------------------------------------------------------

    @Test
    @DisplayName("recallEmployeesForPost caches result; repeated call returns cached value")
    void vectorRecallResultIsCached() {
        MatchingPostProfile post = new MatchingPostProfile(POST_ID, null, null, null, null, null, List.of());

        // Milvus is disabled -> returns empty map, still cached
        Map<Long, BigDecimal> first = vectorRecallService.recallEmployeesForPost(post, 100);
        assertThat(first).isEmpty();
        assertThat(recallCacheExists(POST_ID, 100)).isTrue();

        // second call returns cached result
        Map<Long, BigDecimal> second = vectorRecallService.recallEmployeesForPost(post, 100);
        assertThat(second).isEmpty();
        assertThat(recallCacheExists(POST_ID, 100)).isTrue();
    }

    @Test
    @DisplayName("different topK values produce distinct cache keys")
    void differentTopK_distinctCacheKeys() {
        MatchingPostProfile post = new MatchingPostProfile(POST_ID, null, null, null, null, null, List.of());

        vectorRecallService.recallEmployeesForPost(post, 50);
        vectorRecallService.recallEmployeesForPost(post, 100);

        assertThat(recallCacheExists(POST_ID, 50)).isTrue();
        assertThat(recallCacheExists(POST_ID, 100)).isTrue();
    }

    @Test
    @DisplayName("vector recall cache evicts when post model changes (manual eviction simulation)")
    void vectorRecallCacheEvictedOnModelChange() {
        MatchingPostProfile post = new MatchingPostProfile(POST_ID, null, null, null, null, null, List.of());

        // populate cache
        vectorRecallService.recallEmployeesForPost(post, 100);
        assertThat(recallCacheExists(POST_ID, 100)).isTrue();

        // simulate post model change: advance epoch (new cache keys)
        // (In production this happens via VectorRecallCacheEpoch.advance() on PostAbilityModelServiceImpl)
        com.example.matching.service.common.VectorRecallCacheEpoch epoch =
                new com.example.matching.service.common.VectorRecallCacheEpoch(
                        org.springframework.data.redis.core.StringRedisTemplate.class.cast(
                                redisTemplate));
        epoch.advance();

        assertThat(recallCacheExists(POST_ID, 100)).isFalse();

        // next call re-populates from "DB" (Milvus disabled -> empty)
        Map<Long, BigDecimal> result = vectorRecallService.recallEmployeesForPost(post, 100);
        assertThat(result).isEmpty();
        assertThat(recallCacheExists(POST_ID, 100)).isTrue();
    }

    @Test
    @DisplayName("AbilityChangeEvent increments EMP_VECTOR_CACHE_EPOCH in Redis")
    void abilityChangeEvent_incrementsEpoch() {
        // ensure epoch key does not exist
        redisTemplate.delete(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);

        // publish the same event type the AbilityChangeListener handles
        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", 10L));

        // the @Async listener runs in a separate thread; poll until it completes
        boolean incremented = false;
        for (int i = 0; i < 30; i++) {
            Object epoch = redisTemplate.opsForValue().get(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
            if (epoch != null && epoch.toString().equals("1")) {
                incremented = true;
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(incremented)
                .as("EMP_VECTOR_CACHE_EPOCH should be incremented by the async event listener")
                .isTrue();
    }

    @Test
    @DisplayName("multiple ability change events increment epoch cumulatively")
    void multipleEvents_incrementEpochCumulatively() {
        redisTemplate.delete(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);

        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", 10L));
        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", 20L));

        // poll for both increments
        boolean reachedTwo = false;
        for (int i = 0; i < 50; i++) {
            Object epoch = redisTemplate.opsForValue().get(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
            if (epoch != null) {
                try {
                    if (Long.parseLong(epoch.toString()) >= 2) {
                        reachedTwo = true;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(reachedTwo)
                .as("EMP_VECTOR_CACHE_EPOCH should reach at least 2 after two events")
                .isTrue();
    }
}
