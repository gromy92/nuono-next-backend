package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NoonRiskBackoffGuardTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-05-22T09:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void publicRiskCreatesPublicAccountWideHoldWithoutBlockingPartner() {
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, CLOCK);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.publicDetail(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "PUBLIC_DETAIL",
                130001L,
                null,
                "public detail rate limited"
        );

        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.publicDetail(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.publicSearch(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertTrue(repository.selectLatestHold(
                NoonRiskBackoffScope.allPublicNoon(307L, "STR108065-NSA", "SA").getScopeKey()
        ) != null);
    }

    @Test
    void partnerRiskCreatesPartnerAccountWideHold() {
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, CLOCK);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "sales report blocked"
        );

        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertTrue(repository.selectLatestHold(
                NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA").getScopeKey()
        ) != null);
    }

    @Test
    void differentReportDomainsHaveDifferentExactKeys() {
        NoonReportPullRequest sales = report(NoonPullDataDomain.SALES);
        NoonReportPullRequest order = report(NoonPullDataDomain.ORDER);

        assertFalse(NoonRiskBackoffScope.report(sales).getScopeKey()
                .equals(NoonRiskBackoffScope.report(order).getScopeKey()));
        assertTrue(NoonRiskBackoffScope.report(sales).getScopeKey().contains("REPORT_SALES"));
        assertTrue(NoonRiskBackoffScope.report(order).getScopeKey().contains("REPORT_ORDER"));
    }

    @Test
    void explicitAccountWideSignalStillBlocksMatchingChannel() {
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, CLOCK);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "provider declared account-wide risk"
        );

        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.publicDetail(307L, "STR108065-NSA", "SA")
        ).isPresent());
    }

    @Test
    void successResetsTheExactAndMatchingAccountWideHold() {
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, CLOCK);
        NoonRiskBackoffScope exact = NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA");
        NoonRiskBackoffScope broad = NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA");

        guard.recordRiskSignal(exact, "rate_limited", "SALES", 130001L, null, "exact");
        guard.recordSuccess(exact, "SALES");

        assertNull(repository.selectActiveHold(exact.getScopeKey(), LocalDateTime.of(2026, 5, 22, 9, 1)));
        assertNull(repository.selectActiveHold(
                broad.getScopeKey(),
                LocalDateTime.of(2026, 5, 22, 9, 1)
        ));
    }

    private NoonReportPullRequest report(NoonPullDataDomain domain) {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(domain)
                .reportType(domain.name())
                .build();
    }
}
