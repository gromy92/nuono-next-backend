package com.nuono.next.competitoranalysis.noon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;

final class NoonSearchEvidenceSupport {
    private NoonSearchEvidenceSupport() {
    }

    static void merge(
            NoonSearchPage primary,
            NoonSearchPage alternate
    ) {
        if (primary == null || alternate == null) {
            return;
        }
        primary.setResponseHash(combinedHash(
                primary.getResponseHash(),
                alternate.getResponseHash()
        ));
        if (alternate.getProviderHttpStatus() != null) {
            primary.setProviderHttpStatus(
                    alternate.getProviderHttpStatus()
            );
        }
        LocalDateTime alternateCapturedAt = alternate.getCapturedAt();
        if (alternateCapturedAt != null
                && (primary.getCapturedAt() == null
                || alternateCapturedAt.isAfter(primary.getCapturedAt()))) {
            primary.setCapturedAt(alternateCapturedAt);
        }
    }

    private static String combinedHash(
            String primary,
            String alternate
    ) {
        String value = String.valueOf(primary)
                + "|"
                + String.valueOf(alternate);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format(
                        Locale.ROOT,
                        "%02x",
                        item & 0xff
                ));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Noon 双语言列表响应 hash 计算失败。",
                    exception
            );
        }
    }
}
