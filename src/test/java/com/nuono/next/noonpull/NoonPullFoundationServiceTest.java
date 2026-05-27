package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
        return NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.ONBOARDING)
                .targetIdentity("catalog:list")
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 1))
                .build();
    }

}
