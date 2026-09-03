package com.example.matching.port.system;

import java.util.List;

public interface SystemAuthenticationPort {

    AuthenticatedUser getUserByUsername(String username);

    List<String> getAuthorities(Long userId);

    record AuthenticatedUser(Long id, String username, String password, Integer status) {
    }
}
