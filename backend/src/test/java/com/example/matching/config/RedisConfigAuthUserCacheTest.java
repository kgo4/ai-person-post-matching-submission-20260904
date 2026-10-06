package com.example.matching.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigAuthUserCacheTest {

    @Test
    void authUserSerializerDeserializesLegacyJsonAsSysUser() throws Exception {
        RedisConfig config = new RedisConfig(mock(RedisConnectionFactory.class));
        byte[] legacyJson = new ObjectMapper().writeValueAsBytes(
                java.util.Map.of("id", 7L, "username", "admin", "status", 1));

        SysUser user = config.sysUserCacheSerializer().deserialize(legacyJson);

        assertThat(user).isInstanceOf(SysUser.class);
        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("admin");
    }

    @Test
    void postPageCacheDeserializesToPageWithPostRecords() {
        RedisConfig config = new RedisConfig(mock(RedisConnectionFactory.class));
        PostPost post = new PostPost();
        post.setId(7L);
        post.setPostName("Java Developer");
        Page<PostPost> page = new Page<>(1, 10, 1);
        page.setRecords(java.util.List.of(post));

        RedisCacheManager cacheManager = (RedisCacheManager) config.cacheManager();
        var cacheConfig = initialCacheConfigurations(cacheManager)
                .get(RedisCacheNames.POST_POST_PAGE)
                .getValueSerializationPair();
        Object restored = cacheConfig.read(cacheConfig.write(page));

        assertThat(restored).isInstanceOf(Page.class);
        assertThat(((Page<?>) restored).getRecords()).allMatch(PostPost.class::isInstance);
    }

    @Test
    void employeePageCacheDeserializesToPageWithEmployeeRecords() {
        RedisConfig config = new RedisConfig(mock(RedisConnectionFactory.class));
        EmpEmployee employee = new EmpEmployee();
        employee.setId(8L);
        employee.setRealName("Alice");
        Page<EmpEmployee> page = new Page<>(1, 10, 1);
        page.setRecords(java.util.List.of(employee));

        RedisCacheManager cacheManager = (RedisCacheManager) config.cacheManager();
        var cacheConfig = initialCacheConfigurations(cacheManager)
                .get(RedisCacheNames.EMP_EMPLOYEE_PAGE)
                .getValueSerializationPair();
        Object restored = cacheConfig.read(cacheConfig.write(page));

        assertThat(restored).isInstanceOf(Page.class);
        assertThat(((Page<?>) restored).getRecords()).allMatch(EmpEmployee.class::isInstance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, org.springframework.data.redis.cache.RedisCacheConfiguration>
    initialCacheConfigurations(RedisCacheManager cacheManager) {
        try {
            Method method = RedisCacheManager.class.getDeclaredMethod("getInitialCacheConfiguration");
            method.setAccessible(true);
            return (Map<String, org.springframework.data.redis.cache.RedisCacheConfiguration>) method.invoke(cacheManager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect Redis cache configuration", e);
        }
    }
}
