package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonSyncFoundationServiceTest {

    @Test
    void catalogIncludesFirstVersionPlanTypesAndCreatesQueuedTaskWithScopedContract() {
        NoonSyncFoundationService service = new NoonSyncFoundationService();

        List<String> planKeys = service.planCatalog()
                .stream()
                .map(NoonSyncPlanDefinition::getKey)
                .collect(Collectors.toList());

        assertTrue(planKeys.containsAll(List.of(
                "product_initialization",
                "product_explicit_refresh",
                "sales_daily_sync",
                "sales_backfill",
                "sales_low_frequency_correction"
        )));

        NoonSyncTask task = service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.SCHEDULED_DAILY,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 19)),
                NoonSyncRetryPolicy.RETRYABLE
        ));

        assertEquals(NoonSyncDataDomain.SALES, task.getDataDomain());
        assertEquals(NoonSyncTriggerMode.SCHEDULED_DAILY, task.getTriggerMode());
        assertEquals(10002L, task.getScope().getOwnerUserId());
        assertEquals("STR245027-SAU", task.getScope().getStoreCode());
        assertEquals(LocalDate.of(2026, 5, 19), task.getTarget().getDateFrom());
        assertEquals(NoonSyncTaskStatus.QUEUED, task.getStatus());
        assertEquals(NoonSyncRetryPolicy.RETRYABLE, task.getRetryPolicy());
    }

    @Test
    void lifecycleTransitionsRecordTypedFailuresAndSafeDiagnostics() {
        NoonSyncFoundationService service = new NoonSyncFoundationService();

        NoonSyncTask succeeded = service.markSucceeded(service.markRunning(salesTask(service)).getId());
        NoonSyncTask partial = service.markPartial(service.markRunning(salesTask(service)).getId(), "2 rows failed mapping");
        NoonSyncTask skipped = service.markSkipped(salesTask(service).getId(), "plan paused");
        NoonSyncTask cancelled = service.markCancelled(salesTask(service).getId(), "manual stop");
        NoonSyncTask failed = service.markFailed(
                service.markRunning(salesTask(service)).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(
                        "trace-20260521",
                        "Noon provider unavailable; cookie=secret; Authorization: Bearer token; raw={sensitive}"
                )
        );

        assertEquals(NoonSyncTaskStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(NoonSyncTaskStatus.PARTIAL, partial.getStatus());
        assertEquals("2 rows failed mapping", partial.getDiagnosticSummary());
        assertEquals(NoonSyncTaskStatus.SKIPPED, skipped.getStatus());
        assertEquals(NoonSyncTaskStatus.CANCELLED, cancelled.getStatus());
        assertEquals(NoonSyncTaskStatus.FAILED, failed.getStatus());
        assertEquals(NoonSyncFailureReason.PROVIDER_UNAVAILABLE, failed.getFailureReason());
        assertEquals("trace-20260521", failed.getProviderTraceId());
        assertTrue(failed.getDiagnosticSummary().contains("[redacted]"));
        assertFalse(failed.getDiagnosticSummary().contains("secret"));
        assertFalse(failed.getDiagnosticSummary().contains("Bearer token"));
        assertFalse(failed.getDiagnosticSummary().contains("raw={sensitive}"));
    }

    @Test
    void activeTaskLockRejectsDuplicateOverlappingScopeAndAllowsAfterTerminalState() {
        NoonSyncFoundationService service = new NoonSyncFoundationService();

        NoonSyncTask active = service.markRunning(service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)),
                NoonSyncRetryPolicy.RETRYABLE
        )));

        assertThrows(NoonSyncDuplicateActiveTaskException.class, () -> service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 7)),
                NoonSyncRetryPolicy.RETRYABLE
        )));

        NoonSyncTask nonOverlapping = service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 12)),
                NoonSyncRetryPolicy.RETRYABLE
        ));

        service.markSucceeded(active.getId());
        NoonSyncTask rerun = service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.GAP_BACKFILL,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 7)),
                NoonSyncRetryPolicy.RETRYABLE
        ));

        assertEquals(NoonSyncTaskStatus.QUEUED, nonOverlapping.getStatus());
        assertEquals(NoonSyncTaskStatus.QUEUED, rerun.getStatus());
    }

    @Test
    void pausedPlanDoesNotCreateScheduledTaskUntilResumed() {
        NoonSyncFoundationService service = new NoonSyncFoundationService();
        NoonSyncPlan plan = service.createPlan(
                "sales_daily_sync",
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA")
        );

        NoonSyncPlan paused = service.pausePlan(plan.getId(), "Noon maintenance window");

        assertTrue(paused.isPaused());
        assertEquals("Noon maintenance window", paused.getPauseReason());
        assertTrue(service.createScheduledTaskFromPlan(
                plan.getId(),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 20))
        ).isEmpty());

        NoonSyncPlan resumed = service.resumePlan(plan.getId());
        NoonSyncTask task = service.createScheduledTaskFromPlan(
                plan.getId(),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 20))
        ).orElseThrow();

        assertFalse(resumed.isPaused());
        assertEquals(NoonSyncDataDomain.SALES, task.getDataDomain());
        assertEquals(NoonSyncTriggerMode.SCHEDULED_DAILY, task.getTriggerMode());
        assertEquals(NoonSyncTaskStatus.QUEUED, task.getStatus());
    }

    @Test
    void failureReadModelDistinguishesRetryableAndNonRetryableFailures() {
        NoonSyncFoundationService service = new NoonSyncFoundationService();

        NoonSyncTask retryable = service.markFailed(
                service.markRunning(salesTask(service)).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe("trace-retry", "temporary timeout")
        );
        NoonSyncTask nonRetryable = service.markFailed(
                service.markRunning(salesTask(service)).getId(),
                NoonSyncFailureReason.AUTHORIZATION_FAILED,
                NoonSyncRetryPolicy.NON_RETRYABLE,
                NoonSyncDiagnostic.safe("trace-auth", "Noon permission denied")
        );

        assertTrue(retryable.isRetryableFailure());
        assertFalse(nonRetryable.isRetryableFailure());
    }

    private NoonSyncTask salesTask(NoonSyncFoundationService service) {
        return service.createTask(new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                NoonSyncTriggerMode.SCHEDULED_DAILY,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 19)),
                NoonSyncRetryPolicy.RETRYABLE
        ));
    }
}
