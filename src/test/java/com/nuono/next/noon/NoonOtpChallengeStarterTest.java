package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonAuthCheckpointVault;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonOtpChallengeStarterTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private final NoonSessionGateway gateway = mock(NoonSessionGateway.class);
    private final NoonEmailOtpReader reader = mock(NoonEmailOtpReader.class);
    private final NoonAuthCheckpointVault vault = mock(NoonAuthCheckpointVault.class);
    private final NoonAuthGatewayCheckpointCodec codec =
            new NoonAuthGatewayCheckpointCodec(new ObjectMapper());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void persistsChallengeAfterSendIntentAndBeforeAnyProviderSend() {
        NoonSessionGateway.EmailOtpGeneration generation = actualGeneration();
        NoonEmailOtpReader.MailboxCursor cursor =
                new NoonEmailOtpReader.MailboxCursor(8L, 99L, NOW.minusSeconds(1));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("shared@example.com");
        when(vault.load(91L, NOW)).thenReturn(Optional.empty());
        when(gateway.prepareEmailOtpGeneration("shared@example.com")).thenReturn(generation);
        when(reader.snapshot("shared@example.com", "imap-secret")).thenReturn(cursor);
        when(gateway.snapshotEmailOtpGeneration(generation)).thenReturn(snapshot());
        AtomicInteger sendIntents = new AtomicInteger();

        NoonOtpChallengeStarter.StartResult result = starter().start(command(sendIntents));

        assertFalse(result.resumedChallenge);
        assertEquals(1, sendIntents.get());
        verify(vault).save(
                eq(91L), eq(1), eq(NoonAuthCheckpointVault.Kind.OTP_CHALLENGE),
                any(), eq(NOW.plus(Duration.ofMinutes(10)))
        );
        verify(gateway, never()).sendEmailOtp(any());
    }

    @Test
    void restoredChallengeSkipsSendIntentAndSnapshot() {
        String payload = codec.encodeChallenge(
                snapshot(),
                new NoonEmailOtpReader.MailboxCursor(8L, 99L, NOW.minusSeconds(1)),
                NOW.minusSeconds(5)
        );
        NoonSessionGateway.EmailOtpGeneration generation = actualGeneration();
        when(gateway.configuredMerchantLoginEmail()).thenReturn("shared@example.com");
        when(vault.load(91L, NOW)).thenReturn(Optional.of(new NoonAuthCheckpointVault.Checkpoint(
                1, NoonAuthCheckpointVault.Kind.OTP_CHALLENGE, payload, NOW.plusSeconds(300)
        )));
        when(gateway.restoreEmailOtpGeneration(any())).thenReturn(generation);
        AtomicInteger sendIntents = new AtomicInteger();

        NoonOtpChallengeStarter.StartResult result = starter().start(command(sendIntents));

        assertTrue(result.resumedChallenge);
        assertEquals(0, sendIntents.get());
        verify(gateway, never()).prepareEmailOtpGeneration(any());
        verify(reader, never()).snapshot(any(), any());
        verify(vault, never()).save(anyLong(), anyInt(), any(), any(), any());
    }

    private NoonOtpChallengeStarter starter() {
        return new NoonOtpChallengeStarter(
                gateway, reader, clock, "imap-secret", vault, codec, Duration.ofMinutes(10)
        );
    }

    private NoonAuthRecoveryAttemptCommand command(AtomicInteger sendIntents) {
        return new NoonAuthRecoveryAttemptCommand(
                91L, 1, NOW, Set.of(),
                List.of(new NoonAuthRecoveryProjectTarget(307L, "PRJ-A", "STORE-A", "AE", 7L)),
                () -> true,
                () -> sendIntents.incrementAndGet() == 1
        );
    }

    private NoonAuthGatewayCheckpointCodec.GenerationSnapshot snapshot() {
        return new NoonAuthGatewayCheckpointCodec.GenerationSnapshot(
                "shared@example.com", "USER-1", "verifier", "pkce", "cookie=value"
        );
    }

    private NoonSessionGateway.EmailOtpGeneration actualGeneration() {
        return fixtureGateway().restoreEmailOtpGeneration(snapshot());
    }

    private NoonSessionGateway fixtureGateway() {
        return new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 0L, true,
                "", "", "", "", true,
                "http://noon.test/whoami", "http://noon.test/lookup",
                "http://noon.test/pkce", "http://noon.test/generate",
                "http://noon.test/validate", "http://noon.test/projects",
                "http://noon.test/session-create", false, "HTTP", "", 0, ""
        );
    }
}
