package com.example.matching.config;

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Redisson treats an empty password as a configured password and sends AUTH.
 * Local Redis commonly runs without authentication, so convert an empty
 * optional password back to null before the client is created.
 */
@Configuration(proxyBeanMethods = false)
public class RedissonPasswordCustomizer {

    @Bean
    RedissonAutoConfigurationCustomizer redisPasswordCustomizer(Environment environment) {
        return config -> {
            String password = environment.getProperty("spring.data.redis.password");
            if (!StringUtils.hasText(password)) {
                config.useSingleServer().setPassword(null);
            }
        };
    }
}
