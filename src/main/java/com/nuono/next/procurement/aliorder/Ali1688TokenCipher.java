package com.nuono.next.procurement.aliorder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Ali1688TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final SecureRandom random = new SecureRandom();

    public Ali1688TokenCipher(Ali1688HistoricalOrderOpenApiProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt 1688 token.", exception);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return cipherText;
        }
        if (!cipherText.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported 1688 token cipher version.");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(cipherText.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid 1688 token cipher payload.");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt 1688 token.", exception);
        }
    }

    private SecretKeySpec key() {
        String secret = properties == null ? null : properties.getTokenCipherSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("1688 token cipher secret must be configured before storing real provider tokens.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize 1688 token cipher secret.", exception);
        }
    }
}
