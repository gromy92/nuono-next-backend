package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyPasswordCodecTest {

    @Test
    void shouldMatchPlainAndLegacySaltedPasswords() {
        assertTrue(LegacyPasswordCodec.matchesStoredPassword("admin123", "admin123"));
        assertTrue(
                LegacyPasswordCodec.matchesStoredPassword(
                        "Ahoney$123",
                        LegacyPasswordCodec.encryptWithSalt("Ahoney$123", LegacyPasswordCodec.LEGACY_SALT)
                )
        );
        assertFalse(LegacyPasswordCodec.matchesStoredPassword("admin123", "wrong-pass"));
    }

    @Test
    void shouldMatchLegacySuperPassword() {
        assertTrue(LegacyPasswordCodec.matchesLegacySuperPassword("Ahoney$123"));
        assertFalse(LegacyPasswordCodec.matchesLegacySuperPassword("admin123"));
    }
}
