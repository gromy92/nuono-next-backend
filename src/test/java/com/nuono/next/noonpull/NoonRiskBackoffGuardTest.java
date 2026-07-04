package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NoonRiskBackoffGuardTest {

    @Test
    void recordRiskSignalAlsoCreatesAccountWideHoldForOtherNoonOperations() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "blocked by risk control"
        );

        assertEquals(
                "blocked_by_risk_control",
                repository.selectLatestHold(NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA").getScopeKey())
                        .getRiskType()
        );
        assertEquals(
                "blocked_by_risk_control",
                repository.selectLatestHold(NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA").getScopeKey())
                        .getRiskType()
        );
        assertTrue(guard.currentHold(NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA")).isPresent());
    }

    @Test
    void recordRiskSignalReturnsLongerAccountWideHoldWhenAnotherOperationEscalatedTheScope() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "sales blocked"
        );
        NoonRiskBackoffHold productHold = guard.recordRiskSignal(
                NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "PRODUCT",
                130002L,
                null,
                "product rate limited"
        );

        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 4), productHold.getBlockedUntil());
        assertEquals(2, repository.selectLatestHold(NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA").getScopeKey())
                .getAttemptCount());
    }

    @Test
    void accountWideHoldWithSpecificSiteBlocksNoonCallerWhenSiteIsUnknown() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "sales blocked"
        );

        assertTrue(guard.currentHold(NoonRiskBackoffScope.sourceCollection(307L, "STR108065-NSA", null)).isPresent());
    }
}
