package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NoonRiskBackoffGuardTest {

    @Test
    void publicRiskDoesNotBlockPartnerOperations() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.publicDetail(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "PUBLIC_DETAIL",
                130001L,
                null,
                "public detail rate limited"
        );

        assertNotNull(repository.selectLatestHold(
                NoonRiskBackoffScope.allPublicNoon(307L, "STR108065-NSA", "SA").getScopeKey()
        ));
        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.publicSearch(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA")
        ).isPresent());
    }

    @Test
    void partnerRiskDoesNotBlockPublicOperations() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "partner report blocked"
        );

        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.publicDetail(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.publicSearch(307L, "STR108065-NSA", "SA")
        ).isPresent());
        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.sourceCollection(307L, "STR108065-NSA", null)
        ).isPresent());
    }

    @Test
    void legacyPublicAccountWideHoldDoesNotBlockOrEscalatePartnerOperations() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        NoonRiskBackoffHold legacyPublicHold = guard.recordRiskSignal(
                NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "PUBLIC_DETAIL",
                130001L,
                null,
                "legacy public hold stored under NOON"
        );
        legacyPublicHold.setAttemptCount(4);
        legacyPublicHold.setBlockedUntil(LocalDateTime.of(2026, 5, 22, 9, 16));
        repository.upsert(legacyPublicHold);

        assertFalse(guard.currentHold(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA")
        ).isPresent());
        NoonRiskBackoffHold partnerHold = guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "SALES",
                130002L,
                null,
                "partner report rate limited"
        );

        assertEquals(1, partnerHold.getAttemptCount());
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), partnerHold.getBlockedUntil());
    }

    @Test
    void partnerRiskSignalAlsoCreatesAccountWideHoldForOtherPartnerOperations() {
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
    void publicAccountWideHoldWithSpecificSiteBlocksPublicCallerWhenSiteIsUnknown() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.publicSearch(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "PUBLIC_SEARCH",
                130001L,
                null,
                "public search blocked"
        );

        assertTrue(guard.currentHold(NoonRiskBackoffScope.sourceCollection(307L, "STR108065-NSA", null)).isPresent());
    }

    @Test
    void partnerAccountWideHoldWithSpecificSiteBlocksPartnerCallerWhenSiteIsUnknown() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);

        guard.recordRiskSignal(
                NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "partner report blocked"
        );

        assertTrue(guard.currentHold(
                NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", null)
        ).isPresent());
    }

    @Test
    void successfulCallResetsMatchingScopeAndAccountWideBackoffToFirstAttempt() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);
        NoonRiskBackoffScope scope = NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA");

        for (int attempt = 0; attempt < 4; attempt++) {
            guard.recordRiskSignal(scope, "rate_limited", "SALES", 130001L, null, "sales risk");
        }
        assertEquals(4, repository.selectLatestHold(scope.getScopeKey()).getAttemptCount());

        guard.recordSuccess(scope, "SALES");
        assertTrue(guard.currentHold(scope).isEmpty());
        NoonRiskBackoffHold next = guard.recordRiskSignal(
                scope, "rate_limited", "SALES", 130002L, null, "sales risk again"
        );

        assertEquals(1, repository.selectLatestHold(scope.getScopeKey()).getAttemptCount());
        assertEquals(1, repository.selectLatestHold(scope.accountWide().getScopeKey()).getAttemptCount());
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), next.getBlockedUntil());
    }

    @Test
    void successfulCallDoesNotResetAccountWideBackoffOwnedByAnotherDomain() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(repository, clock);
        NoonRiskBackoffScope salesScope = NoonRiskBackoffScope.report(307L, "STR108065-NSA", "SA");
        NoonRiskBackoffScope productScope = NoonRiskBackoffScope.productInterface(307L, "STR108065-NSA", "SA");

        guard.recordRiskSignal(salesScope, "rate_limited", "SALES", 130001L, null, "sales risk");
        guard.recordRiskSignal(productScope, "rate_limited", "PRODUCT", 130002L, null, "product risk");
        guard.recordSuccess(salesScope, "SALES");

        assertEquals(0, repository.selectLatestHold(salesScope.getScopeKey()).getAttemptCount());
        assertEquals(2, repository.selectLatestHold(salesScope.accountWide().getScopeKey()).getAttemptCount());
        assertTrue(guard.currentHold(productScope).isPresent());
    }
}
