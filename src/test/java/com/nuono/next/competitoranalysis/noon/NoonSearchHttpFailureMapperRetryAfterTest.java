package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoonSearchHttpFailureMapperRetryAfterTest {

    @Test
    void mapsRetryableFrontendStatusesWithDeltaSecondsHints() {
        NoonSearchProviderException risk = NoonSearchHttpFailureMapper.map(
                418,
                "https://www.noon.com/search",
                "41"
        );
        NoonSearchProviderException transientFailure = NoonSearchHttpFailureMapper.map(
                408,
                "https://www.noon.com/search",
                "53"
        );

        assertEquals("BLOCKED_BY_RISK_CONTROL", risk.getErrorCode());
        assertEquals(Duration.ofSeconds(41), risk.getRetryAfter());
        assertEquals("PROVIDER_UNAVAILABLE", transientFailure.getErrorCode());
        assertEquals(Duration.ofSeconds(53), transientFailure.getRetryAfter());
    }

    @Test
    void invalidOrNonRetryableHeadersAreDiscarded() {
        NoonSearchProviderException invalid = NoonSearchHttpFailureMapper.map(
                429,
                "https://www.noon.com/search",
                "secret-value"
        );
        NoonSearchProviderException contract = NoonSearchHttpFailureMapper.map(
                400,
                "https://www.noon.com/search",
                "60"
        );
        NoonSearchProviderException legacy = NoonSearchHttpFailureMapper.map(
                429,
                "https://www.noon.com/search"
        );

        assertNull(invalid.getRetryAfter());
        assertNull(contract.getRetryAfter());
        assertNull(legacy.getRetryAfter());
    }
}
