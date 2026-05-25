package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NoonSalesProductViewsCompletenessAuditTest {

    @Test
    void factsPresentExposeLatestAndHistoryCoverage() {
        NoonSalesProductViewsCompletenessAudit audit = NoonSalesProductViewsCompletenessAudit.factsPresent(
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-24"),
                42
        );

        assertEquals(NoonDataLatestStatus.READY, audit.latestStatus());
        assertEquals(NoonDataHistoryStatus.COMPLETE, audit.historyStatus());
        assertEquals(LocalDate.parse("2026-05-24"), audit.getLatestFactDate());
        assertEquals(42, audit.getFactRowCount());
    }

    @Test
    void missingFactsStayIncomplete() {
        NoonSalesProductViewsCompletenessAudit audit = NoonSalesProductViewsCompletenessAudit.missing();

        assertEquals(NoonDataLatestStatus.INCOMPLETE, audit.latestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, audit.historyStatus());
    }

    @Test
    void recentCompletedEmptyReportStaysPendingConfirmation() {
        NoonSalesProductViewsCompletenessAudit audit = NoonSalesProductViewsCompletenessAudit.pendingEmptyConfirmation(
                LocalDate.parse("2026-05-24"),
                "empty_report_pending_confirmation"
        );

        assertEquals(NoonDataLatestStatus.PENDING_CONFIRMATION, audit.latestStatus());
        assertTrue(audit.isPendingConfirmation());
        assertEquals("empty_report_pending_confirmation", audit.getFailureType());
    }

    @Test
    void failedAuditKeepsFailureTypeForGapReduction() {
        NoonSalesProductViewsCompletenessAudit audit = NoonSalesProductViewsCompletenessAudit.failed("missing_columns");

        assertEquals(NoonDataLatestStatus.FAILED, audit.latestStatus());
        assertEquals(NoonDataHistoryStatus.FAILED, audit.historyStatus());
        assertEquals("missing_columns", audit.getFailureType());
    }
}
