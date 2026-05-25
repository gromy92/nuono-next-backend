package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonpull.NoonSalesRetentionCapability;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonHistoricalBackfillPolicyTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    private NoonHistoricalBackfillPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new NoonHistoricalBackfillPolicy(FIXED_CLOCK);
    }

    @Test
    void defaultsSalesOrderAndProductViewsHistoryToOneMonthCorrectionTargetWhenRetentionAllows() {
        NoonHistoricalBackfillPolicy.SkuBaselineEvidence baseline = NoonHistoricalBackfillPolicy.SkuBaselineEvidence.of(
                12,
                10,
                9,
                LocalDate.parse("2026-05-24")
        );

        NoonHistoricalBackfillPolicy.Target orderTarget = policy.defaultTarget(
                NoonDataCategory.SALES_ORDER,
                LocalDate.parse("2026-05-25"),
                NoonSalesRetentionCapability.configured(365),
                baseline
        );
        NoonHistoricalBackfillPolicy.Target salesTarget = policy.defaultTarget(
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.parse("2026-05-25"),
                NoonSalesRetentionCapability.configured(365),
                baseline
        );

        assertEquals(LocalDate.parse("2026-04-25"), orderTarget.getRequestedFrom());
        assertEquals(LocalDate.parse("2026-05-24"), orderTarget.getRequestedTo());
        assertEquals(LocalDate.parse("2026-04-25"), orderTarget.getEffectiveFrom());
        assertEquals(LocalDate.parse("2026-05-24"), orderTarget.getEffectiveTo());
        assertFalse(orderTarget.isRetentionLimited());
        assertEquals(orderTarget.getRequestedFrom(), salesTarget.getRequestedFrom());
        assertEquals(orderTarget.getRequestedTo(), salesTarget.getRequestedTo());
    }

    @Test
    void splitsHistoricalTargetIntoRetryableGapWindowsWithSkuBaselineEvidence() {
        NoonHistoricalBackfillPolicy.Target target = policy.defaultTarget(
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.parse("2026-05-25"),
                NoonSalesRetentionCapability.configured(365),
                NoonHistoricalBackfillPolicy.SkuBaselineEvidence.of(12, 10, 9, LocalDate.parse("2026-05-24"))
        );

        List<NoonDataGapWindowRecord> gaps = policy.splitIntoGapWindows(row(NoonDataCategory.SALES_PRODUCT_VIEWS), target, 31);

        assertEquals(1, gaps.size());
        assertEquals(LocalDate.parse("2026-04-25"), gaps.get(0).getDateFrom());
        assertEquals(LocalDate.parse("2026-05-24"), gaps.get(0).getDateTo());
        assertEquals(NoonDataGapStatus.PENDING, gaps.get(0).getStatus());
        assertEquals(Boolean.TRUE, gaps.get(0).getRetryable());
        assertTrue(gaps.get(0).getDiagnosticSummary().contains("products=12"));
        assertTrue(gaps.get(0).getDiagnosticSummary().contains("offers=10"));
        assertTrue(gaps.get(0).getDiagnosticSummary().contains("inventorySkus=9"));
        assertEquals(LocalDate.parse("2026-05-24"), gaps.get(0).getDateTo());
    }

    @Test
    void confirmedEarliestEmptyFullMonthMarksHistoryCompleteAndStopsHistoryPatrolOnly() {
        NoonDataCompletenessRecord row = row(NoonDataCategory.SALES_PRODUCT_VIEWS);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setHistoryCoveredFrom(LocalDate.parse("2026-01-01"));
        row.setHistoryCoveredTo(LocalDate.parse("2026-05-24"));
        row.setPatrolEnabled(true);
        row.setActiveGapCount(2);

        NoonDataCompletenessRecord updated = policy.applyEarliestEmptyMonthEvidence(
                row,
                NoonHistoricalBackfillPolicy.EmptyMonthEvidence.confirmed(
                        LocalDate.parse("2025-12-01"),
                        12,
                        10,
                        9,
                        0,
                        0
                )
        );

        assertEquals(NoonDataHistoryStatus.COMPLETE, updated.getHistoryStatus());
        assertEquals(LocalDate.parse("2025-12-01"), updated.getHistoryCoveredFrom());
        assertEquals(LocalDate.parse("2026-05-24"), updated.getHistoryCoveredTo());
        assertTrue(updated.isPatrolEnabled());
        assertEquals(1, updated.getActiveGapCount());
        assertTrue(updated.getDiagnosticSummary().contains("earliest empty month 2025-12"));
    }

    @Test
    void emptyHistoryWithConfirmedEarliestEmptyMonthBecomesConfirmedEmpty() {
        NoonDataCompletenessRecord row = row(NoonDataCategory.SALES_ORDER);
        row.setLatestStatus(NoonDataLatestStatus.READY);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);

        NoonDataCompletenessRecord updated = policy.applyEarliestEmptyMonthEvidence(
                row,
                NoonHistoricalBackfillPolicy.EmptyMonthEvidence.confirmed(
                        LocalDate.parse("2025-12-01"),
                        12,
                        10,
                        9,
                        0,
                        0
                )
        );

        assertEquals(NoonDataHistoryStatus.CONFIRMED_EMPTY, updated.getHistoryStatus());
        assertFalse(updated.isPatrolEnabled());
        assertEquals(0, updated.getActiveGapCount());
    }

    @Test
    void incompleteMonthEvidenceKeepsHistoryIncompleteAndPatrolEnabled() {
        NoonDataCompletenessRecord row = row(NoonDataCategory.SALES_PRODUCT_VIEWS);
        row.setLatestStatus(NoonDataLatestStatus.READY);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);

        NoonDataCompletenessRecord updated = policy.applyEarliestEmptyMonthEvidence(
                row,
                NoonHistoricalBackfillPolicy.EmptyMonthEvidence.observed(
                        LocalDate.parse("2025-12-01"),
                        12,
                        10,
                        9,
                        1,
                        0
                )
        );

        assertEquals(NoonDataHistoryStatus.INCOMPLETE, updated.getHistoryStatus());
        assertTrue(updated.isPatrolEnabled());
        assertEquals(1, updated.getActiveGapCount());
    }

    @Test
    void providerRetentionStillClampsCorrectionTargetWhenNarrowerThanOneMonth() {
        NoonHistoricalBackfillPolicy.Target target = policy.defaultTarget(
                NoonDataCategory.SALES_ORDER,
                LocalDate.parse("2026-05-25"),
                NoonSalesRetentionCapability.configured(20),
                NoonHistoricalBackfillPolicy.SkuBaselineEvidence.of(12, 10, 9, LocalDate.parse("2026-05-24"))
        );

        List<NoonDataGapWindowRecord> gaps = policy.splitIntoGapWindows(row(NoonDataCategory.SALES_ORDER), target, 31);

        assertTrue(target.isRetentionLimited());
        assertEquals(LocalDate.parse("2026-05-05"), target.getEffectiveFrom());
        assertEquals(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT, gaps.get(0).getStatus());
        assertEquals("provider_retention_limit", gaps.get(0).getFailureType());
        assertEquals(Boolean.FALSE, gaps.get(0).getRetryable());
        assertEquals(LocalDate.parse("2026-04-25"), gaps.get(0).getDateFrom());
        assertEquals(LocalDate.parse("2026-05-04"), gaps.get(0).getDateTo());
        assertEquals(NoonDataGapStatus.PENDING, gaps.get(1).getStatus());
        assertEquals(Boolean.TRUE, gaps.get(1).getRetryable());
    }

    private static NoonDataCompletenessRecord row(NoonDataCategory category) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setId(900001L);
        row.setOwnerUserId(307L);
        row.setStoreCode("STR108065-NAE");
        row.setSiteCode("AE");
        row.setCategory(category);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);
        return row;
    }
}
