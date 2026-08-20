package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NoonSessionGatewaySharedAuthQueueTest {

    @Test
    void missingProjectCookieShouldJoinOneSharedAutomaticRecoveryBeforeFailing() {
        NoonSessionGateway gateway = gateway();
        AtomicReference<NoonAuthWaitRequest> queued = new AtomicReference<>();
        gateway.setAuthWaitQueue(request -> {
            queued.set(request);
            return java.util.Optional.of(91L);
        });

        NoonSessionGateway.NoonCookieAuthRequiredException failure = assertThrows(
                NoonSessionGateway.NoonCookieAuthRequiredException.class,
                () -> gateway.loginWithPersistedCookie(
                        307L,
                        "operator@example.com",
                        null,
                        "PRJ245027",
                        "STR245027-NAE"
                )
        );

        assertTrue(failure.getMessage().contains("auth_required"));
        assertTrue(NoonAuthWaitRequest.binding(
                307L, "PRJ245027", "STR245027-NAE"
        ).equals(queued.get()));
    }

    private NoonSessionGateway gateway() {
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                "http://noon.test/whoami",
                "http://noon.test/lookup",
                "http://noon.test/pkce",
                "http://noon.test/generate",
                "http://noon.test/validate",
                "http://noon.test/projects",
                "http://noon.test/session-create",
                false,
                "HTTP",
                "",
                0,
                ""
        );
    }
}
