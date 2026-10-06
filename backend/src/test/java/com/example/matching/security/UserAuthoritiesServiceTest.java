package com.example.matching.security;

import com.example.matching.port.system.SystemAuthenticationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthoritiesServiceTest {

    @Mock private SystemAuthenticationPort systemAuthenticationPort;

    @Test
    void getAuthoritiesResolvesEnabledRolesForCaching() {
        when(systemAuthenticationPort.getAuthorities(7L)).thenReturn(java.util.List.of("ROLE_USER", "ROLE_ADMIN"));

        UserAuthoritiesService service = new UserAuthoritiesService(systemAuthenticationPort);

        assertThat(service.getAuthorities(7L)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }
}
