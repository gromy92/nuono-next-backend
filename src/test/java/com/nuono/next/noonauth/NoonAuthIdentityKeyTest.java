package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoonAuthIdentityKeyTest {

    @Test
    void normalizesEmailWithoutPersistingPlaintextIdentity() {
        String first = NoonAuthIdentityKey.fromEmail(" Merchant@Example.COM ");
        String second = NoonAuthIdentityKey.fromEmail("merchant@example.com");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertFalse(first.contains("merchant"));
    }

    @Test
    void configFingerprintChangesWhenMailboxCredentialChanges() {
        assertNotEquals(
                NoonAuthIdentityKey.configFingerprint("merchant@example.com", "first-secret"),
                NoonAuthIdentityKey.configFingerprint("merchant@example.com", "second-secret")
        );
    }

    @Test
    void configFingerprintIncludesNormalizedTrustedSenderDomains() {
        String first = NoonAuthIdentityKey.configFingerprint(
                "merchant@example.com",
                "secret",
                Set.of("mail.noon.com", "noon.com")
        );
        String sameDifferentOrder = NoonAuthIdentityKey.configFingerprint(
                "merchant@example.com",
                "secret",
                Set.of("noon.com", "mail.noon.com")
        );
        String changed = NoonAuthIdentityKey.configFingerprint(
                "merchant@example.com",
                "secret",
                Set.of("notifications.noon.com")
        );

        assertEquals(first, sameDifferentOrder);
        assertNotEquals(first, changed);
    }

    @Test
    void trustedSenderDomainsAreNormalizedSortedAndMatchedAtDomainBoundaries() {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setTrustedSenderDomains(" @Noon.Example. , mail.noon.example,NOON.EXAMPLE ");

        assertEquals(
                List.of("mail.noon.example", "noon.example"),
                List.copyOf(properties.normalizedTrustedSenderDomains())
        );
        assertTrue(properties.allowsTrustedSenderDomain("delivery.noon.example"));
        assertFalse(properties.allowsTrustedSenderDomain("noon.example.evil.test"));
    }
}
