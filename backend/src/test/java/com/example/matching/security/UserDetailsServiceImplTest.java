package com.example.matching.security;

import com.example.matching.port.system.SystemAuthenticationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private SystemAuthenticationPort systemAuthenticationPort;
    @Mock
    private UserAuthoritiesService userAuthoritiesService;

    @Test
    void loadUserByUsername_usesEnabledDatabaseRolesAsAuthorities() {
        SystemAuthenticationPort.AuthenticatedUser user = new SystemAuthenticationPort.AuthenticatedUser(
                7L, "admin", "encoded-password", 1);
        when(systemAuthenticationPort.getUserByUsername("admin")).thenReturn(user);
        when(userAuthoritiesService.getAuthorities(7L)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));

        UserDetails details = new UserDetailsServiceImpl(systemAuthenticationPort, userAuthoritiesService)
                .loadUserByUsername("admin");

        assertThat(details.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }
}
