package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonDataCompletenessSafeTextTest {

    @Test
    void redactsCredentialsBearerTokensApiKeysAndDownloadUrls() {
        String redacted = NoonDataCompletenessSafeText.redact(
                "cookie=abc password=secret Authorization: Bearer token-123 api_key=live "
                        + "https://download.noon.test/raw.csv"
        );

        assertFalse(redacted.contains("abc"));
        assertFalse(redacted.contains("secret"));
        assertFalse(redacted.contains("token-123"));
        assertFalse(redacted.contains("live"));
        assertFalse(redacted.contains("download.noon.test"));
        assertTrue(redacted.contains("[REDACTED]"));
        assertTrue(redacted.contains("[REDACTED_URL]"));
    }
}
