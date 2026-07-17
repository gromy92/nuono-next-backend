package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.noon.NoonSessionGateway;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayPullSessionFactoryTest {

    @Test
    void backgroundPullMustNotUseEmailOtpWhenCookieNeedsAuthentication() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGatewayPullSessionFactory factory = new NoonSessionGatewayPullSessionFactory(gateway);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                308L,
                "PRJ313934",
                "STR313934-NAE",
                "AE",
                "313934",
                "merchant@example.com",
                "legacy-password",
                "imap-secret",
                "sid=expired"
        );

        factory.login(binding);

        verify(gateway).loginWithPersistedCookie(
                308L,
                "merchant@example.com",
                "sid=expired",
                "PRJ313934",
                "STR313934-NAE"
        );
        verify(gateway, never()).login(any(), any(), any(), any(), any(), any());
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
                null,
                null,
                "sid=expired"
        );

        assertThrows(NoonInterfacePullException.class, () -> factory.login(binding));

        verifyNoInteractions(gateway);
    }
}
