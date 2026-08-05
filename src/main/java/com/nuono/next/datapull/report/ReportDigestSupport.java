package com.nuono.next.datapull.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Package-local SHA-256 helpers used by the report persistence boundaries. */
final class ReportDigestSupport {
    private ReportDigestSupport() {
    }

    static String sha256(byte[] value) {
        return hex(newSha256().digest(value));
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required", failure);
        }
    }

    static String hex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            hex.append(String.format("%02x", item & 0xff));
        }
        return hex.toString();
    }
}
