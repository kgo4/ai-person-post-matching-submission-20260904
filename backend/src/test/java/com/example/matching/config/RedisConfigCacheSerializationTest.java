package com.example.matching.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.matching.DashboardStatsSnapshot;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.vo.system.AbilityTagTreeVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Redis 缓存序列化回归测试
 * <p>
 * 每个复杂缓存验证一次“写入后读取”：
 * 读取结果不是 LinkedHashMap、Page 本身是 Page、records 元素类型正确、
 * List 元素为正确 DTO/实体、Map 的 key/value 类型正确。
 * 另含结构测试：独立分页/详情配置、旧缓存名不再使用、复杂缓存统一 v3 前缀、
 * 不开启全局 defaultTyping。
 */
class RedisConfigCacheSerializationTest {

    private final RedisConfig config = new RedisConfig(mock(RedisConnectionFactory.class));

    private final RedisCacheManager cacheManager = (RedisCacheManager) config.cacheManager();

    // ==================== 写入后读取：复杂缓存 ====================

    @Test
    @SuppressWarnings("unchecked")
    void abilityTagInfoRoundTripsAsAbilityTag() {
        AbilityTag tag = new AbilityTag();
        tag.setId(11L);
        tag.setTagName("Java");
        tag.setTagCategory("TECH");

        Object restored = roundTrip(RedisCacheNames.ABILITY_TAG_INFO, tag);

        assertThat(restored).isInstanceOf(AbilityTag.class)
                .isNotInstanceOf(LinkedHashMap.class);
        assertThat(((AbilityTag) restored).getTagName()).isEqualTo("Java");
    }

