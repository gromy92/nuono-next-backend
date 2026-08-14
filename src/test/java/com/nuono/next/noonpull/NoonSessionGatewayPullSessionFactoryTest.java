package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
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
    void unavailableSharedAccountMustStopBeforeAnyNoonHttpSessionCall() {
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonSessionGatewayPullSessionFactory factory = new NoonSessionGatewayPullSessionFactory(gateway);
        NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
        org.mockito.Mockito.when(attention.blocksProviderCalls()).thenReturn(true);
        factory.setAccountSessionAttention(attention);
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

    @Test
    void oneShotSessionMustNotRunTheWhoamiBackedLoginPath() {
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
                "sid=persisted"
        );

        factory.openOneShot(binding);

        verify(gateway).openWithPersistedCookieWithoutProbe(
                308L,
                "project-session-user",
                "sid=persisted",
                "PRJ313934",
                "STR313934-NAE"
        );
        verify(gateway, never()).loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        );
    }
}
