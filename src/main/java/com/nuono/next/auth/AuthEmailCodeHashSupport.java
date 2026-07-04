package com.nuono.next.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class AuthEmailCodeHashSupport {

    private AuthEmailCodeHashSupport() {
    }

    static String hash(String email, String purpose, String code, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((email + ":" + purpose + ":" + code + ":" + salt)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte current : hashed) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法生成邮箱验证码摘要。", error);
        }
    }
}
