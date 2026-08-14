package com.nuono.next.product.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import org.junit.jupiter.api.Test;

class ProductNoonAdapterAuthRecoveryTest {

    @Test
    void unavailableSharedAccountStopsBeforePersistedCookieLogin() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
        when(attention.blocksProviderCalls()).thenReturn(true);
        ProductNoonAdapter adapter = adapter(sessionGateway, attention);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L, "operator@example.com", "cookie", "PRJ-1", "STR108065-NAE")
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        verify(sessionGateway, never()).loginWithPersistedCookie(
                any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void freshCookieAuthFailureOnlyRequestsManualLogin() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
        when(sessionGateway.loginWithPersistedCookie(
                307L, "operator@example.com", "cookie", "PRJ-1", "STR108065-NAE"))
                .thenThrow(new IllegalStateException("auth_required: WHOAMI HTTP 307"));
        ProductNoonAdapter adapter = adapter(sessionGateway, attention);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L, "operator@example.com", "cookie", "PRJ-1", "STR108065-NAE")
        );

        assertNull(exception.getRecoveryId());
        assertFalse(exception.isWriteMayHaveOccurred());
        verify(attention).requireManualLogin();
    }

    @Test
    void projectScopeFailureDoesNotRequestManualLogin() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
        when(sessionGateway.loginWithPersistedCookie(
                307L, "operator@example.com", "cookie", "PRJ-404", "STR108065-NAE"))
                .thenThrow(new IllegalStateException(
                        "auth_required: account does not contain current project PRJ-404"));
        ProductNoonAdapter adapter = adapter(sessionGateway, attention);

        NoonProductException exception = assertThrows(
                NoonProductException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L, "operator@example.com", "cookie", "PRJ-404", "STR108065-NAE")
        );

        verify(attention, never()).requireManualLogin();
        org.junit.jupiter.api.Assertions.assertEquals(
                NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING, exception.getCode());
    }

    @Test
    void authResponseEnvelopeRequiresManualLoginWithoutCreatingRecoveryId() {
        NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
        ProductNoonAdapter adapter = adapter(mock(NoonSessionGateway.class), attention);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.requireNoAuthResponse(
                        307L, "PRJ-1", "STR108065-NAE",
                        new ObjectMapper().createObjectNode().put("error", "auth_required"))
        );

        assertNull(exception.getRecoveryId());
        verify(attention).requireManualLogin();
    }

    private ProductNoonAdapter adapter(
            NoonSessionGateway sessionGateway,
            NoonAccountSessionAttentionPort attention
    ) {
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(attention));
        return adapter;
    }
}
