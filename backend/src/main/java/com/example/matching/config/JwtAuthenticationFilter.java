package com.example.matching.config;

import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.UserDetailsServiceImpl;
import com.example.matching.utils.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final com.example.matching.security.TokenInvalidationService tokenInvalidationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        try {
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                if (!tokenInvalidationService.isTokenValid(userId, jwtTokenProvider.getIssuedAtFromToken(token))) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 设置当前用户ID到 SecurityUtils
                SecurityUtils.setCurrentUserId(userId);
                SecurityUtils.setCurrentUsername(username);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 不论后续过滤链是否抛异常，必须清理 ThreadLocal，否则 Tomcat 线程复用会串号
            SecurityUtils.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(SecurityUtils.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityUtils.TOKEN_PREFIX)) {
            return bearerToken.substring(SecurityUtils.TOKEN_PREFIX.length());
        }
        return null;
    }
}
