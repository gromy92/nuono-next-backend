package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitorDetailRetryRequestLedgerTest {
    private static final LocalDateTime NOW =
            LocalDateTime.parse("2026-07-28T02:00:00");
    private static final CompetitorDetailRetryPolicy POLICY =
            new CompetitorDetailRetryPolicy();

    @Test
    void durableReservationSurvivesJsonAndCrashConsumesTheAttempt() {
        CompetitorProductDetailTarget target =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorDetailRetryPayload payload = payload(target, 0);

        CompetitorDetailRetryPayload reserved =
                CompetitorDetailRetryRequestLedger.begin(
                        payload, target, 220123L
                );
        CompetitorDetailRetryPayload restored =
                CompetitorDetailRetryPayload.fromJson(reserved.toJson());

        assertEquals(1, restored.getDetailRequestAttemptCount());
        assertTrue(restored.state(target).isRequestInFlight());
        assertEquals(0, restored.state(target).getRetryAttempt());

        CompetitorDetailRetryPayload recovered =
                CompetitorDetailRetryRequestLedger.recoverInFlight(
                        restored, NOW, POLICY
                );

        assertEquals(1, recovered.getDetailRequestAttemptCount());
        assertEquals(1, recovered.state(target).getRetryAttempt());
        assertEquals(NOW.plusMinutes(2), recovered.state(target).getRetryNotBefore());
        assertFalse(recovered.state(target).isRequestInFlight());
        assertEquals(
                CompetitorDetailRetryRequestLedger.UNKNOWN_OUTCOME,
                recovered.getLastErrorCode()
        );
    }

    @Test
    void returnedFailureDoesNotDoubleCountReservedRequest() {
        CompetitorProductDetailTarget target =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorDetailRetryPayload reserved =
                CompetitorDetailRetryRequestLedger.begin(
                        payload(target, 0), target, 220123L
                );

        CompetitorDetailRetryPayload failed =
                CompetitorDetailRetryRequestLedger.failure(
                        reserved,
                        target,
                        "DETAIL_REFRESH_FAILED",
                        "timeout",
                        true,
                        220123L,
                        NOW,
                        POLICY
                );

        assertEquals(1, failed.getDetailRequestAttemptCount());
        assertEquals(1, failed.state(target).getRetryAttempt());
        assertEquals(NOW.plusMinutes(2), failed.state(target).getRetryNotBefore());
        assertFalse(failed.state(target).isRequestInFlight());
    }

    @Test
    void crashDuringLastRetryBecomesTerminalInsteadOfIssuingExtraRequest() {
        CompetitorProductDetailTarget target =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorDetailRetryPayload reserved =
                CompetitorDetailRetryRequestLedger.begin(
                        payload(target, 4), target, 220127L
                );

        CompetitorDetailRetryPayload recovered =
                CompetitorDetailRetryRequestLedger.recoverInFlight(
                        CompetitorDetailRetryPayload.fromJson(
                                reserved.toJson()
                        ),
                        NOW,
                        POLICY
                );

        assertTrue(recovered.getRetryStates().isEmpty());
        assertEquals(1, recovered.getDetailRequestAttemptCount());
        assertEquals(1, recovered.getDetailTerminalFailedCount());
        assertEquals(
                CompetitorDetailRetryRequestLedger.UNKNOWN_OUTCOME,
                recovered.getDetailTerminalErrorCode()
        );
    }

    @Test
    void rankCoverageDeferralDoesNotConsumeAProviderAttempt() {
        CompetitorProductDetailTarget target =
                CompetitorProductDetailTarget.self("ZSELF001");

        CompetitorDetailRetryPayload deferred =
                CompetitorDetailRetryRequestLedger.defer(
                        payload(target, 4),
                        target,
                        "RANK_COVERAGE_INCOMPLETE",
                        "waiting for rank",
                        220127L,
                        NOW,
                        POLICY
                );

        assertEquals(0, deferred.getDetailRequestAttemptCount());
        assertEquals(4, deferred.state(target).getRetryAttempt());
        assertEquals(
                NOW.plusMinutes(16),
                deferred.state(target).getRetryNotBefore()
        );
        assertFalse(deferred.state(target).isRequestInFlight());
    }

    private static CompetitorDetailRetryPayload payload(
            CompetitorProductDetailTarget target,
            int retryAttempt
    ) {
        CompetitorDetailRetryPayload payload =
                CompetitorDetailRetryPayload.empty();
        payload.setRetryStates(List.of(new CompetitorDetailRetryState(
                target,
                retryAttempt,
                NOW,
                null,
                null
        )));
        payload.setDetailTargetTotal(1);
        return payload;
    }
}
