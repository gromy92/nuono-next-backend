package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonBusinessReadinessServiceTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private Clock clock;
    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
    }

    @Test
    void shouldExposeSharedReadinessVocabularyWithoutFakeZeroMetrics() {
        assertReadiness(null, null, null, "backfill_required", false);
        assertReadiness(NoonPullTaskStatus.QUEUED, NoonPullTriggerMode.SCHEDULED_DAILY, null, "sync_in_progress", false);
        assertReadiness(NoonPullTaskStatus.RUNNING, NoonPullTriggerMode.MANUAL_BACKFILL, null, "backfill_running", false);
        assertReadiness(NoonPullTaskStatus.PARTIAL, NoonPullTriggerMode.GAP_BACKFILL, "partial_success", "partial_success", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.GAP_BACKFILL, "unknown_failure", "backfill_failed", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "empty_report", "empty_report", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "missing_columns", "missing_columns", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "mapping_failed", "mapping_failed", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "provider_unavailable", "provider_unavailable", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "report_not_ready", "report_not_ready", false);
        assertReadiness(NoonPullTaskStatus.FAILED, NoonPullTriggerMode.SCHEDULED_DAILY, "provider_retention_limit", "provider_retention_limit", false);
        assertReadiness(NoonPullTaskStatus.SUCCEEDED, NoonPullTriggerMode.SCHEDULED_DAILY, null, "ready", true);
    }

    @Test
    void shouldMarkOldSuccessfulReportAsStaleInsteadOfAllowingMetrics() {
        NoonPullPlanRecord plan = createPlan(NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY);
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity("sales:2026-05-18")
                .targetDateFrom(LocalDate.of(2026, 5, 18))
                .targetDateTo(LocalDate.of(2026, 5, 18))
                .build()).orElseThrow();
        foundationService.markSucceeded(task.getId(), "batch-old", "old sales");

        NoonBusinessReadinessView view = service().readiness(10002L, "STR245027-NAE", "AE", NoonPullDataDomain.SALES);

        assertEquals("stale", view.getQualityState());
        assertFalse(view.isMetricsAllowed());
        assertEquals("batch-old", view.getSourceBatchId());
    }

    @Test
    void shouldExposeSameReadinessToBusinessConsumers() {
        NoonPullPlanRecord plan = createPlan(NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY);
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity("sales:2026-05-21")
                .targetDateFrom(LocalDate.of(2026, 5, 21))
                .targetDateTo(LocalDate.of(2026, 5, 21))
                .build()).orElseThrow();
        foundationService.markFailed(task.getId(), "provider_unavailable", "provider unavailable");

        NoonBusinessReadinessConsumers consumers = new NoonBusinessReadinessConsumers(service());

        assertEquals("provider_unavailable", consumers.salesAnalysis(10002L, "STR245027-NAE", "AE").getQualityState());
        assertEquals("provider_unavailable", consumers.salesForecast(10002L, "STR245027-NAE", "AE").getQualityState());
        assertEquals("provider_unavailable", consumers.aiDashboardSales(10002L, "STR245027-NAE", "AE").getQualityState());
        assertEquals(NoonPullDataDomain.PRODUCT, consumers.productManagement(10002L, "STR245027-NAE", "AE").getDataDomain());
        assertEquals(NoonPullDataDomain.ORDER, consumers.orderReadiness(10002L, "STR245027-NAE", "AE").getDataDomain());
    }

    private void assertReadiness(
            NoonPullTaskStatus status,
            NoonPullTriggerMode triggerMode,
            String failureType,
            String expectedState,
            boolean metricsAllowed
    ) {
        setUp();
        if (status != null) {
            NoonPullPlanRecord plan = createPlan(NoonPullDataDomain.SALES, triggerMode);
            NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                    .ownerUserId(10002L)
                    .storeCode("STR245027-NAE")
                    .siteCode("AE")
                    .pullType(NoonPullType.REPORT)
                    .dataDomain(NoonPullDataDomain.SALES)
                    .triggerMode(triggerMode)
                    .targetIdentity("sales:2026-05-21")
                    .targetDateFrom(LocalDate.of(2026, 5, 21))
                    .targetDateTo(LocalDate.of(2026, 5, 21))
                    .build()).orElseThrow();
            persistTaskState(task.getId(), status, failureType);
        }

        NoonBusinessReadinessView view = service().readiness(10002L, "STR245027-NAE", "AE", NoonPullDataDomain.SALES);

        assertEquals(expectedState, view.getQualityState());
        assertEquals(metricsAllowed, view.isMetricsAllowed());
        if (!metricsAllowed) {
            assertFalse(view.hasConfirmedZeroMetrics());
        } else {
            assertTrue(view.isMetricsAllowed());
        }
    }

    private void persistTaskState(Long taskId, NoonPullTaskStatus status, String failureType) {
        NoonPullTaskRecord task = repository.selectTask(taskId);
        task.setStatus(status);
        task.setFailureType(failureType);
        task.setSourceBatchId(status == NoonPullTaskStatus.SUCCEEDED ? "batch-ready" : null);
        repository.updateTask(task);
    }

    private NoonBusinessReadinessService service() {
        return new NoonBusinessReadinessService(repository, clock);
    }

    private NoonPullPlanRecord createPlan(NoonPullDataDomain dataDomain, NoonPullTriggerMode triggerMode) {
        return foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(dataDomain == NoonPullDataDomain.PRODUCT ? NoonPullType.INTERFACE : NoonPullType.REPORT)
                .dataDomain(dataDomain)
                .triggerMode(triggerMode)
                .scheduleExpression("test")
                .build());
    }
}
