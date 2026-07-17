package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullAuthRecoveryFoundationTest {
    private static final Long RECOVERY_ID = 91001L;

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        service = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        service.setAuthRecoveryQueue((task, rawFailure) -> {
            repository.blockTaskForAuth(
                    task.getId(),
                    RECOVERY_ID,
                    rawFailure,
                    LocalDateTime.ofInstant(clock.instant(), clock.getZone())
            );
            return Optional.of(RECOVERY_ID);
        });
    }

    @Test
    void authFailureShouldBlockAndResumeSameTaskWithoutLosingExportOrCheckpoint() {
        NoonPullTaskRecord task = reportTask();
        task.setReportExportId("EXP-EXISTING");
        task.setReportExportStatus("PENDING");
        task.setReportPollAttempts(3);
        task.setCheckpointCursor("page:7");
        task.setProcessedItemCount(700);
        task.setRequestCount(8);
        task.setNextResumePosition("page:8");
        repository.updateTask(task);

        NoonPullTaskRecord blocked = service.markFailedWithPolicy(
                task.getId(),
                "auth_required: persisted cookie expired; project=PRJ313934",
                1
        );

        assertEquals(task.getId(), blocked.getId());
        assertEquals(NoonPullTaskStatus.BLOCKED_AUTH, blocked.getStatus());
        assertEquals(NoonPullRetryAction.WAIT_FOR_AUTH.name(), blocked.getRetryAction());
        assertEquals(RECOVERY_ID, blocked.getAuthRecoveryId());
        assertEquals("EXP-EXISTING", blocked.getReportExportId());
        assertEquals("PENDING", blocked.getReportExportStatus());
        assertEquals(3, blocked.getReportPollAttempts());
        assertEquals("page:7", blocked.getCheckpointCursor());
        assertEquals(700, blocked.getProcessedItemCount());
        assertEquals(8, blocked.getRequestCount());
        assertEquals("page:8", blocked.getNextResumePosition());

        NoonPullTaskRecord duplicate = service.createTaskForPlan(task.getPlanId(), reportTaskDraft()).orElseThrow();
        assertEquals(task.getId(), duplicate.getId());
        assertEquals(1, repository.tasks.size());

        assertEquals(1, repository.requeueBlockedTaskAfterAuthForTest(
                task.getId(),
                RECOVERY_ID,
                LocalDateTime.of(2026, 7, 16, 4, 0)
        ));
        NoonPullTaskRecord resumed = repository.selectTask(task.getId());

        assertEquals(task.getId(), resumed.getId());
        assertEquals(NoonPullTaskStatus.QUEUED, resumed.getStatus());
        assertEquals(RECOVERY_ID, resumed.getAuthRecoveryId());
        assertNull(resumed.getFailureType());
        assertNull(resumed.getRetryAction());
        assertEquals("EXP-EXISTING", resumed.getReportExportId());
        assertEquals("PENDING", resumed.getReportExportStatus());
        assertEquals(3, resumed.getReportPollAttempts());
        assertEquals("page:7", resumed.getCheckpointCursor());
        assertEquals(700, resumed.getProcessedItemCount());
        assertEquals(8, resumed.getRequestCount());
        assertEquals("page:8", resumed.getNextResumePosition());

        AtomicInteger createCalls = new AtomicInteger();
        AtomicInteger pollCalls = new AtomicInteger();
        NoonReportPullResult result = new NoonReportPuller(service).execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(308L)
                        .storeCode("STR313934-NAE")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.SALES)
                        .reportType("productviewsandsalesdata")
                        .dateFrom(LocalDate.of(2026, 7, 15))
                        .dateTo(LocalDate.of(2026, 7, 15))
                        .maxPollAttempts(10)
                        .build(),
                new NoonReportProvider() {
                    @Override
                    public String createExport(NoonReportPullRequest request) {
                        createCalls.incrementAndGet();
                        return "MUST-NOT-CREATE";
                    }

                    @Override
                    public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                        pollCalls.incrementAndGet();
                        assertEquals("EXP-EXISTING", exportId);
                        return NoonReportExportStatus.ready("memory://existing-export");
                    }

                    @Override
                    public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                        return "date,sku_parent,units_sold\n".getBytes();
                    }
                },
                file -> NoonReportProcessResult.succeeded(1, 0)
        );

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(0, createCalls.get());
        assertEquals(1, pollCalls.get());
    }

    @Test
    void markRunningShouldNotMoveBlockedTaskBackToRunning() {
        NoonPullTaskRecord task = reportTask();
        service.markFailedWithPolicy(task.getId(), "401 auth_required", 1);

        NoonPullTaskRecord stillBlocked = service.markRunning(task.getId(), "worker-must-not-run");

        assertEquals(NoonPullTaskStatus.BLOCKED_AUTH, stillBlocked.getStatus());
        assertNull(stillBlocked.getLockedBy());
    }

    @Test
    void customProductDetailTaskShouldNotEnterAutomaticAuthRecovery() {
        AtomicInteger queueCalls = new AtomicInteger();
        service.setAuthRecoveryQueue((task, rawFailure) -> {
            queueCalls.incrementAndGet();
            return Optional.of(RECOVERY_ID);
        });
        NoonPullPlanRecord plan = service.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(308L)
                .storeCode("STR313934-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .scheduleExpression("manual")
                .build());
        NoonPullTaskRecord task = service.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("detail:PSKU-313934")
                .build()).orElseThrow();

        NoonPullTaskRecord failed = service.markFailedWithPolicy(
                task.getId(),
                "401 auth_required: persisted cookie expired",
                1
        );

        assertEquals(0, queueCalls.get());
        assertEquals(NoonPullTaskStatus.FAILED, failed.getStatus());
        assertEquals(NoonPullFailureType.AUTH_REQUIRED.code(), failed.getFailureType());
        assertFalse(Boolean.TRUE.equals(failed.getRetryable()));
    }

    @Test
    void staleWorkerWritesShouldNotOverwriteBlockedTaskOrPlanOutcome() {
        NoonPullTaskRecord task = reportTask();
        repository.blockTaskForAuth(
                task.getId(),
                RECOVERY_ID,
                "auth recovery queued",
                LocalDateTime.of(2026, 7, 16, 4, 0)
        );

        NoonPullTaskRecord staleSuccess = service.markSucceeded(task.getId(), "STALE-BATCH", "stale success");
        NoonPullTaskRecord staleFailure = service.markFailed(task.getId(), "timeout", "stale failure");

        assertEquals(NoonPullTaskStatus.BLOCKED_AUTH, staleSuccess.getStatus());
        assertEquals(NoonPullTaskStatus.BLOCKED_AUTH, staleFailure.getStatus());
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());
        assertEquals(NoonPullTaskStatus.BLOCKED_AUTH, persisted.getStatus());
        assertEquals(RECOVERY_ID, persisted.getAuthRecoveryId());
        assertNull(repository.selectPlan(task.getPlanId()).getLatestSuccessAt());
        assertNull(repository.selectPlan(task.getPlanId()).getLatestFailureAt());
    }

    private NoonPullTaskRecord reportTask() {
        NoonPullPlanRecord plan = service.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(308L)
                .storeCode("STR313934-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return service.createTaskForPlan(plan.getId(), reportTaskDraft()).orElseThrow();
    }

    private NoonPullTaskDraft reportTaskDraft() {
        return NoonPullTaskDraft.builder()
                .ownerUserId(308L)
                .storeCode("STR313934-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity("sales:2026-07-15..2026-07-15")
                .targetDateFrom(LocalDate.of(2026, 7, 15))
                .targetDateTo(LocalDate.of(2026, 7, 15))
                .build();
    }
}
