package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompetitorDetailRetryPolicyTest {
    private final CompetitorDetailRetryPolicy policy = new CompetitorDetailRetryPolicy();

    @Test
    void ordinaryFailuresUseTwoFourEightSixteenMinuteBackoffThenStop() {
        LocalDateTime failedAt = LocalDateTime.parse("2026-07-28T02:00:00");
        CompetitorDetailRetryPayload payload = legacyPayload();

        for (int attempt = 1; attempt <= 4; attempt++) {
            long runId = 220100L + attempt;
            Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                    payload,
                    runId,
                    targets(),
                    "DETAIL_REFRESH_FAILED",
                    "Noon detail failed",
                    failedAt,
                    failedAt.plusHours(1)
            );

            assertTrue(planned.isPresent());
            payload = planned.orElseThrow();
            assertEquals(attempt, payload.getRetryAttempt());
            assertEquals(failedAt.plus(policy.backoffForAttempt(attempt)), payload.getRetryNotBefore());
            assertEquals(220101L, payload.getRootRunId());
            assertEquals(runId, payload.getRetryOfRunId());
            assertEquals("DETAIL_REFRESH_FAILED", payload.getLastErrorCode());
            assertEquals("Noon detail failed", payload.getMessage());
        }

        assertFalse(policy.planNextRetry(
                payload,
                220105L,
                targets(),
                "DETAIL_REFRESH_FAILED",
                "still failing",
                failedAt,
                null
        ).isPresent());
    }

    @Test
    void riskFailureUsesLaterSharedHoldAndRecognizesEverySupportedRiskCode() {
        LocalDateTime failedAt = LocalDateTime.parse("2026-07-28T02:00:00");
        LocalDateTime sharedHoldUntil = failedAt.plusMinutes(11);

        for (String errorCode : List.of(
                "RATE_LIMITED",
                "blocked_by_risk_control",
                "Captcha_Required"
        )) {
            CompetitorDetailRetryPayload planned = policy.planNextRetry(
                    legacyPayload(),
                    220100L,
                    targets(),
                    errorCode,
                    "risk",
                    failedAt,
                    sharedHoldUntil
            ).orElseThrow();

            assertTrue(policy.isRiskFailure(errorCode));
            assertEquals(sharedHoldUntil, planned.getRetryNotBefore());
        }
    }

    @Test
    void sharedHoldEarlierThanCalculatedRiskBackoffDoesNotShortenDelay() {
        LocalDateTime failedAt = LocalDateTime.parse("2026-07-28T02:00:00");
        CompetitorDetailRetryPayload current = legacyPayload();
        current.setRetryAttempt(3);

        CompetitorDetailRetryPayload planned = policy.planNextRetry(
                current,
                220104L,
                targets(),
                "RATE_LIMITED",
                "429",
                failedAt,
                failedAt.plusMinutes(3)
        ).orElseThrow();

        assertEquals(failedAt.plusMinutes(16), planned.getRetryNotBefore());
    }

    @Test
    void invalidNoonProductCodeNeverEntersAutomaticRetry() {
        Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                legacyPayload(),
                220100L,
                targets(),
                " invalid_noon_product_code ",
                "invalid code",
                LocalDateTime.parse("2026-07-28T02:00:00"),
                null
        );

        assertFalse(planned.isPresent());
    }

    @Test
    void staleDetailTargetNeverEntersAutomaticRetry() {
        Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                legacyPayload(),
                220100L,
                targets(),
                " detail_target_stale ",
                "target no longer belongs to current watch scope",
                LocalDateTime.parse("2026-07-28T02:00:00"),
                null
        );

        assertFalse(policy.isRetryable("DETAIL_TARGET_STALE"));
        assertFalse(planned.isPresent());
    }

    @Test
    void publicDetailNotFoundUsesLowFrequencyBackoffThenStops() {
        LocalDateTime failedAt = LocalDateTime.parse("2026-07-28T02:00:00");
        List<Duration> expectedBackoffs = List.of(
                Duration.ofMinutes(30),
                Duration.ofHours(6),
                Duration.ofHours(24),
                Duration.ofHours(24)
        );
        CompetitorDetailRetryPayload payload = legacyPayload();

        for (int attempt = 1; attempt <= expectedBackoffs.size(); attempt++) {
            Optional<CompetitorDetailRetryPayload> planned = policy.planNextRetry(
                    payload,
                    220100L + attempt,
                    targets(),
                    "PUBLIC_DETAIL_NOT_FOUND",
                    "not found",
                    failedAt,
                    null
            );

            assertTrue(planned.isPresent());
            payload = planned.orElseThrow();
            assertEquals(
                    failedAt.plus(expectedBackoffs.get(attempt - 1)),
                    payload.getRetryNotBefore()
            );
            assertEquals(
                    expectedBackoffs.get(attempt - 1),
                    policy.backoffForFailure("public_detail_not_found", attempt)
            );
        }

        assertFalse(policy.planNextRetry(
                payload,
                220105L,
                targets(),
                "PUBLIC_DETAIL_NOT_FOUND",
                "still not found",
                failedAt,
                null
        ).isPresent());
    }

    @Test
    void payloadMayLowerButNeverRaiseTheFourRetryLimit() {
        CompetitorDetailRetryPayload twoRetries = legacyPayload();
        twoRetries.setMaxRetryAttempts(2);
        twoRetries.setRetryAttempt(2);
        assertFalse(policy.planNextRetry(
                twoRetries,
                220103L,
                targets(),
                "DETAIL_REFRESH_FAILED",
                "failed",
                LocalDateTime.parse("2026-07-28T02:00:00"),
                null
        ).isPresent());

        CompetitorDetailRetryPayload capped = legacyPayload();
        capped.setMaxRetryAttempts(99);
        assertEquals(4, capped.getMaxRetryAttempts());
        assertEquals(Duration.ofMinutes(16), policy.backoffForAttempt(4));
    }

    private CompetitorDetailRetryPayload legacyPayload() {
        return CompetitorDetailRetryPayload.fromJson(
                "{\"watchProductId\":180123,\"executionMode\":\"scheduledDetail\"}"
        );
    }

    private List<CompetitorProductDetailTarget> targets() {
        return CompetitorDetailRetryPayload.fromJson(
                "{\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\""
                        + "}]}"
        ).getFailedDetailTargets();
    }
}
