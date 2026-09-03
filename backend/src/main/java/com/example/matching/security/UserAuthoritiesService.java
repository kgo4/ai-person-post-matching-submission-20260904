package com.example.matching.security;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.port.system.SystemAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuthoritiesService {

    private final SystemAuthenticationPort systemAuthenticationPort;

    @Cacheable(cacheNames = RedisCacheNames.AUTH_AUTHORITIES, key = "#userId", sync = true)
    public List<String> getAuthorities(Long userId) {
        return systemAuthenticationPort.getAuthorities(userId);
    }
}
