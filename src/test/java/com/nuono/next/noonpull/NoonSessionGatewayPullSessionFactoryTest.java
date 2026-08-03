package com.nuono.next.noonpull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonSessionGateway;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayPullSessionFactoryTest {

    @Test
    void backgroundPullUsesThePersistedProjectCookieSession() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGatewayPullSessionFactory factory = new NoonSessionGatewayPullSessionFactory(gateway);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                308L,
                "PRJ313934",
                "STR313934-NAE",
                "AE",
                "313934",
                "merchant@example.com",
                "project-session-user",
                "sid=expired"
        );

        factory.login(binding);

        verify(gateway).loginWithPersistedCookie(
                308L,
                "project-session-user",
                "sid=expired",
                "PRJ313934",
                "STR313934-NAE"
        );
    }

    @Test
    void blockedProjectMustStopBeforeAnyNoonHttpSessionCall() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGatewayPullSessionFactory factory = new NoonSessionGatewayPullSessionFactory(gateway);
        factory.setProjectAuthGate((ownerUserId, projectCode) ->
                Long.valueOf(308L).equals(ownerUserId) && "PRJ313934".equals(projectCode));
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                308L,
                "PRJ313934",
                "STR313934-NAE",
                "AE",
                "313934",
                "merchant@example.com",
                "sid=expired"
        );

        NoonAuthenticationRequiredException failure = assertThrows(
                NoonAuthenticationRequiredException.class,
                () -> factory.login(binding)
        );

        assertTrue(NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure));
        verifyNoInteractions(gateway);
    }
}
