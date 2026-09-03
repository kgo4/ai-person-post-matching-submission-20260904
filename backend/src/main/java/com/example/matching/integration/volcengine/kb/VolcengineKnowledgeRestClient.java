package com.example.matching.integration.volcengine.kb;

import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Shared HTTP client for Volcengine knowledge-base requests. */
@Component
public class VolcengineKnowledgeRestClient {

    private final RestClient restClient;

    public VolcengineKnowledgeRestClient(VolcengineKnowledgeBaseProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .version(HttpClient.Version.HTTP_2)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .requestFactory(requestFactory)
                .build();
    }

    public RestClient get() {
        return restClient;
    }
}
