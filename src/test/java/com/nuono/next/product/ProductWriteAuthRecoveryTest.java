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

import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductWriteAuthRecoveryTest {
    private final NoonAuthWaitQueue recoveryQueue = mock(NoonAuthWaitQueue.class);
    private final NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
    private final ProductWriteAuthRecovery recovery =
            new ProductWriteAuthRecovery(recoveryQueue, authGate);

    @Test
    void activeSharedRecoveryStopsBeforeWriteAndRetainsAutomaticResume() {
        when(authGate.isBlocked(307L, "PRJ-1")).thenReturn(true);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> recovery.requireAvailable(307L, "PRJ-1", "STR108065-NAE")
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        assertNull(exception.getRecoveryId());
        assertTrue(exception.getMessage().contains("恢复成功后将从安全检查点自动继续"));
    }

    @Test
    void explicitAuthFailureQueuesRecoveryAndRetainsReadbackSafety() {
        when(recoveryQueue.enqueue(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(991L));
        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "PRJ-1",
                "STR108065-NAE",
                new NoonAuthenticationRequiredException("Noon authentication required."),
                true
        );

        assertNotNull(exception);
        assertTrue(exception.getRecoveryId() == 991L);
        assertTrue(exception.isWriteMayHaveOccurred());
        assertTrue(exception.getMessage().contains("先回读 Noon 结果"));
        verify(recoveryQueue).enqueue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dedicatedAuthenticationFailuresAndExplicit401QueueAutomaticRecovery() {
        for (RuntimeException failure : new RuntimeException[] {
                new NoonAuthenticationRequiredException("Noon authentication required."),
                new NoonHttpException(401, "", "/catalog")
        }) {
            ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                    307L, "PRJ-1", "STR108065-NAE", failure, false);
            assertNotNull(exception);
            assertFalse(exception.isWriteMayHaveOccurred());
        }
        verify(recoveryQueue, times(2)).enqueue(org.mockito.ArgumentMatchers.any());
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
