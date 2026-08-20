package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryCheckpointMapper;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AesGcmNoonAuthCheckpointVaultTest {
    @Test
    void shouldEncryptAndRestoreRestartCheckpoint() {
        AtomicReference<NoonAuthRecoveryCheckpointRecord> stored = new AtomicReference<>();
        NoonAuthRecoveryCheckpointMapper mapper = mock(NoonAuthRecoveryCheckpointMapper.class);
        when(mapper.upsert(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.select(41L)).thenAnswer(ignored -> stored.get());
        AesGcmNoonAuthCheckpointVault vault = vault(mapper);
        Instant expiresAt = Instant.parse("2026-08-20T12:10:00Z");

        vault.save(41L, 1, NoonAuthCheckpointVault.Kind.OTP_CHALLENGE,
                "pkce-secret-payload", expiresAt);

        assertNotEquals("pkce-secret-payload", new String(stored.get().getCiphertext()));
        NoonAuthCheckpointVault.Checkpoint restored = vault.load(
                41L, Instant.parse("2026-08-20T12:05:00Z")).orElseThrow();
        assertEquals(1, restored.getGeneration());
        assertEquals(NoonAuthCheckpointVault.Kind.OTP_CHALLENGE, restored.getKind());
        assertEquals("pkce-secret-payload", restored.getPayload());
    }

    @Test
    void shouldRejectCiphertextMovedToAnotherRecovery() {
        AtomicReference<NoonAuthRecoveryCheckpointRecord> stored = new AtomicReference<>();
        NoonAuthRecoveryCheckpointMapper mapper = mock(NoonAuthRecoveryCheckpointMapper.class);
        when(mapper.upsert(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        AesGcmNoonAuthCheckpointVault vault = vault(mapper);
        vault.save(41L, 1, NoonAuthCheckpointVault.Kind.IDENTITY_GRANT,
                "grant-secret", Instant.parse("2026-08-20T12:10:00Z"));
        stored.get().setRecoveryId(42L);
        when(mapper.select(42L)).thenReturn(stored.get());

        assertThrows(IllegalStateException.class, () -> vault.load(
                42L, Instant.parse("2026-08-20T12:05:00Z")));
        assertFalse(vault.load(99L, Instant.parse("2026-08-20T12:05:00Z")).isPresent());
    }

    private AesGcmNoonAuthCheckpointVault vault(NoonAuthRecoveryCheckpointMapper mapper) {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setCheckpointCipherSecret("test-only-checkpoint-key");
        properties.setCheckpointKeyVersion("test-v1");
        return new AesGcmNoonAuthCheckpointVault(mapper, properties);
    }
}
