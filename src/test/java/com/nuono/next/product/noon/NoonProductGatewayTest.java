package com.nuono.next.product.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class NoonProductGatewayTest {
    private final NoonProductGateway gateway = new NoonProductGateway();

    @Test
    void shouldClassifyInvalidCredential() {
        NoonProductError error = gateway.classify(
                new IllegalStateException("Noon password validate 失败：Invalid username or password")
        );

        assertEquals(NoonProductErrorCode.NOON_CREDENTIAL_INVALID, error.getCode());
        assertFalse(error.isRetryable());
    }

    @Test
    void shouldClassifyTlsCertificateFailure() {
        NoonProductError error = gateway.classify(
                new IllegalStateException(
                        "请求 Noon 失败",
                        new SSLHandshakeException(
                                "PKIX path building failed: unable to find valid certification path to requested target"
                        )
                )
        );

        assertEquals(NoonProductErrorCode.NOON_TLS_CERTIFICATE_FAILURE, error.getCode());
        assertFalse(error.isRetryable());
    }

    @Test
    void shouldClassifyMissingProjectScope() {
        NoonProductError error = gateway.classify(
                new IllegalStateException("project.list 未返回目标项目 PRJ108065")
        );

        assertEquals(NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING, error.getCode());
        assertFalse(error.isRetryable());
    }

    @Test
    void shouldClassifyRateLimitAsRetryable() {
        NoonProductError error = gateway.classifyHttpFailure(429, "Too Many Requests");

        assertEquals(NoonProductErrorCode.NOON_RATE_LIMITED, error.getCode());
        assertTrue(error.isRetryable());
    }
}
