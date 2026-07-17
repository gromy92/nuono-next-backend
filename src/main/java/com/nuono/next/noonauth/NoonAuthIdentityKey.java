package com.nuono.next.noonauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class NoonAuthIdentityKey {
    private NoonAuthIdentityKey() {
    }

    public static String fromEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Noon auth recovery requires a configured email identity.");
        }
        return sha256(email.trim().toLowerCase(Locale.ROOT));
    }

    public static String configFingerprint(String email, String mailboxAuthCode) {
        if (!StringUtils.hasText(mailboxAuthCode)) {
            throw new IllegalArgumentException("Noon auth recovery requires a configured mailbox credential.");
        }
        return sha256(fromEmail(email) + ":" + mailboxAuthCode.trim());
    }

    public static String configFingerprint(
            String email,
            String mailboxAuthCode,
            Collection<String> trustedSenderDomains
    ) {
        String normalizedDomains = trustedSenderDomains == null
                ? ""
                : trustedSenderDomains.stream()
                .map(NoonAuthRecoveryProperties::normalizeTrustedSenderDomain)
                .filter(StringUtils::hasText)
                .sorted()
                .collect(Collectors.joining(","));
        if (!StringUtils.hasText(normalizedDomains)) {
            return configFingerprint(email, mailboxAuthCode);
        }
        if (!StringUtils.hasText(mailboxAuthCode)) {
            throw new IllegalArgumentException("Noon auth recovery requires a configured mailbox credential.");
        }
        return sha256(fromEmail(email)
                + ":" + mailboxAuthCode.trim()
                + ":trusted-sender-domains:" + normalizedDomains);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash Noon auth identity.", exception);
        }
    }
}
