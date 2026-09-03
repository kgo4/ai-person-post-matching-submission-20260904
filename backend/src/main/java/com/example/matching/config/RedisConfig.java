package com.example.matching.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.matching.DashboardStatsSnapshot;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SysUser;
import com.example.matching.vo.system.AbilityTagTreeVO;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Redis 配置
 * <p>
 * 通过 {@code spring.redis.enabled=true}（默认）开启。
 * 设为 false 时本类不加载，RedisTemplate 不创建，CacheManager 由 Spring Boot 自动提供 NoOp 实现。
 * <p>
 * 序列化策略：不开启全局 {@code activateDefaultTyping}。每个复杂缓存使用明确目标类型的
 * {@link Jackson2JsonRedisSerializer}，并用 {@code matching:v3:} 前缀隔离旧缓存；
 * 旧 {@code matching:v2:*} 键不读取、不迁移、不删除，由 TTL 自然过期。
 */
@Slf4j
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig implements CachingConfigurer {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** v3 缓存前缀：新版本只读取 v3，旧 v2 键由 TTL 自然过期 */
    private static final org.springframework.data.redis.cache.CacheKeyPrefix V3_PREFIX =
            name -> "matching:v3:" + name + "::";

    private static String v3(String cacheName) {
        return "matching:v3:" + cacheName + "::";
    }

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
        log.info("Redis 缓存已启用");
    }

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // Strict PolymorphicTypeValidator: only allow JDK base types and specific project classes.
        // No broad package prefix — every allowed subtype is explicitly enumerated.
        // activateDefaultTyping is NOT enabled; type metadata is handled per-cache-type by the serializer.
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Base container types (required for Map/List/Set field declarations)
                .allowIfBaseType(Map.class)
                .allowIfBaseType(List.class)
                .allowIfBaseType(Set.class)
                .allowIfBaseType(Number.class)
                .allowIfBaseType(CharSequence.class)
                // MyBatis-Plus pagination
                .allowIfSubType(Page.class)
                // Concrete JDK containers
                .allowIfSubType(java.util.LinkedHashMap.class)
                .allowIfSubType(java.util.HashMap.class)
                .allowIfSubType(java.util.ArrayList.class)
                .allowIfSubType(java.util.LinkedList.class)
                .allowIfSubType(java.util.HashSet.class)
                .allowIfSubType(java.util.LinkedHashSet.class)
                .allowIfSubType(java.util.TreeMap.class)
                .allowIfSubType(java.util.TreeSet.class)
                .allowIfSubType(java.util.Collections.emptyMap().getClass())
                .allowIfSubType(java.util.Collections.emptyList().getClass())
                .allowIfSubType(java.util.Collections.emptySet().getClass())
                // JDK immutable collections
                .allowIfSubType(List.of().getClass())
                .allowIfSubType(Set.of().getClass())
                .allowIfSubType(Map.of().getClass())
                .allowIfSubType(List.of(1).getClass())
                .allowIfSubType(Set.of(1).getClass())
                .allowIfSubType(Map.of("a", 1).getClass())
                // JDK value types
                .allowIfSubType(String.class)
                .allowIfSubType(Integer.class)
                .allowIfSubType(Long.class)
                .allowIfSubType(Double.class)
                .allowIfSubType(Float.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(BigDecimal.class)
                .allowIfSubType(java.math.BigInteger.class)
                // Java Time
                .allowIfSubType(java.time.LocalDateTime.class)
                .allowIfSubType(java.time.LocalDate.class)
                .allowIfSubType(java.time.LocalTime.class)
                .allowIfSubType(java.time.Instant.class)
                .allowIfSubType(java.time.Duration.class)
                .allowIfSubType(java.time.ZonedDateTime.class)
                .allowIfSubType(java.time.OffsetDateTime.class)
                .build();
        // DO NOT enable default typing — each cache type should use explicit DTO serialization.
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ==================== 通用 typed 缓存工厂 ====================

    /**
     * 单对象缓存：明确目标类型，命中缓存后不会退化为 LinkedHashMap
     */
    private <T> RedisCacheConfiguration typedCacheConfig(Class<T> type, Duration ttl, String prefix) {
        ObjectMapper mapper = redisObjectMapper();
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer<>(mapper, type);
        return cacheConfig(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(name -> prefix);
    }

    /**
     * 参数化 List 缓存：元素类型明确
     */
    private <T> RedisCacheConfiguration typedListCacheConfig(Class<T> elementType, Duration ttl, String prefix) {
        ObjectMapper mapper = redisObjectMapper();
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
        Jackson2JsonRedisSerializer<List<T>> serializer = new Jackson2JsonRedisSerializer<>(mapper, type);
        return cacheConfig(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(name -> prefix);
    }

    /**
     * 参数化分页缓存：records 元素类型明确
     */
    private <T> RedisCacheConfiguration typedPageCacheConfig(Class<T> recordType, Duration ttl, String prefix) {
        ObjectMapper mapper = redisObjectMapper();
        JavaType type = mapper.getTypeFactory().constructParametricType(Page.class, recordType);
        Jackson2JsonRedisSerializer<Page<T>> serializer = new Jackson2JsonRedisSerializer<>(mapper, type);
        return cacheConfig(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(name -> prefix);
    }

    /**
     * 参数化 Map 缓存：key/value 类型明确（如 Map&lt;Long, BigDecimal&gt;）
     */
    private <K, V> RedisCacheConfiguration typedMapCacheConfig(Class<K> keyType, Class<V> valueType,
                                                               Duration ttl, String prefix) {
        ObjectMapper mapper = redisObjectMapper();
        JavaType type = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, keyType, valueType);
        Jackson2JsonRedisSerializer<Map<K, V>> serializer = new Jackson2JsonRedisSerializer<>(mapper, type);
        return cacheConfig(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(name -> prefix);
    }

    private RedisCacheConfiguration cacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();
    }

    /**
     * Authentication results have a concrete return type. Do not deserialize
     * this cache through the generic serializer, which turns legacy JSON into
     * a LinkedHashMap when no type metadata is present.
     */
    Jackson2JsonRedisSerializer<SysUser> sysUserCacheSerializer() {
        return new Jackson2JsonRedisSerializer<>(redisObjectMapper(), SysUser.class);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Override
    @Bean
    public CacheManager cacheManager() {
        ObjectMapper objectMapper = redisObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()
                // 兜底配置同样使用 v3 前缀：新版本只读取 v3，旧 v2 键由 TTL 自然过期
                .computePrefixWith(V3_PREFIX);

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // ==================== 复杂类型缓存：typed 序列化 + v3 前缀 ====================

        // 能力标签：单对象 / 树 / 分类列表 / 演化启用列表
        cacheConfigs.put(RedisCacheNames.ABILITY_TAG_INFO,
                typedCacheConfig(AbilityTag.class, Duration.ofMinutes(30), v3(RedisCacheNames.ABILITY_TAG_INFO)));
        cacheConfigs.put(RedisCacheNames.ABILITY_TAG_TREE,
                typedListCacheConfig(AbilityTagTreeVO.class, Duration.ofMinutes(30), v3(RedisCacheNames.ABILITY_TAG_TREE)));
        cacheConfigs.put(RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
                typedListCacheConfig(AbilityTagTreeVO.class, Duration.ofMinutes(30), v3(RedisCacheNames.ABILITY_TAG_CATEGORY_LIST)));
        cacheConfigs.put(RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS,
                typedListCacheConfig(AbilityTag.class, Duration.ofMinutes(30), v3(RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS)));

        // 岗位：启用列表 / 能力模型
        cacheConfigs.put(RedisCacheNames.POST_ENABLED,
                typedListCacheConfig(PostPost.class, Duration.ofMinutes(10), v3(RedisCacheNames.POST_ENABLED)));
        cacheConfigs.put(RedisCacheNames.POST_MODEL,
                typedListCacheConfig(PostAbilityModel.class, Duration.ofMinutes(10), v3(RedisCacheNames.POST_MODEL)));

        // 匹配记录：分页 / 详情
        cacheConfigs.put(RedisCacheNames.MATCHING_RECORD_PAGE,
                typedPageCacheConfig(MatchingRecord.class, Duration.ofMinutes(10), v3(RedisCacheNames.MATCHING_RECORD_PAGE)));
        cacheConfigs.put(RedisCacheNames.MATCHING_RECORD_DETAIL,
                typedCacheConfig(MatchingRecord.class, Duration.ofMinutes(10), v3(RedisCacheNames.MATCHING_RECORD_DETAIL)));

        // 向量召回：Map<Long, BigDecimal>
        cacheConfigs.put(RedisCacheNames.VECTOR_RECALL,
                typedMapCacheConfig(Long.class, BigDecimal.class, Duration.ofMinutes(15), v3(RedisCacheNames.VECTOR_RECALL)));

        // Dashboard：不可变快照 DTO（不再缓存 Map<String, Object>）
        cacheConfigs.put(RedisCacheNames.DASHBOARD_STATS,
                typedCacheConfig(DashboardStatsSnapshot.class, Duration.ofMinutes(5),
                        "matching:v3:dashboard:stats::"));

        // 认证：SysUser（保留原有独立前缀语义，与 v3 通用前缀一致）
        cacheConfigs.put(RedisCacheNames.AUTH_SYSUSER,
                typedCacheConfig(SysUser.class, Duration.ofMinutes(30), v3(RedisCacheNames.AUTH_SYSUSER)));

        // 员工 / 岗位分页
        cacheConfigs.put(RedisCacheNames.EMP_EMPLOYEE_PAGE,
                typedPageCacheConfig(EmpEmployee.class, Duration.ofMinutes(10), v3(RedisCacheNames.EMP_EMPLOYEE_PAGE)));
        cacheConfigs.put(RedisCacheNames.POST_POST_PAGE,
                typedPageCacheConfig(PostPost.class, Duration.ofMinutes(10), v3(RedisCacheNames.POST_POST_PAGE)));

        // ==================== 简单类型缓存：明确目标类型，杜绝 Integer/Long 隐式错型 ====================

        // 用户权限列表：List<String>（安全：字符串数组 JSON，无类型歧义）
        cacheConfigs.put(RedisCacheNames.AUTH_AUTHORITIES,
                typedListCacheConfig(String.class, Duration.ofMinutes(30), v3(RedisCacheNames.AUTH_AUTHORITIES)));
        // 标签标准ID：Long（typed 序列化避免小整数读回 Integer 导致 ClassCastException）
        cacheConfigs.put(RedisCacheNames.TAG_CANONICAL,
                typedCacheConfig(Long.class, Duration.ofMinutes(30), v3(RedisCacheNames.TAG_CANONICAL)));
        // AI 报告：String
        cacheConfigs.put(RedisCacheNames.MATCHING_AI_REPORT,
                typedCacheConfig(String.class, Duration.ofHours(1), v3(RedisCacheNames.MATCHING_AI_REPORT)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
}
