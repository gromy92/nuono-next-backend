package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NoonSalesOrderCompletenessAuditTest {

    @Test
    void orderFactsPresentExposeReadyCompleteness() {
        NoonSalesOrderCompletenessAudit audit = NoonSalesOrderCompletenessAudit.factsPresent(
                LocalDate.parse("2026-05-23"),
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-23"),
                18
        );

        assertEquals(NoonDataLatestStatus.READY, audit.latestStatus());
        assertEquals(NoonDataHistoryStatus.COMPLETE, audit.historyStatus());
        assertEquals(18, audit.getOrderLineCount());
    }

    @Test
    void unavailableOrderDomainIsNotIntegratedNotEmptyOrders() {
        NoonSalesOrderCompletenessAudit audit = NoonSalesOrderCompletenessAudit.notIntegrated("provider_not_configured");

        assertFalse(audit.isIntegrated());
        assertEquals(NoonDataLatestStatus.NOT_INTEGRATED, audit.latestStatus());
        assertEquals(NoonDataHistoryStatus.NOT_INTEGRATED, audit.historyStatus());
        assertEquals("provider_not_configured", audit.getFailureType());
    }
}
