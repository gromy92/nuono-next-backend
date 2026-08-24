package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoonProjectSessionRecoveryTest {

    @Test
    void missingCookieKeepsProjectRecoverableInsteadOfCreatingManualHold() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGateway.EmailIdentityGrant grant = identityGrant();
        NoonAuthRecoveryProjectTarget target = new NoonAuthRecoveryProjectTarget(
                307L, "PRJ7001", "STR7001-NAE", "AE", 9L
        );
        when(gateway.createEmailOtpProjectSession(grant, "PRJ7001", "STR7001-NAE"))
                .thenThrow(new NoonProjectSessionCookieMissingException());

        NoonAuthRecoveryProjectResult result = new NoonProjectSessionRecovery(gateway)
                .recover(
                        grant,
                        "merchant@example.com",
                        List.of(target),
                        new NoonAuthRecoveryAttemptCommand(
                                91L,
                                1,
                                Instant.parse("2026-08-24T08:00:00Z"),
                                Set.of(),
                                List.of(target),
                                () -> true,
                                () -> true
                        )
                )
                .get(0);

        assertTrue(result.isTransientFailure());
        assertEquals(
                NoonAuthRecoveryFailureStage.PROJECT_SESSION_CREATE,
                result.getFailureStage()
        );
        assertEquals(
                NoonTransientErrorType.PROJECT_SESSION_COOKIE_MISSING,
                result.getTransientErrorType()
        );
    }

    private NoonSessionGateway.EmailIdentityGrant identityGrant() {
        NoonSessionGateway fixture = new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 0L, true,
                "", "", "", "", true,
                "http://noon.test/whoami", "http://noon.test/lookup",
                "http://noon.test/pkce", "http://noon.test/generate",
                "http://noon.test/validate", "http://noon.test/projects",
                "http://noon.test/session-create", false, "HTTP", "", 0, ""
        );
        return fixture.restoreEmailIdentityGrant(new NoonAuthGatewayCheckpointCodec.GrantSnapshot(
                new NoonAuthGatewayCheckpointCodec.GenerationSnapshot(
                        "merchant@example.com", "USER-1", "verifier", "pkce", ""
                ),
                "access-token",
                List.of("PRJ7001")
        ));
    }
}
