package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonauth.NoonAuthCheckpointVault;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayAuthRecoveryGatewayTest {
    private static final Instant NOW = Instant.parse("2026-08-24T08:00:00Z");
    private final NoonAuthCheckpointVault vault = mock(NoonAuthCheckpointVault.class);

    @Test
    void onlyAuthenticatedIdentityCheckpointCanResumeProjectSessionCooldown() {
        when(vault.load(81L, NOW)).thenReturn(Optional.of(checkpoint(
                NoonAuthCheckpointVault.Kind.IDENTITY_GRANT
        )));
        when(vault.load(82L, NOW)).thenReturn(Optional.of(checkpoint(
                NoonAuthCheckpointVault.Kind.OTP_CHALLENGE
        )));

        NoonSessionGatewayAuthRecoveryGateway gateway = gateway();

        assertTrue(gateway.canResumeAuthenticatedIdentity(81L));
        assertFalse(gateway.canResumeAuthenticatedIdentity(82L));
    }

    private NoonSessionGatewayAuthRecoveryGateway gateway() {
        return new NoonSessionGatewayAuthRecoveryGateway(
                mock(NoonSessionGateway.class),
                mock(NoonEmailOtpReader.class),
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC),
                duration -> { },
                "imap-secret",
                vault,
                new NoonAuthGatewayCheckpointCodec(new ObjectMapper()),
                Duration.ofMinutes(10)
        );
    }

    private NoonAuthCheckpointVault.Checkpoint checkpoint(NoonAuthCheckpointVault.Kind kind) {
        return new NoonAuthCheckpointVault.Checkpoint(
                1,
                kind,
                "encrypted-payload",
                NOW.plus(Duration.ofMinutes(10))
        );
    }
}
