package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonReportCreateCoordinatorTest {
    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonPullFailurePolicy failurePolicy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-02T01:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        failurePolicy = new NoonPullFailurePolicy(clock);
        foundationService = new NoonPullFoundationService(repository, clock, failurePolicy);
    }

    @Test
    void unknownCreateOutcomeMustNeverIssueASecondCreate() {
        NoonPullTaskRecord task = createTask("sales:create-unknown");
        AtomicInteger creates = new AtomicInteger();

        NoonReportCreateCoordinator.Attempt first = NoonReportCreateCoordinator.ensureHandle(
                task.getId(),
                task,
                "sales report",
                () -> {
                    creates.incrementAndGet();
                    throw new IllegalStateException("timeout while reading create response");
                },
                foundationService,
                failurePolicy
        );
        NoonPullTaskRecord unresolved = repository.selectTask(task.getId());

        assertTrue(first.isWaiting());
        assertEquals(1, creates.get());
        assertEquals("CREATE_INTENT", unresolved.getReportExportStatus());
        assertEquals("provider_unavailable", unresolved.getFailureType());

        NoonReportCreateCoordinator.Attempt second = NoonReportCreateCoordinator.ensureHandle(
                task.getId(),
                unresolved,
                "sales report",
                () -> {
                    creates.incrementAndGet();
                    return "MUST-NOT-BE-CREATED";
                },
                foundationService,
                failurePolicy
        );

        assertTrue(second.isWaiting());
        assertEquals(1, creates.get());
        assertEquals("CREATE_INTENT", repository.selectTask(task.getId()).getReportExportStatus());
    }

    @Test
    void explicitRemoteRejectionMayCreateAgainAfterItsRecoveryGateClears() {
        NoonPullTaskRecord task = createTask("sales:create-rejected");
        AtomicInteger creates = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> NoonReportCreateCoordinator.ensureHandle(
                task.getId(),
                task,
                "sales report",
                () -> {
                    creates.incrementAndGet();
                    throw new IllegalStateException("HTTP 403 Forbidden");
                },
                foundationService,
                failurePolicy
        ));
        NoonPullTaskRecord rejected = repository.selectTask(task.getId());
        assertEquals("CREATE_REJECTED", rejected.getReportExportStatus());

        NoonReportCreateCoordinator.Attempt retried = NoonReportCreateCoordinator.ensureHandle(
                task.getId(),
                rejected,
                "sales report",
                () -> {
                    creates.incrementAndGet();
                    return "EXP-RECOVERED";
                },
                foundationService,
                failurePolicy
        );

        assertEquals(2, creates.get());
        assertEquals("EXP-RECOVERED", retried.exportId());
        assertEquals("CREATED", repository.selectTask(task.getId()).getReportExportStatus());
    }

    @Test
    void missingCreateHandleIsAlsoAnUnknownOutcome() {
        NoonPullTaskRecord task = createTask("sales:create-handle-missing");

        NoonReportCreateCoordinator.Attempt result = NoonReportCreateCoordinator.ensureHandle(
                task.getId(),
                task,
                "sales report",
                () -> " ",
                foundationService,
                failurePolicy
        );

        assertTrue(result.isWaiting());
        NoonPullTaskRecord unresolved = repository.selectTask(task.getId());
        assertEquals("CREATE_INTENT", unresolved.getReportExportStatus());
        assertEquals(Boolean.TRUE, unresolved.getRetryable());
    }

    @Test
    void zeroRowReadyResponseWithoutLocatorMustKeepPollingTheSameHandle() {
        NoonPullTaskRecord task = createTask("sales:ready-empty-without-proof");
        FakeReportProvider provider = FakeReportProvider.sequence(NoonReportExportStatus.ready(null, 0));
        NoonReportPuller puller = new NoonReportPuller(foundationService);

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );
        NoonPullTaskRecord waiting = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals("EXP-1", waiting.getReportExportId());
        assertEquals("report_not_ready", waiting.getFailureType());
        assertEquals(1, provider.calls.stream().filter("create"::equals).count());
        assertEquals(0, provider.calls.stream().filter("download"::equals).count());
    }

    private NoonPullTaskRecord createTask(String targetIdentity) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity(targetIdentity)
                .targetDateFrom(LocalDate.of(2026, 8, 1))
                .targetDateTo(LocalDate.of(2026, 8, 1))
                .build()).orElseThrow();
    }

    private NoonReportPullRequest salesRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.SALES)
                .reportType("productviewsandsalesdata")
                .dateFrom(LocalDate.of(2026, 8, 1))
                .dateTo(LocalDate.of(2026, 8, 1))
                .maxPollAttempts(18)
                .build();
    }
}
