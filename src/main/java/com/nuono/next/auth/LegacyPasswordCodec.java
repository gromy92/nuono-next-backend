package com.nuono.next.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class LegacyPasswordCodec {

    static final String LEGACY_SALT = "noon_helper";
    static final String LEGACY_SUPER_PASSWORD_HASH = "1bb2ded28503fce30f3c02fcf867a56b";

    private LegacyPasswordCodec() {
    }

    public static boolean matchesStoredPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        return storedPassword.equals(rawPassword) || storedPassword.equals(encryptWithSalt(rawPassword, LEGACY_SALT));
    }

    public static boolean matchesLegacySuperPassword(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        return LEGACY_SUPER_PASSWORD_HASH.equals(encryptWithSalt(rawPassword, LEGACY_SALT));
    }

    public static String encryptWithSalt(String rawPassword, String salt) {
        return encrypt(rawPassword + salt);
    }

    static String encrypt(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                String hex = Integer.toHexString(value & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 MD5。", exception);
        }
    }
}
