package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullRetryTaskTest {
    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        service = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
    }

    @Test
    void shouldRetryOnlyRetryableFailedTaskWithSameTarget() {
        NoonPullTaskRecord failed = failedTask("report_not_ready", true, false, "DELAY");

        NoonPullTaskRecord retry = service.retryTask(failed.getId());

        assertEquals(NoonPullTaskStatus.QUEUED, retry.getStatus());
        assertEquals(failed.getPlanId(), retry.getPlanId());
        assertEquals("sales:2026-05-21", retry.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 21), retry.getTargetDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), retry.getTargetDateTo());
    }

    @Test
    void shouldRejectRetryForManualActionFailures() {
        NoonPullTaskRecord failed = failedTask("provider_not_configured", false, true, "MANUAL_ACTION");

        assertThrows(IllegalStateException.class, () -> service.retryTask(failed.getId()));
    }

    private NoonPullTaskRecord failedTask(
            String failureType,
            boolean retryable,
            boolean requiresManualAction,
            String retryAction
    ) {
        NoonPullPlanRecord plan = service.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
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
        task.setStatus(NoonPullTaskStatus.FAILED);
        task.setFailureType(failureType);
        task.setRetryable(retryable);
        task.setRequiresManualAction(requiresManualAction);
        task.setRetryAction(retryAction);
        repository.updateTask(task);
        return task;
    }
}
