package com.nuono.next.product.publish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ProductPublishTransientFailureClassificationTest {

    private final ProductPublishCommandService service =
            new ProductPublishCommandService(mock(ProductManagementMapper.class));

    @Test
    void listedTransientFailuresShouldEnterAutomaticBackoff() {
        assertTrue(service.isRetryableNoonRequestFailure(
                new IllegalStateException("request failed", new EOFException("unexpected EOF"))
        ));
        assertTrue(service.isRetryableNoonRequestFailure(
                new IOException("connection timeout")
        ));
        assertTrue(service.isRetryableNoonRequestFailure(
                new NoonHttpException(500, "provider unavailable", "/catalog")
        ));
    }

    @Test
    void permanentFailuresShouldNeverEnterAutomaticBackoff() {
        assertFalse(service.isRetryableNoonRequestFailure(
                new IOException("connection reset by peer")
        ));
        assertFalse(service.isRetryableNoonRequestFailure(
                new NoonHttpException(400, "invalid partnerSku", "/catalog")
        ));
        assertFalse(service.isRetryableNoonRequestFailure(
                new IllegalArgumentException("partnerSku is required")
        ));
        assertFalse(service.isRetryableNoonRequestFailure(
                new IllegalStateException("Noon business validation failed")
        ));
    }

    @Test
    void typedRetryableFlagShouldNotBypassTransportWhitelist() {
        assertFalse(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_RATE_LIMITED,
                true,
                new IllegalStateException("rate limited")
        )));
        assertFalse(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_TLS_CERTIFICATE_FAILURE,
                true,
                new IllegalStateException("certificate rejected")
        )));
        assertFalse(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING,
                true,
                new IllegalStateException("project access denied")
        )));
        assertFalse(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_CREDENTIAL_INVALID,
                true,
                new IllegalStateException("bad credentials")
        )));
        assertFalse(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_REQUEST_FAILED,
                true,
                new IllegalStateException("business validation failed")
        )));
    }

    @Test
    void genericTypedFailureShouldStillUseWhitelistedCause() {
        assertTrue(service.isRetryableNoonRequestFailure(typedFailure(
                NoonProductErrorCode.NOON_REQUEST_FAILED,
                false,
                new NoonHttpException(500, "provider unavailable", "/catalog")
        )));
    }

    @Test
    void typedAuthShouldWinOverNestedTransientFailure() {
        NoonProductException authFailure = typedFailure(
                NoonProductErrorCode.NOON_AUTH_REQUIRED,
                true,
                new NoonHttpException(500, "provider unavailable", "/whoami")
        );

        assertFalse(service.isRetryableNoonRequestFailure(authFailure));
    }

    private NoonProductException typedFailure(
            NoonProductErrorCode code,
            boolean retryable,
            Throwable cause
    ) {
        return new NoonProductException(
                new NoonProductError(code, retryable, "typed Noon failure"),
                cause
        );
    }
}
