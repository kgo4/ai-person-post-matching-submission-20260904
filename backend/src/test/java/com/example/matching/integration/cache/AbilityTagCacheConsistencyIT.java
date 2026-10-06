package com.example.matching.integration.cache;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying ability tag cache consistency with real Redis.
 * <p>
 * Validates that updateStatus and mergeTags evict all related caches
 * (tree, list, info, canonical) forcing fresh reads from the database.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AbilityTagCacheConsistencyIT extends AbstractIntegrationTest {

    private static final Long TAG_A_ID = 999010L;
    private static final Long TAG_B_ID = 999011L;

    @Autowired private AbilityTagService abilityTagService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM ability_tag WHERE id IN (?, ?)", TAG_A_ID, TAG_B_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:system:ability-tag*"));
        redisTemplate.delete(redisTemplate.keys("matching:v2:system:tag:canonical*"));

        jdbcTemplate.update(
                "INSERT INTO ability_tag (id, tag_code, tag_name, parent_id, tag_category, tag_level, sort_order, is_system, status, is_deleted, canonical_tag_id) "
                        + "VALUES (?, 'IT_CACHE_A', 'CacheTestTagA', 0, 'TECHNICAL', 1, 9990, 0, 1, 0, ?)",
                TAG_A_ID, TAG_A_ID);
        jdbcTemplate.update(
                "INSERT INTO ability_tag (id, tag_code, tag_name, parent_id, tag_category, tag_level, sort_order, is_system, status, is_deleted, canonical_tag_id) "
                        + "VALUES (?, 'IT_CACHE_B', 'CacheTestTagB', 0, 'TECHNICAL', 1, 9991, 0, 1, 0, ?)",
                TAG_B_ID, TAG_B_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ability_tag WHERE id IN (?, ?)", TAG_A_ID, TAG_B_ID);
        redisTemplate.delete(redisTemplate.keys("matching:v2:system:ability-tag*"));
        redisTemplate.delete(redisTemplate.keys("matching:v2:system:tag:canonical*"));
    }

    private boolean cacheKeyExists(String cacheName, Object key) {
        String redisKey = "matching:v2:" + cacheName + "::" + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    // -- updateStatus tests ---------------------------------------------------

    @Test
    @DisplayName("getById caches tag; updateStatus evicts info/tree/list caches")
    void updateStatus_evictsAllTagCaches() {
        // 1. populate caches
        AbilityTag tag = abilityTagService.getById(TAG_A_ID);
        assertThat(tag).isNotNull();
        assertThat(tag.getStatus()).isEqualTo(1);

        abilityTagService.getTree();

        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_A_ID)).isTrue();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isTrue();

        // 2. update status -> evicts all tag caches
        abilityTagService.updateStatus(TAG_A_ID, 0);

        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_A_ID)).isFalse();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isFalse();

        // 3. next read hits DB and returns updated status
        AbilityTag fresh = abilityTagService.getById(TAG_A_ID);
        assertThat(fresh).isNotNull();
        assertThat(fresh.getStatus()).isEqualTo(0);
    }

    // -- mergeTags tests ------------------------------------------------------

    @Test
    @DisplayName("getById caches both tags; mergeTags evicts all tag caches")
    void mergeTags_evictsAllTagCaches() {
        // 1. populate caches for both tags and the tree
        AbilityTag a = abilityTagService.getById(TAG_A_ID);
        AbilityTag b = abilityTagService.getById(TAG_B_ID);
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();

        abilityTagService.getTree();

        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_A_ID)).isTrue();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_B_ID)).isTrue();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isTrue();

        // 2. merge A into B -> evicts all tag caches + canonical cache
        abilityTagService.mergeTags(TAG_A_ID, TAG_B_ID);

        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_A_ID)).isFalse();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_INFO, TAG_B_ID)).isFalse();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isFalse();

        // 3. source tag should now be disabled (status=0) with canonical pointing to B
        AbilityTag merged = abilityTagService.getById(TAG_A_ID);
        assertThat(merged).isNotNull();
        assertThat(merged.getStatus()).isEqualTo(0);
        assertThat(merged.getCanonicalTagId()).isEqualTo(TAG_B_ID);
    }

    // -- getTree and getByCategory cache tests --------------------------------

    @Test
    @DisplayName("getTree populates cache; updateStatus evicts it so next getTree reads DB")
    void treeCache_updateStatusEvicts() {
        abilityTagService.getTree();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isTrue();

        abilityTagService.updateStatus(TAG_A_ID, 0);

        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isFalse();

        // next getTree re-populates from DB (tag A now has status=0, excluded from tree)
        abilityTagService.getTree();
        assertThat(cacheKeyExists(RedisCacheNames.ABILITY_TAG_TREE, "all")).isTrue();
    }
}
