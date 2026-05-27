package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoonSyncAdminDiagnosticsServiceTest {

    @Test
    void diagnosticsRequireSystemAdminAndCanFilterTaskSummaries() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonSyncAdminDiagnosticsService service = new NoonSyncAdminDiagnosticsService(foundation);
        foundation.createPlan("product_initialization", scope("STR245027-SAU"));
        foundation.createPlan("sales_low_frequency_correction", scope("STR245027-SAU"));
        NoonSyncTask failedSales = foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION))).getId(),
                NoonSyncFailureReason.TIMEOUT,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe("trace-timeout", "Noon report timeout")
        );
        foundation.markSucceeded(foundation.markRunning(foundation.createTask(productTask())).getId());

        NoonSyncAdminDiagnosticsView view = service.overview(
                adminContext(),
                new NoonSyncAdminDiagnosticFilter(
                        NoonSyncDataDomain.SALES,
                        NoonSyncTaskStatus.FAILED,
                        NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 5, 31)
                )
        );

        assertEquals(1, view.getTaskSummaries().size());
        assertEquals(failedSales.getId(), view.getTaskSummaries().get(0).getTaskId());
        assertEquals(NoonSyncFailureReason.TIMEOUT, view.getTaskSummaries().get(0).getFailureReason());
        assertEquals(2, view.getPlanSummaries().size());
        assertTrue(view.getHealth().getRetryableFailureCount() >= 1);
        assertThrows(IllegalStateException.class, () -> service.overview(operatorContext(), NoonSyncAdminDiagnosticFilter.empty()));
    }

    @Test
    void diagnosticSummariesRedactLoginMaterialsAndUnsafeProviderPayloads() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonSyncAdminDiagnosticsService service = new NoonSyncAdminDiagnosticsService(foundation);
        NoonSyncTask task = foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY))).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(
                        "trace-provider",
                        "cookie=secret; api_key=abc123; password=hunter2; Authorization: Bearer token; raw={unsafe}"
                )
        );

        NoonSyncAdminTaskSummary detail = service.taskDetail(adminContext(), task.getId(), 10002L);

        assertFalse(detail.getDiagnosticSummary().contains("secret"));
        assertFalse(detail.getDiagnosticSummary().contains("abc123"));
        assertFalse(detail.getDiagnosticSummary().contains("hunter2"));
        assertFalse(detail.getDiagnosticSummary().contains("Bearer token"));
        assertFalse(detail.getDiagnosticSummary().contains("raw={unsafe}"));
        assertEquals("trace-provider", detail.getProviderTraceId());
    }

    @Test
    void adminCanPauseResumePlansAndRetryOnlyRetryableFailures() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonSyncAdminDiagnosticsService service = new NoonSyncAdminDiagnosticsService(foundation);
        NoonSyncPlan plan = foundation.createPlan("sales_daily_sync", scope("STR245027-SAU"));
        NoonSyncTask retryable = foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY))).getId(),
                NoonSyncFailureReason.PROVIDER_UNAVAILABLE,
                NoonSyncRetryPolicy.RETRYABLE,
                NoonSyncDiagnostic.safe(null, "provider unavailable")
        );
        NoonSyncTask nonRetryable = foundation.markFailed(
                foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.GAP_BACKFILL))).getId(),
                NoonSyncFailureReason.MISSING_COLUMNS,
                NoonSyncRetryPolicy.NON_RETRYABLE,
                NoonSyncDiagnostic.safe(null, "missing columns")
        );

        NoonSyncPlan paused = service.pausePlan(adminContext(), plan.getId(), "maintenance");
        NoonSyncPlan resumed = service.resumePlan(adminContext(), plan.getId());
        NoonSyncTask retry = service.retryTask(adminContext(), retryable.getId());

        assertTrue(paused.isPaused());
        assertFalse(resumed.isPaused());
        assertEquals(NoonSyncTaskStatus.QUEUED, retry.getStatus());
        assertEquals(retryable.getTriggerMode(), retry.getTriggerMode());
        assertThrows(IllegalStateException.class, () -> service.retryTask(adminContext(), nonRetryable.getId()));
    }

    @Test
    void crossOwnerTaskDetailRequiresExplicitMatchingOwner() {
        NoonSyncFoundationService foundation = new NoonSyncFoundationService();
        NoonSyncAdminDiagnosticsService service = new NoonSyncAdminDiagnosticsService(foundation);
        NoonSyncTask task = foundation.markRunning(foundation.createTask(salesTask(NoonSyncTriggerMode.SCHEDULED_DAILY)));

        assertThrows(IllegalStateException.class, () -> service.taskDetail(adminContext(), task.getId(), 99999L));
        assertEquals(task.getId(), service.taskDetail(adminContext(), task.getId(), 10002L).getTaskId());
    }

    private NoonSyncTaskRequest salesTask(NoonSyncTriggerMode triggerMode) {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.SALES,
                triggerMode,
                scope("STR245027-SAU"),
                NoonSyncTarget.dateRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 19)),
                NoonSyncRetryPolicy.RETRYABLE
        );
    }

    private NoonSyncTaskRequest productTask() {
        return new NoonSyncTaskRequest(
                NoonSyncDataDomain.PRODUCT,
                NoonSyncTriggerMode.ONBOARDING,
                scope("STR245027-SAU"),
                NoonSyncTarget.identity("STR245027-SAU"),
                NoonSyncRetryPolicy.RETRYABLE
        );
    }

    private NoonSyncScope scope(String storeCode) {
        return NoonSyncScope.of(10002L, 245027L, storeCode, "SA");
    }

    private BusinessAccessContext adminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10001L)
                .businessOwnerUserId(10001L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleName("系统管理员")
                .roleLevel(0)
                .menuPaths(Set.of("/system/noon-sync-diagnostics"))
                .build();
    }

    private BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("运营")
                .roleLevel(3)
                .storeCodes(Set.of("STR245027-SAU"))
                .build();
    }
}
