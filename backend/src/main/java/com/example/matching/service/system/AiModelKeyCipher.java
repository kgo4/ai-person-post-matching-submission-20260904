package com.example.matching.service.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AI 模型密钥加密工具：部署密钥只从环境变量读取，密钥以 AES-GCM 加密保存。
 * <p>
 * 未配置部署密钥时不阻断应用启动：加密/解密降级为不可用（返回 null 并记 ERROR），
 * 模型密钥无法落库加密，由管理员在配置页明确提示。
 */
@Slf4j
@Service
public class AiModelKeyCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] keyBytes;
    private final boolean available;

    public AiModelKeyCipher(@Value("${ai-model.deployment-key:}") String deploymentKey) {
        if (deploymentKey == null || deploymentKey.isBlank()) {
            this.keyBytes = null;
            this.available = false;
            log.error("AI model deployment key is not configured. Set environment variable AI_MODEL_DEPLOYMENT_KEY "
                    + "to enable encrypted storage of the enterprise model api key.");
            return;
        }
        this.keyBytes = deriveKey(deploymentKey);
        this.available = true;
    }

    /** 部署密钥是否已配置（未配置时模型密钥无法安全落库）。 */
    public boolean isAvailable() {
        return available;
    }

    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AI model encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        if (!available) {
            log.error("AI model deployment key is not configured; refusing to store plaintext api key");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt AI model api key", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!available) {
            log.error("AI model deployment key is not configured; cannot decrypt stored api key");
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt AI model api key", e);
            return null;
        }
    }
}
