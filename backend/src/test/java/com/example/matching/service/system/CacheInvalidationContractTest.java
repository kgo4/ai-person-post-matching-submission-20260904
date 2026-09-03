package com.example.matching.service.system;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.entity.system.SysUser;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.security.UserAuthoritiesService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.service.system.impl.AbilityTagServiceImpl;
import com.example.matching.service.system.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheInvalidationContractTest {

    @Test
    void passwordReset_evictsOnlyTheAffectedUsersCachedCredentials() throws Exception {
        Method method = SysUserServiceImpl.class.getMethod("resetPassword", Long.class);
        CacheEvict eviction = method.getAnnotation(CacheEvict.class);

        assertThat(eviction).isNull();

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("alice");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(cacheManager.getCache(RedisCacheNames.AUTH_SYSUSER)).thenReturn(cache);

        SysUserServiceImpl service = new SysUserServiceImpl(passwordEncoder, mock(JwtTokenProvider.class),
                mock(TokenInvalidationService.class), mock(SysRoleService.class), cacheManager,
                mock(UserAuthoritiesService.class));
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);

        service.resetPassword(7L);

        verify(cache).evict("alice");
    }

    @Test
    void tagStatusAndMerge_evictCachedTagDetails() throws Exception {
        for (Method method : List.of(
                AbilityTagServiceImpl.class.getMethod("updateStatus", Long.class, Integer.class),
                AbilityTagServiceImpl.class.getMethod("mergeTags", Long.class, Long.class))) {
            CacheEvict eviction = method.getAnnotation(CacheEvict.class);

            assertThat(eviction).isNotNull();
            assertThat(Arrays.asList(eviction.cacheNames())).contains(RedisCacheNames.ABILITY_TAG_INFO);
            assertThat(eviction.allEntries()).isTrue();
        }
    }
}
