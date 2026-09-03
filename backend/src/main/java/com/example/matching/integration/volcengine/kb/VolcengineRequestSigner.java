package com.example.matching.integration.volcengine.kb;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Volcengine HMAC-SHA256 request signer.
 */
public class VolcengineRequestSigner {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String service;
    private final Clock clock;

    public VolcengineRequestSigner(String accessKey, String secretKey, String region, String service, Clock clock) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.service = service;
        this.clock = clock;
    }

    public Map<String, String> sign(String method, String path, String body) {
        String now = DATE_TIME_FORMAT.format(clock.instant());
        String date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).format(DATE_FORMAT);
        String payloadHash = sha256Hex(body == null ? "" : body);
        String canonicalRequest = method.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + "\n"
                + "content-type:application/json\n"
                + "x-date:" + now + "\n"
                + "\n"
                + "content-type;x-date\n"
                + payloadHash;
        String credentialScope = date + "/" + region + "/" + service + "/request";
        String stringToSign = "HMAC-SHA256\n" + now + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
        byte[] signingKey = hmac(("HMAC-SHA256" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        signingKey = hmac(signingKey, region);
        signingKey = hmac(signingKey, service);
        signingKey = hmac(signingKey, "request");
        String signature = hex(hmac(signingKey, stringToSign));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Date", now);
        headers.put("Authorization", "HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=content-type;x-date, Signature=" + signature);
        return headers;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate SHA-256 hash", e);
        }
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate HMAC-SHA256 signature", e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
