package com.nuono.next.noonpull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class NoonReportDigest {
    private NoonReportDigest() {
    }

    static String sha256(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content);
            StringBuilder value = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
