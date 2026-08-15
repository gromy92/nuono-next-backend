package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import org.junit.jupiter.api.Test;

class ProductWriteAuthRecoveryTest {
    private final NoonAccountSessionAttentionPort attention = mock(NoonAccountSessionAttentionPort.class);
    private final ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(attention);

    @Test
    void unavailableSharedAccountStopsBeforeWriteWithoutCreatingRecoveryTask() {
        when(attention.blocksProviderCalls()).thenReturn(true);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> recovery.requireAvailable(307L, "PRJ-1", "STR108065-NAE")
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        assertNull(exception.getRecoveryId());
        assertTrue(exception.getMessage().contains("不会自动发送验证码、重试或继续"));
    }

    @Test
    void explicitAuthFailureOnlyRequestsManualLoginAndRetainsReadbackSafety() {
        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "PRJ-1",
                "STR108065-NAE",
                new NoonAuthenticationRequiredException("Noon authentication required."),
                true
        );

        assertNotNull(exception);
        assertNull(exception.getRecoveryId());
        assertTrue(exception.isWriteMayHaveOccurred());
        assertTrue(exception.getMessage().contains("先回读 Noon 结果"));
        verify(attention).requireManualLogin();
    }

    @Test
    void dedicatedAuthenticationFailuresAndExplicit401RequireManualLogin() {
        for (RuntimeException failure : new RuntimeException[] {
                new NoonAuthenticationRequiredException("Noon authentication required."),
                new NoonHttpException(401, "", "/catalog")
        }) {
            ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE", failure, false);
            assertNotNull(exception);
            assertFalse(exception.isWriteMayHaveOccurred());
        }
        verify(attention, times(2)).requireManualLogin();
    }

    @Test
    void redirectAndForbiddenStatusesDoNotClaimAuthenticationExpiry() {
        for (int status : new int[] {302, 307, 403}) {
            assertNull(recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE",
                    new NoonHttpException(status, "", "/catalog/create"), false));
        }
    }

    @Test
    void permanentCredentialAndProjectScopeFailuresNeverRequestManualOtp() {
        NoonProductException scopeFailure = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING,
                        false,
                        "Noon project.list did not return the target project"
                ),
                new IllegalStateException("HTTP 401 project scope missing")
        );
        NoonProductException credentialFailure = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_CREDENTIAL_INVALID,
                        false,
                        "Noon account credentials are invalid"
                ),
                new IllegalStateException("HTTP 401 invalid password")
        );

        assertNull(recovery.suspendIfAuthFailure(
                307L, "PRJ-404", "STR108065-NAE", scopeFailure, false));
        assertNull(recovery.suspendIfAuthFailure(
                307L, "PRJ-1", "STR108065-NAE", credentialFailure, false));
    }
}
