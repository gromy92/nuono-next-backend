package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class NoonTransientTransportFailurePolicyTest {

    @Test
    void proxyRefreshShouldInspectNestedTransientTransportFailure() {
        IllegalStateException failure = new IllegalStateException(
                "request failed",
                new EOFException("HTTP/1.1 header parser received no bytes")
        );

        assertTrue(NoonTransientTransportFailurePolicy.shouldRefresh(true, failure));
        assertFalse(NoonTransientTransportFailurePolicy.shouldRefresh(false, failure));
        assertFalse(NoonTransientTransportFailurePolicy.shouldRefresh(
                true,
                new IllegalStateException("HTTP 400 bad request")
        ));
    }

    @Test
    void listedTransientFailuresShouldRefreshProxy() {
        for (Throwable failure : new Throwable[] {
                new EOFException("unexpected EOF"),
                new IOException("connection timeout"),
                new IllegalStateException("HTTP 408"),
                new IllegalStateException("HTTP 500"),
                new IllegalStateException("HTTP 502"),
                new IllegalStateException("HTTP 503"),
                new IllegalStateException("HTTP 504")
        }) {
            assertTrue(NoonTransientTransportFailurePolicy.shouldRefresh(
                    true,
                    failure
            ));
        }
    }

    @Test
    void retryClassifierShouldAcceptTypedTransportFailuresAndListedHttpStatuses() {
        assertTrue(NoonTransientTransportFailurePolicy.isRetryable(new EOFException("stream ended")));
        assertTrue(NoonTransientTransportFailurePolicy.isRetryable(
                new HttpConnectTimeoutException("Connect timed out")
        ));
        assertTrue(NoonTransientTransportFailurePolicy.isRetryable(
                new NoonHttpException(500, "provider unavailable", "/catalog")
        ));
    }

    @Test
    void permanentOrUnlistedFailuresShouldNotBeRetried() {
        for (Throwable failure : new Throwable[] {
                new NoonHttpException(400, "invalid parameter", "/catalog"),
                new IllegalArgumentException("missing partnerSku"),
                new IllegalStateException("HTTP 403 Access Denied"),
                new IllegalStateException("HTTP 429 Too Many Requests"),
                new IllegalStateException("HTTP 435 connection timed out"),
                new IllegalStateException("HTTP 436 read timed out"),
                new IllegalStateException("HTTP 500 followed by HTTP 435 connection timed out"),
                new IOException("connection reset"),
                new HttpTimeoutException("request timed out"),
                new SocketTimeoutException("read timed out"),
                new IllegalStateException("connection refused")
        }) {
            assertFalse(NoonTransientTransportFailurePolicy.isRetryable(failure));
        }
    }
}
