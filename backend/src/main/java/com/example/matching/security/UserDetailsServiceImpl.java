package com.example.matching.security;

import com.example.matching.port.system.SystemAuthenticationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户详情加载服务
 * <p>
 * Loads users through the system authentication port.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SystemAuthenticationPort systemAuthenticationPort;
    private final UserAuthoritiesService userAuthoritiesService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SystemAuthenticationPort.AuthenticatedUser sysUser = systemAuthenticationPort.getUserByUsername(username);

        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在或已被禁用: " + username);
        }

        return User.builder()
                .username(sysUser.username())
                .password(sysUser.password())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(sysUser.status() == 0)
                .authorities(userAuthoritiesService.getAuthorities(sysUser.id()).toArray(String[]::new))
                .build();
    }
}
