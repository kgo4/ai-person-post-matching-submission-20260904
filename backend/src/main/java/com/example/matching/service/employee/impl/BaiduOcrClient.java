package com.example.matching.service.employee.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Component
public class BaiduOcrClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${resume.ocr.enabled:false}")
    private boolean enabled;

    @Value("${resume.ocr.api-key:}")
    private String apiKey;

    @Value("${resume.ocr.secret-key:}")
    private String secretKey;

    @Value("${resume.ocr.timeout-seconds:180}")
    private long timeoutSeconds;

    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public BaiduOcrClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String recognizePdf(Path pdfPath, int pageCount) throws IOException, InterruptedException {
        if (!enabled || apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return "";
        }
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        String encodedPdf = Base64.getEncoder().encodeToString(pdfBytes);
        if (encodedPdf.length() > 8 * 1024 * 1024) {
            throw new IOException("PDF 经 Base64 编码后超过百度 OCR 8MB 限制");
        }

        String token = getAccessToken();
        StringBuilder text = new StringBuilder();
        int pages = Math.max(1, Math.min(pageCount, 20));
        for (int page = 1; page <= pages; page++) {
            String form = "pdf_file=" + encode(encodedPdf)
                    + "&pdf_file_num=" + page
                    + "&language_type=CHN_ENG&detect_direction=true&paragraph=true";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=" + encode(token)))
                    .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds / pages)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("百度 OCR HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error_code")) {
                throw new IOException("百度 OCR 错误 " + root.path("error_code").asText() + ": " + root.path("error_msg").asText());
            }
            for (JsonNode item : root.path("words_result")) {
                String line = item.path("words").asText("").trim();
                if (!line.isEmpty()) text.append(line).append('\n');
            }
        }
        return text.toString().trim();
    }

    private String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
        synchronized (this) {
            if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
            String uri = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                    + "&client_id=" + encode(apiKey) + "&client_secret=" + encode(secretKey);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("百度 OCR Token HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String token = root.path("access_token").asText("");
            if (token.isBlank()) throw new IOException("百度 OCR Token 获取失败: " + root.path("error_description").asText());
            accessToken = token;
            long expires = Math.max(300, root.path("expires_in").asLong(2592000));
            tokenExpiresAt = Instant.now().plusSeconds(expires - 300);
            return token;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
