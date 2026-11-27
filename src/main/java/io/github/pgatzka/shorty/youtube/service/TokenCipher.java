package io.github.pgatzka.shorty.youtube.service;

import io.github.pgatzka.shorty.youtube.domain.YouTubeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for the stored refresh token. The key is a base64-encoded 32-byte value
 * from {@code SHORTY_ENCRYPTION_KEY}.
 */
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${shorty.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(base64Key.strip());
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("shorty.encryption-key must be 32 bytes, base64-encoded");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plainText) {
        SecretKey activeKey = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new YouTubeException("Could not encrypt token", e);
        }
    }

    public String decrypt(String encoded) {
        SecretKey activeKey = requireKey();
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length <= IV_BYTES) {
                throw new YouTubeException("Stored token is corrupt");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new YouTubeException("Could not decrypt the stored token. Was the encryption key changed?", e);
        }
    }

    private SecretKey requireKey() {
        if (key == null) {
            throw new YouTubeException("Encryption key (SHORTY_ENCRYPTION_KEY) is not configured");
        }
        return key;
    }
}