    @Test
    @SuppressWarnings("unchecked")
    void abilityTagTreeRoundTripsAsListOfAbilityTagTreeVo() {
        AbilityTagTreeVO node = new AbilityTagTreeVO();
        node.setId(1L);
        node.setTagName("后端开发");
        AbilityTagTreeVO child = new AbilityTagTreeVO();
        child.setId(2L);
        child.setTagName("Java");
        node.setChildren(List.of(child));

        Object restored = roundTrip(RedisCacheNames.ABILITY_TAG_TREE, List.of(node));

        assertThat(restored).isInstanceOf(List.class).isNotInstanceOf(LinkedHashMap.class);
        List<?> list = (List<?>) restored;
        assertThat(list).hasSize(1).allMatch(AbilityTagTreeVO.class::isInstance);
        assertThat(((AbilityTagTreeVO) list.get(0)).getChildren())
                .allMatch(AbilityTagTreeVO.class::isInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void abilityTagCategoryListRoundTripsAsListOfAbilityTagTreeVo() {
        AbilityTagTreeVO node = new AbilityTagTreeVO();
        node.setId(3L);
        node.setTagName("前端开发");

        Object restored = roundTrip(RedisCacheNames.ABILITY_TAG_CATEGORY_LIST, List.of(node));

        assertThat(restored).isInstanceOf(List.class);
        assertThat(((List<?>) restored)).allMatch(AbilityTagTreeVO.class::isInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evolutionActiveAbilityTagsRoundTripsAsListOfAbilityTag() {
        AbilityTag tag = new AbilityTag();
        tag.setId(9L);
        tag.setTagName("Spring");

        Object restored = roundTrip(RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, List.of(tag));

        assertThat(restored).isInstanceOf(List.class);
        assertThat(((List<?>) restored)).allMatch(AbilityTag.class::isInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void postModelRoundTripsAsListOfPostAbilityModel() {
        PostAbilityModel model = new PostAbilityModel();
        model.setPostId(1L);
        model.setTagId(2L);

        Object restored = roundTrip(RedisCacheNames.POST_MODEL, List.of(model));

        assertThat(restored).isInstanceOf(List.class).isNotInstanceOf(LinkedHashMap.class);
        assertThat(((List<?>) restored)).allMatch(PostAbilityModel.class::isInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void postEnabledRoundTripsAsListOfPostPost() {
        PostPost post = new PostPost();
        post.setId(5L);
        post.setPostName("Java工程师");

        Object restored = roundTrip(RedisCacheNames.POST_ENABLED, List.of(post));

        assertThat(restored).isInstanceOf(List.class);
        assertThat(((List<?>) restored)).allMatch(PostPost.class::isInstance);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vectorRecallRoundTripsAsMapWithLongKeysAndBigDecimalValues() {
        Map<Long, BigDecimal> scores = new java.util.LinkedHashMap<>();
        scores.put(100L, new BigDecimal("85.50"));
        scores.put(200L, new BigDecimal("72.10"));

        Object restored = roundTrip(RedisCacheNames.VECTOR_RECALL, scores);

        // 不能是 GenericJackson 反序列化出的 LinkedHashMap<String, Object>
        assertThat(restored).isInstanceOf(Map.class).isNotInstanceOf(String.class);
        Map<?, ?> map = (Map<?, ?>) restored;
        assertThat(map).hasSize(2);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            assertThat(entry.getKey()).isInstanceOf(Long.class);
            assertThat(entry.getValue()).isInstanceOf(BigDecimal.class);
        }
        assertThat(map.get(100L)).isEqualTo(new BigDecimal("85.50"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingRecordPageRoundTripsAsPageOfMatchingRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(42L);
        record.setEmpId(1L);
        record.setPostId(2L);
        Page<MatchingRecord> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(record));

        Object restored = roundTrip(RedisCacheNames.MATCHING_RECORD_PAGE, page);

        assertThat(restored).isInstanceOf(Page.class);
        Page<?> restoredPage = (Page<?>) restored;
        assertThat(restoredPage.getCurrent()).isEqualTo(1L);
        assertThat(restoredPage.getRecords()).allMatch(MatchingRecord.class::isInstance);
        MatchingRecord restoredRecord = (MatchingRecord) restoredPage.getRecords().get(0);
        assertThat(restoredRecord.getId()).isEqualTo(42L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchingRecordDetailRoundTripsAsMatchingRecord() {
        MatchingRecord record = new MatchingRecord();
        record.setId(43L);
        record.setAiMatchScore(new BigDecimal("90.25"));

        Object restored = roundTrip(RedisCacheNames.MATCHING_RECORD_DETAIL, record);

        assertThat(restored).isInstanceOf(MatchingRecord.class)
                .isNotInstanceOf(LinkedHashMap.class);
        assertThat(((MatchingRecord) restored).getId()).isEqualTo(43L);
        assertThat(((MatchingRecord) restored).getAiMatchScore()).isEqualByComparingTo("90.25");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardStatsRoundTripsWithMatchingRecordElements() {
        MatchingRecord record = new MatchingRecord();
        record.setId(7L);
        DashboardStatsSnapshot snapshot = new DashboardStatsSnapshot(
                10L, 20L, 30L,
                Map.of("strong", 5L, "reject", 25L),
                Map.of("pending", 1L, "match", 29L),
                List.of(record));

        Object restored = roundTrip(RedisCacheNames.DASHBOARD_STATS, snapshot);

        assertThat(restored).isInstanceOf(DashboardStatsSnapshot.class)
                .isNotInstanceOf(LinkedHashMap.class);
        DashboardStatsSnapshot restoredSnapshot = (DashboardStatsSnapshot) restored;
        assertThat(restoredSnapshot.employeeCount()).isEqualTo(10L);
        assertThat(restoredSnapshot.recordCount()).isEqualTo(30L);
        assertThat(restoredSnapshot.scoreDistribution().get("strong")).isEqualTo(5L);
        assertThat(restoredSnapshot.recentRecords()).allMatch(MatchingRecord.class::isInstance);
    }

    // ==================== 写入后读取：简单类型 ====================

    @Test
    @SuppressWarnings("unchecked")
    void simpleTypesRoundTripWithExactTypes() {
        Object authorities = roundTrip(RedisCacheNames.AUTH_AUTHORITIES, List.of("ROLE_ADMIN", "ROLE_HR"));
        assertThat(authorities).isInstanceOf(List.class);
        assertThat(((List<?>) authorities)).allMatch(String.class::isInstance);

        Object canonical = roundTrip(RedisCacheNames.TAG_CANONICAL, 123456789L);
        assertThat(canonical).isInstanceOf(Long.class);

        Object report = roundTrip(RedisCacheNames.MATCHING_AI_REPORT, "AI分析报告内容");
        assertThat(report).isInstanceOf(String.class);
    }

    // ==================== 结构测试 ====================

    @Test
    void matchingRecordPageAndDetailHaveIndependentSerializationConfigs() {
        Map<String, RedisCacheConfiguration> configs = initialCacheConfigurations(cacheManager);
        assertThat(configs).containsKeys(RedisCacheNames.MATCHING_RECORD_PAGE, RedisCacheNames.MATCHING_RECORD_DETAIL);
        RedisSerializationContext.SerializationPair<?> pagePair =
                configs.get(RedisCacheNames.MATCHING_RECORD_PAGE).getValueSerializationPair();
        RedisSerializationContext.SerializationPair<?> detailPair =
                configs.get(RedisCacheNames.MATCHING_RECORD_DETAIL).getValueSerializationPair();
        assertThat(pagePair).isNotNull();
        assertThat(detailPair).isNotNull();
        assertThat(pagePair).isNotSameAs(detailPair);
    }

    @Test
    void allComplexCachesUseV3Prefix() {
        Map<String, RedisCacheConfiguration> configs = initialCacheConfigurations(cacheManager);
        for (String cacheName : List.of(
                RedisCacheNames.ABILITY_TAG_INFO,
                RedisCacheNames.ABILITY_TAG_TREE,
                RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
                RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS,
                RedisCacheNames.POST_MODEL,
                RedisCacheNames.POST_ENABLED,
                RedisCacheNames.VECTOR_RECALL,
                RedisCacheNames.MATCHING_RECORD_PAGE,
                RedisCacheNames.MATCHING_RECORD_DETAIL,
                RedisCacheNames.DASHBOARD_STATS)) {
            assertThat(configs.get(cacheName).getKeyPrefixFor(cacheName))
                    .as("缓存 %s 必须使用 matching:v3: 前缀", cacheName)
                    .startsWith("matching:v3:");
        }
    }

    @Test
    void obsoleteCacheNamesAreNoLongerConfigured() {
        Map<String, RedisCacheConfiguration> configs = initialCacheConfigurations(cacheManager);
        assertThat(configs).doesNotContainKeys("system:ability-tag:list", "matching:record");
    }

    @Test
    void noCacheableStillReferencesObsoleteCacheNames() throws IOException {
        assertNoSourceReference("RedisCacheNames.MATCHING_RECORD");
        assertNoSourceReference("RedisCacheNames.ABILITY_TAG_LIST");
    }

    @Test
    void genericSerializerDoesNotEmitTypeMetadata() {
        // redisObjectMapper() 未开启 activateDefaultTyping：泛型序列化输出不得含 @class 类型元数据
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.serializer.RedisSerializer<Object> serializer =
                (org.springframework.data.redis.serializer.RedisSerializer<Object>)
                        config.redisTemplate().getValueSerializer();
        byte[] payload = serializer.serialize(Map.of("count", 1));
        assertThat(new String(payload, StandardCharsets.UTF_8)).doesNotContain("@class");
    }

    // ==================== helpers ====================

    private Object roundTrip(String cacheName, Object value) {
        RedisCacheConfiguration cacheConfig = initialCacheConfigurations(cacheManager).get(cacheName);
        assertThat(cacheConfig).as("缓存 %s 必须显式配置", cacheName).isNotNull();
        var pair = cacheConfig.getValueSerializationPair();
        return pair.read(pair.write(value));
    }

    private void assertNoSourceReference(String identifier) throws IOException {
        // 词边界匹配：RedisCacheNames.MATCHING_RECORD 命中，但 ..._PAGE/..._DETAIL 不命中
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(identifier) + "\\b");
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> offenders = paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            return pattern.matcher(content).find();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
            assertThat(offenders).as("不应再存在 %s 缓存名引用", identifier).isEmpty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, RedisCacheConfiguration> initialCacheConfigurations(RedisCacheManager cacheManager) {
        try {
            java.lang.reflect.Method method = RedisCacheManager.class.getDeclaredMethod("getInitialCacheConfiguration");
            method.setAccessible(true);
            return (Map<String, RedisCacheConfiguration>) method.invoke(cacheManager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect Redis cache configuration", e);
        }
    }
}
