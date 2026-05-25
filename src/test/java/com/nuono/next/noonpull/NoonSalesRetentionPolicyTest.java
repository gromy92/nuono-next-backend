package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonSalesRetentionPolicyTest {

    private NoonSalesRetentionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new NoonSalesRetentionPolicy(Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldDefaultToConservativeNearNinetyDayRetention() {
        NoonSalesRetentionCapability capability = NoonSalesRetentionCapability.defaultCapability();

        assertEquals(90, capability.getRetentionDays());
        assertEquals(LocalDate.of(2026, 2, 21), policy.retainedFrom(capability));
    }

    @Test
    void shouldUseConfiguredOrProviderCapabilityRetentionHorizon() {
        NoonSalesRetentionCapability configured = NoonSalesRetentionCapability.configured(120);
        NoonSalesRetentionCapability smokeDiscovered = NoonSalesRetentionCapability.providerDiscovered(60);

        assertEquals(LocalDate.of(2026, 1, 22), policy.retainedFrom(configured));
        assertEquals(LocalDate.of(2026, 3, 23), policy.retainedFrom(smokeDiscovered));
    }

    @Test
    void shouldClampOnboardingAndGapBackfillToRetentionWindow() {
        NoonSalesBackfillPlan plan = policy.planBackfill(
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2026, 5, 21),
                NoonSalesRetentionCapability.defaultCapability()
        );

        assertEquals(LocalDate.of(2026, 2, 21), plan.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), plan.getDateTo());
        assertEquals("provider_retention_limit", plan.getQualityState());
        assertFalse(plan.isRetryable());
    }

    @Test
    void shouldProduceWeeklyFortyFiveDayCorrectionWithoutRollingOrDeepCorrection() {
        NoonSalesCorrectionPlan correction = policy.weeklyCorrection(LocalDate.of(2026, 5, 21));

        assertEquals("weekly", correction.getFrequency());
        assertEquals(LocalDate.of(2026, 4, 7), correction.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), correction.getDateTo());
        assertEquals(45, correction.getWindowDays());
        assertTrue(policy.defaultScheduledCorrections().contains(correction.getType()));
        assertFalse(policy.defaultScheduledCorrections().contains("rolling_15_21_day"));
        assertFalse(policy.defaultScheduledCorrections().contains("deep_61_180_day"));
    }

    @Test
    void shouldNotDisplayFakeZeroWhenHistoryIsUnavailable() {
        NoonSalesReadinessView view = policy.historyUnavailableView("provider_retention_limit");

        assertEquals("provider_retention_limit", view.getQualityState());
        assertFalse(view.isMetricsAllowed());
        assertNull(view.getSalesAmount());
        assertNull(view.getUnitsSold());
    }

    @Test
    void latestDayNotReadyShouldDelayWithoutEmptyReportOrRollingCorrection() {
        NoonSalesLatestDayPlan plan = policy.latestDayNotReady(LocalDate.of(2026, 5, 21));

        assertEquals("report_not_ready", plan.getQualityState());
        assertTrue(plan.isRetryable());
        assertEquals(List.of(), plan.getCorrectionTypes());
    }
}
