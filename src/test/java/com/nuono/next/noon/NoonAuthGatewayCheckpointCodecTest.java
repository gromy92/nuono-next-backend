package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAuthGatewayCheckpointCodecTest {
    private final NoonAuthGatewayCheckpointCodec codec =
            new NoonAuthGatewayCheckpointCodec(new ObjectMapper());

    @Test
    void shouldRoundTripChallengeAndMailboxCursor() {
        NoonAuthGatewayCheckpointCodec.GenerationSnapshot generation = generation();
        NoonEmailOtpReader.MailboxCursor cursor = new NoonEmailOtpReader.MailboxCursor(
                8L, 99L, Instant.parse("2026-08-20T12:00:00Z"));

        NoonAuthGatewayCheckpointCodec.Challenge restored = codec.decodeChallenge(
                codec.encodeChallenge(generation, cursor, Instant.parse("2026-08-20T12:00:03Z")));

        assertEquals("shared@example.com", restored.generation.getEmail());
        assertEquals("verifier", restored.generation.getCodeVerifier());
        assertEquals(8L, restored.cursor.getUidValidity());
        assertEquals(99L, restored.cursor.getLastUid());
        assertEquals(Instant.parse("2026-08-20T12:00:03Z"), restored.sentAt);
    }

    @Test
    void shouldRoundTripIdentityGrantForProjectDistributionResume() {
        NoonAuthGatewayCheckpointCodec.GrantSnapshot grant =
                new NoonAuthGatewayCheckpointCodec.GrantSnapshot(
                        generation(), "access-token", List.of("PRJ-A", "PRJ-B"));

        NoonAuthGatewayCheckpointCodec.GrantSnapshot restored =
                codec.decodeGrant(codec.encodeGrant(grant));

        assertEquals("access-token", restored.getAccessToken());
        assertEquals(List.of("PRJ-A", "PRJ-B"), restored.getProjectCodes());
        assertEquals("cookie=value", restored.getGeneration().getCookieHeader());
    }

    private NoonAuthGatewayCheckpointCodec.GenerationSnapshot generation() {
        return new NoonAuthGatewayCheckpointCodec.GenerationSnapshot(
                "shared@example.com", "USER-1", "verifier", "pkce-key", "cookie=value");
    }
}
