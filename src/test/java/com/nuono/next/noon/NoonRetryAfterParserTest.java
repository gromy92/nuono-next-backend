package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NoonRetryAfterParserTest {

    @Test
    void parsesDeltaSecondsAndRfc1123WithoutRetainingTheRawValue() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");

        assertEquals(Duration.ofSeconds(73), NoonRetryAfterParser.parse(" 73 ", now));
        assertEquals(
                Duration.ofMinutes(2),
                NoonRetryAfterParser.parse("Sun, 2 Aug 2026 10:02:00 GMT", now)
        );
        assertEquals(
                Duration.ZERO,
                NoonRetryAfterParser.parse("Sun, 2 Aug 2026 09:59:00 GMT", now)
        );
    }

    @Test
    void invalidValuesFallBackToNull() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");

        assertNull(NoonRetryAfterParser.parse(null, now));
        assertNull(NoonRetryAfterParser.parse("-1", now));
        assertNull(NoonRetryAfterParser.parse("1.5", now));
        assertNull(NoonRetryAfterParser.parse("secret-token", now));
        assertNull(NoonRetryAfterParser.parse("999999999999999999999999", now));
    }

    @Test
    void oldHttpExceptionConstructorRemainsCompatible() {
        NoonHttpException legacy = new NoonHttpException(503, "unavailable", "/report");
        NoonHttpException hinted = new NoonHttpException(
                503,
                "unavailable",
                "/report",
                Duration.ofSeconds(19)
        );

        assertNull(legacy.getRetryAfter());
        assertEquals(Duration.ofSeconds(19), hinted.getRetryAfter());
    }
}
