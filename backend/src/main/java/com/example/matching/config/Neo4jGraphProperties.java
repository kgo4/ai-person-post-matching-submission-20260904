package com.example.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "neo4j.graph")
public class Neo4jGraphProperties {

    private boolean enabled = false;
    private String uri = "";
    private String username = "";
    private String password = "";
    private String database = "neo4j";

    public boolean isUsable() {
        return enabled
                && uri != null && !uri.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
