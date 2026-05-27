package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonBusinessSyncStatusServiceTest {

    private static final NoonSyncScope SCOPE = NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA");

    @Test
    void legacyImportedAccountWithoutSalesFactsRequiresBackfillAndBlocksBusinessMetrics() {
        NoonBusinessSyncStatusService service = service(new NoonSyncFoundationService());

        NoonSalesSyncReadModel status = service.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));

        assertEquals(NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED, status.getState());
        assertFalse(status.isDataSufficient());
        assertFalse(status.isBusinessMetricsAllowed());
        assertEquals("sales_backfill", status.getRequiredWork().get(0).getPlanKey());
    }

    @Test
    void runningAndFailedBackfillAreSharedSyncStatesForSalesSurfaces() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonBusinessSyncStatusService service = service(foundation);
        foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.GAP_BACKFILL)));

        NoonSalesSyncReadModel running = service.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));
        NoonSyncFoundationService failedFoundation = new NoonSyncFoundationService();
        NoonBusinessSyncStatusService failedService = service(failedFoundation);
        failedFoundation.markFailed(
                failedFoundation.markRunning(failedFoundation.createTask(salesTask(NoonSyncTriggerMode.GAP_BACKFILL))).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "provider unavailable")
        );

        NoonSalesSyncReadModel failed = failedService.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));

        assertEquals(NoonSalesSyncSurfaceState.BACKFILL_RUNNING, running.getState());
        assertEquals(NoonSalesSyncSurfaceState.BACKFILL_FAILED, failed.getState());
        assertEquals(NoonSyncFailureReason.PROVIDER_UNAVAILABLE, failed.getFailureReason());
    }

    @Test
    void emptyReportAndMappingFailuresDoNotBecomeConfirmedZeroSales() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonBusinessSyncStatusService service = service(foundation);
        foundation.markPartial(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY))).getId(),
                NoonSyncFailureReason.EMPTY_REPORT,
                NoonSyncRetryPolicy.NON_RETRYABLE,
                NoonSyncDiagnostic.safe(null, "empty report")
        );

        NoonSalesSyncReadModel empty = service.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));
        foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY))).getId(),
                NoonSyncFailureReason.MISSING_COLUMNS,
                NoonSyncRetryPolicy.NON_RETRYABLE,
                NoonSyncDiagnostic.safe(null, "missing columns")
        );

        NoonSalesSyncReadModel missingMapping = service.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));

        assertEquals(NoonSalesSyncSurfaceState.EMPTY_REPORT, empty.getState());
        assertFalse(empty.isBusinessMetricsAllowed());
        assertEquals(NoonSalesSyncSurfaceState.MISSING_MAPPING, missingMapping.getState());
        assertFalse(missingMapping.isBusinessMetricsAllowed());
    }

    @Test
    void staleLatestSalesAndCorrectionFailuresAreVisibleSeparately() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonBusinessSyncStatusService service = service(foundation);
        foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION))).getId(),
                NoonSyncFailureReason.TIMEOUT,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "timeout")
        );

        NoonSalesSyncReadModel status = service.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                SCOPE,
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 19),
                List.of(),
                true
        ));

        assertEquals(NoonSalesSyncSurfaceState.STALE_LATEST_SALES, status.getState());
        assertEquals(LocalDate.of(2026, 5, 10), status.getLatestAvailableSalesDate());
        assertEquals(NoonSalesCorrectionSurfaceState.CORRECTION_FAILED, status.getCorrectionState());
        assertTrue(status.getRequiredWork().stream()
                .anyMatch(work -> "sales_low_frequency_correction".equals(work.getPlanKey())));
    }

    @Test
    void dashboardSignalsConsumeSharedSyncStatusInsteadOfRawTaskInterpretation() {
        NoonDashboardSyncSignalProvider provider = new NoonDashboardSyncSignalProvider(service(new NoonSyncFoundationService()));

        NoonDashboardSyncSignal signal = provider.salesSignal(new NoonSalesSurfaceSyncInput(
                SCOPE,
                null,
                LocalDate.of(2026, 5, 19),
                List.of(),
                false
        ));

        assertEquals(NoonDashboardSyncSignalState.DATA_INSUFFICIENT, signal.getState());
        assertEquals(NoonSalesSyncSurfaceState.NO_DATA_BACKFILL_REQUIRED, signal.getSalesSyncStatus().getState());
    }

    private NoonBusinessSyncStatusService service(NoonSyncFoundationService foundation) {
        return new NoonBusinessSyncStatusService(
                foundation,
                Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneId.of("UTC"))
        );
    }

    private NoonSyncTaskRequest salesTask(NoonSyncTriggerMode triggerMode) {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                triggerMode,
                SCOPE,
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 19)),
                NoonSyncRetryPolicy.RETRYABLE
        );
    }
}
