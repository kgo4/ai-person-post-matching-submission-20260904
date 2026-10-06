package com.example.matching.agent.json;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogitBiasAdapterTest {

    @Test
    void buildsBiasMapWhenEnabled() {
        Map<String, Integer> bias = LogitBiasAdapter.biasMap(true);
        assertTrue(bias.getOrDefault("123", 0) > 0); // '{' token 正偏置
        assertTrue(bias.getOrDefault("12", 0) < 0);  // 反引号 token 负偏置
    }

    @Test
    void emptyWhenDisabled() {
        assertTrue(LogitBiasAdapter.biasMap(false).isEmpty());
    }
}
