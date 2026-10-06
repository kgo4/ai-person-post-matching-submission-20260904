package com.example.matching.config;

import java.util.Arrays;

public final class WebSocketOriginPatterns {

    private WebSocketOriginPatterns() {
    }

    public static String[] fromCommaSeparated(String origins) {
        if (origins == null || origins.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
