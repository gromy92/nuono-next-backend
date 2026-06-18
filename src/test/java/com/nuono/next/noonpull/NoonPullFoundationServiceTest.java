package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullFoundationServiceTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNoonPullRepository();
        service = new NoonPullFoundationService(
                repository,
                Clock.fixed(Instant.parse("2026-05-22T04:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldPersistPlanAndRunTaskThroughLifecycle() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();

        assertEquals(NoonPullType.INTERFACE, plan.getPullType());
        assertEquals(NoonPullDataDomain.PRODUCT, plan.getDataDomain());
        assertEquals(NoonPullTaskStatus.QUEUED, task.getStatus());
        assertEquals(plan.getId(), task.getPlanId());
        assertEquals("owner:307|store:STR245027|site:AE|type:INTERFACE|domain:PRODUCT|target:catalog:list",
                task.getActiveLockKey());

        NoonPullTaskRecord running = service.markRunning(task.getId(), "worker-1");
        assertEquals(NoonPullTaskStatus.RUNNING, running.getStatus());
        assertEquals("worker-1", running.getLockedBy());

        NoonPullTaskRecord succeeded = service.markSucceeded(
                task.getId(),
                "batch-product-001",
                "Pulled 37 products from Noon"
        );

        assertEquals(NoonPullTaskStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals("batch-product-001", succeeded.getSourceBatchId());
        assertEquals("Pulled 37 products from Noon", succeeded.getDiagnosticSummary());
        assertEquals(succeeded.getFinishedAt(), repository.selectPlan(plan.getId()).getLatestSuccessAt());
    }

    @Test
    void shouldReturnExistingActiveTaskForDuplicateScopeAndTarget() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());

        NoonPullTaskRecord first = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();
        NoonPullTaskRecord duplicate = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();

        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1, repository.tasks.size());
    }

    @Test
    void shouldAllowSameScopeAndTargetAfterTerminalStatus() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord first = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();
        service.markSucceeded(first.getId(), "batch-product-001", "done");

        NoonPullTaskRecord second = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, repository.tasks.size());
    }

    @Test
    void shouldNotCreateGapBackfillTaskAfterNonRetryableFailureForSameWindow() {
        NoonPullPlanRecord plan = service.createPlan(orderBackfillPlan());
        NoonPullTaskRecord failedTask = service.createTaskForPlan(plan.getId(), orderBackfillTask()).orElseThrow();
        service.markFailedWithPolicy(failedTask.getId(), "missing columns", 1);

        Optional<NoonPullTaskRecord> duplicate = service.createTaskForPlan(plan.getId(), orderBackfillTask());

        assertFalse(duplicate.isPresent());
        assertEquals(1, repository.tasks.size());
    }

    @Test
    void shouldNotCreateGapBackfillTaskAfterSuccessForSameWindow() {
        NoonPullPlanRecord plan = service.createPlan(orderBackfillPlan());
        NoonPullTaskRecord succeededTask = service.createTaskForPlan(plan.getId(), orderBackfillTask()).orElseThrow();
        service.markSucceeded(succeededTask.getId(), "batch-order-001", "imported=271");

        Optional<NoonPullTaskRecord> duplicate = service.createTaskForPlan(plan.getId(), orderBackfillTask());

        assertFalse(duplicate.isPresent());
        assertEquals(1, repository.tasks.size());
    }

    @Test
    void shouldCreateGapBackfillTaskAfterRetryableFailureForSameWindow() {
        NoonPullPlanRecord plan = service.createPlan(orderBackfillPlan());
        NoonPullTaskRecord failedTask = service.createTaskForPlan(plan.getId(), orderBackfillTask()).orElseThrow();
        service.markFailedWithPolicy(failedTask.getId(), "timeout", 1);

        Optional<NoonPullTaskRecord> retry = service.createTaskForPlan(plan.getId(), orderBackfillTask());

        assertTrue(retry.isPresent());
        assertNotEquals(failedTask.getId(), retry.orElseThrow().getId());
        assertEquals(2, repository.tasks.size());
    }

    @Test
    void shouldRecoverStaleQueuedTaskOutsideRecentTaskWindow() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord staleTask = service.createTaskForPlan(plan.getId(), productListTask("catalog:stale"))
                .orElseThrow();
        staleTask.setQueuedAt(LocalDateTime.of(2026, 5, 20, 1, 0));
        repository.updateTask(staleTask);

        for (int i = 0; i < 210; i++) {
            NoonPullTaskRecord recent = service.createTaskForPlan(
                    plan.getId(),
                    productListTask("catalog:recent:" + i)
            ).orElseThrow();
            service.markSucceeded(recent.getId(), "batch-" + i, "done");
        }
        repository.limitListTasksToRecent(200);

        assertFalse(repository.listTasks().stream().anyMatch((task) -> staleTask.getId().equals(task.getId())));

        int recovered = service.recoverStaleQueuedTasks(Duration.ofMinutes(30));

        NoonPullTaskRecord recoveredTask = repository.selectTask(staleTask.getId());
        assertEquals(1, recovered);
        assertEquals(NoonPullTaskStatus.FAILED, recoveredTask.getStatus());
        assertEquals("timeout", recoveredTask.getFailureType());
        assertEquals(NoonPullRetryAction.RETRY.name(), recoveredTask.getRetryAction());
        assertTrue(recoveredTask.getDiagnosticSummary().contains("stale queued task exceeded 30 minutes"));
    }

    @Test
    void shouldNotRecoverRunningReportExportAsStaleBecauseItUsesNextPollState() {
        NoonPullPlanRecord plan = service.createPlan(orderBackfillPlan());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), orderBackfillTask()).orElseThrow();
        NoonPullTaskRecord running = service.markRunning(task.getId(), "worker-1");
        running.setStartedAt(LocalDateTime.of(2026, 5, 20, 1, 0));
        running.setReportExportId("EXP-1");
        running.setReportExportStatus("PENDING");
        repository.updateTask(running);

        int recovered = service.recoverStaleRunningTasks(Duration.ofMinutes(30));

        NoonPullTaskRecord persisted = repository.selectTask(task.getId());
        assertEquals(0, recovered);
        assertEquals(NoonPullTaskStatus.RUNNING, persisted.getStatus());
        assertEquals("EXP-1", persisted.getReportExportId());
    }

    @Test
    void shouldNotCreateScheduledTaskWhenPlanIsPaused() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());

        NoonPullPlanRecord paused = service.pausePlan(plan.getId(), "provider disabled during smoke");
        Optional<NoonPullTaskRecord> skipped = service.createTaskForPlan(paused.getId(), productListTask());

        assertTrue(paused.isPaused());
        assertFalse(skipped.isPresent());
        assertEquals(0, repository.tasks.size());

        NoonPullPlanRecord resumed = service.resumePlan(plan.getId());
        assertFalse(resumed.isPaused());
        assertNull(resumed.getPauseReason());
        assertTrue(service.createTaskForPlan(resumed.getId(), productListTask()).isPresent());
    }

    @Test
    void shouldRedactSensitiveDiagnosticsBeforePersistingFailure() {
        NoonPullPlanRecord plan = service.createPlan(productPlan());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), productListTask()).orElseThrow();

        NoonPullTaskRecord failed = service.markFailed(
                task.getId(),
                "auth_required",
                "cookie=abc123 password=secret Authorization: Bearer token-123 api_key=live-key"
        );

        assertEquals(NoonPullTaskStatus.FAILED, failed.getStatus());
        assertEquals("auth_required", failed.getFailureType());
        assertFalse(failed.getDiagnosticSummary().contains("abc123"));
        assertFalse(failed.getDiagnosticSummary().contains("secret"));
        assertFalse(failed.getDiagnosticSummary().contains("token-123"));
        assertFalse(failed.getDiagnosticSummary().contains("live-key"));
        assertTrue(failed.getDiagnosticSummary().contains("[redacted]"));
        assertEquals(failed.getFinishedAt(), repository.selectPlan(plan.getId()).getLatestFailureAt());
    }

    private NoonPullPlanDraft productPlan() {
        return NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .scheduleExpression("manual")
                .build();
    }

    private NoonPullTaskDraft productListTask() {
        return productListTask("catalog:list");
    }

    private NoonPullTaskDraft productListTask(String targetIdentity) {
        return NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity(targetIdentity)
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 1))
                .build();
    }

    private NoonPullPlanDraft orderBackfillPlan() {
        return NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .scheduleExpression("backfill:2025-11-25..2025-12-25;maxDays=31;maxWindows=1")
                .build();
    }

    private NoonPullTaskDraft orderBackfillTask() {
        return NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.GAP_BACKFILL)
                .targetIdentity("orders:2025-11-25..2025-12-25")
                .targetDateFrom(LocalDate.of(2025, 11, 25))
                .targetDateTo(LocalDate.of(2025, 12, 25))
                .build();
    }

}
