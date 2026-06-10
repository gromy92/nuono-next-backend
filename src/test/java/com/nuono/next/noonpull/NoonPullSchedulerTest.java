package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullSchedulerTest {
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
    void shouldCreateDueSalesAndOrderTasksButNeverDefaultDailyProductTask() {
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        createPlan(NoonPullType.INTERFACE, NoonPullDataDomain.PRODUCT, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");

        NoonPullSchedulerResult result = scheduler().runDuePlans();

        assertEquals(3, result.getCreatedTaskCount());
        List<NoonPullTaskRecord> tasks = repository.listTasks();
        assertTrue(tasks.stream().anyMatch((task) -> task.getDataDomain() == NoonPullDataDomain.SALES
                && "sales:2026-05-20".equals(task.getTargetIdentity())));
        assertTrue(tasks.stream().anyMatch((task) -> task.getDataDomain() == NoonPullDataDomain.SALES
                && "sales:2026-05-21".equals(task.getTargetIdentity())));
        assertTrue(tasks.stream().anyMatch((task) -> task.getDataDomain() == NoonPullDataDomain.ORDER
                && "orders:2026-05-21..2026-05-21".equals(task.getTargetIdentity())));
        assertFalse(tasks.stream().anyMatch((task) -> task.getDataDomain() == NoonPullDataDomain.PRODUCT
                && task.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
    }

    @Test
    void shouldNotRecreateCompletedSalesDailyTargetsOnRepeatedTicks() {
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        NoonPullScheduler scheduler = scheduler();

        NoonPullSchedulerResult firstRun = scheduler.runDuePlans();
        for (NoonPullTaskRecord task : repository.listTasks()) {
            foundationService.markSucceeded(task.getId(), "batch-" + task.getTargetIdentity(), "ready");
        }
        NoonPullSchedulerResult secondRun = scheduler.runDuePlans();

        assertEquals(2, firstRun.getCreatedTaskCount());
        assertTrue(repository.listTasks().stream().anyMatch((task) -> "sales:2026-05-20".equals(task.getTargetIdentity())));
        assertTrue(repository.listTasks().stream().anyMatch((task) -> "sales:2026-05-21".equals(task.getTargetIdentity())));
        assertEquals(0, secondRun.getCreatedTaskCount());
        assertEquals(2, repository.listTasks().size());
    }

    @Test
    void shouldCreateLatestSalesPageQueryTaskAfterReadyTime() {
        Clock afterReady = Clock.fixed(Instant.parse("2026-05-24T01:00:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, afterReady, new NoonPullFailurePolicy(afterReady));
        createPlan(NoonPullType.PAGE_QUERY, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY,
                "latest-day page-query after 08:00 Asia/Shanghai");

        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                afterReady,
                new NoonOrderReportSchedulePolicy(afterReady),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(afterReady),
                (plan) -> true
        );

        NoonPullSchedulerResult result = scheduler.runDuePlans();

        assertEquals(1, result.getCreatedTaskCount());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullType.PAGE_QUERY, task.getPullType());
        assertEquals("sales-page-query:2026-05-23..2026-05-23", task.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 23), task.getTargetDateFrom());
        assertEquals(LocalDate.of(2026, 5, 23), task.getTargetDateTo());
    }

    @Test
    void shouldNotCreateSalesLatestDayTaskBeforeProviderReadyTime() {
        Clock beforeReady = Clock.fixed(Instant.parse("2026-05-23T23:30:00Z"), SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, beforeReady, new NoonPullFailurePolicy(beforeReady));
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");

        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                beforeReady,
                new NoonOrderReportSchedulePolicy(beforeReady),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(beforeReady),
                (plan) -> true
        );

        NoonPullSchedulerResult result = scheduler.runDuePlans();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(0, repository.listTasks().size());
    }

    @Test
    void shouldHonorReportNotReadyRetryDelayWhenFailureClockIsUtcAndSchedulerClockIsShanghai() {
        Instant failureInstant = Instant.parse("2026-05-25T02:08:00Z");
        Clock utcFailureClock = Clock.fixed(failureInstant, ZoneOffset.UTC);
        Clock shanghaiSchedulerClock = Clock.fixed(failureInstant, SHANGHAI);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, utcFailureClock, new NoonPullFailurePolicy(utcFailureClock));
        NoonPullPlanRecord plan = createPlan(
                NoonPullType.REPORT,
                NoonPullDataDomain.SALES,
                NoonPullTriggerMode.SCHEDULED_DAILY,
                "daily"
        );
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("sales:2026-05-24")
                .targetDateFrom(LocalDate.of(2026, 5, 24))
                .targetDateTo(LocalDate.of(2026, 5, 24))
                .build()).orElseThrow();
        foundationService.markFailedWithPolicy(task.getId(), "report not ready", 1);

        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                shanghaiSchedulerClock,
                new NoonOrderReportSchedulePolicy(shanghaiSchedulerClock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(shanghaiSchedulerClock),
                (candidate) -> true
        );

        NoonPullSchedulerResult result = scheduler.runDuePlans();

        assertEquals(0, result.getCreatedTaskCount());
    }

    @Test
    void shouldSkipPausedBackoffCooldownProviderDisabledAndDuplicateActiveTasks() {
        NoonPullPlanRecord paused = createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        foundationService.pausePlan(paused.getId(), "manual pause");

        NoonPullPlanRecord backoff = createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        backoff.setNextRetryAt(LocalDateTime.of(2026, 5, 22, 10, 0));
        repository.updatePlan(backoff);

        NoonPullPlanRecord cooldown = createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        cooldown.setLatestSuccessAt(LocalDateTime.of(2026, 5, 22, 8, 59));
        cooldown.setCooldownSeconds(3600);
        repository.updatePlan(cooldown);

        createPlan(10003L, "STR-PROVIDER-OFF", "AE", NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");

        NoonPullPlanRecord duplicate = createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.SCHEDULED_DAILY, "daily");
        foundationService.createTaskForPlan(duplicate.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(duplicate.getOwnerUserId())
                .storeCode(duplicate.getStoreCode())
                .siteCode(duplicate.getSiteCode())
                .pullType(duplicate.getPullType())
                .dataDomain(duplicate.getDataDomain())
                .triggerMode(duplicate.getTriggerMode())
                .targetIdentity("sales:2026-05-21")
                .targetDateFrom(LocalDate.of(2026, 5, 21))
                .targetDateTo(LocalDate.of(2026, 5, 21))
                .build());
        foundationService.createTaskForPlan(duplicate.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(duplicate.getOwnerUserId())
                .storeCode(duplicate.getStoreCode())
                .siteCode(duplicate.getSiteCode())
                .pullType(duplicate.getPullType())
                .dataDomain(duplicate.getDataDomain())
                .triggerMode(duplicate.getTriggerMode())
                .targetIdentity("sales:2026-05-20")
                .targetDateFrom(LocalDate.of(2026, 5, 20))
                .targetDateTo(LocalDate.of(2026, 5, 20))
                .build());

        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                clock,
                new NoonOrderReportSchedulePolicy(clock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(clock),
                (plan) -> !"STR-PROVIDER-OFF".equals(plan.getStoreCode())
        );

        NoonPullSchedulerResult result = scheduler.runDuePlans();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(2, repository.listTasks().size());
    }

    @Test
    void shouldCreateSalesCorrectionAndBudgetedOrderBackfillWindows() {
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.SALES, NoonPullTriggerMode.LOW_FREQUENCY_CORRECTION, "weekly_45_day_correction");
        createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.GAP_BACKFILL,
                "backfill:2026-05-01..2026-05-12;maxDays=5;maxWindows=2");

        NoonPullSchedulerResult result = scheduler().runDuePlans();

        assertEquals(3, result.getCreatedTaskCount());
        assertTrue(repository.listTasks().stream().anyMatch((task) -> task.getDataDomain() == NoonPullDataDomain.SALES
                && "sales-correction:2026-04-07..2026-05-21".equals(task.getTargetIdentity())));
        assertTrue(repository.listTasks().stream().anyMatch((task) ->
                "orders:2026-05-01..2026-05-05".equals(task.getTargetIdentity())));
        assertTrue(repository.listTasks().stream().anyMatch((task) ->
                "orders:2026-05-06..2026-05-10".equals(task.getTargetIdentity())));
    }

    @Test
    void shouldRecoverStaleRunningTaskBeforeScanningDuePlans() {
        NoonPullPlanRecord plan = createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.GAP_BACKFILL,
                "backfill:2026-05-01..2026-05-05;maxDays=5;maxWindows=1");
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("orders:2026-05-01..2026-05-05")
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 5))
                .build()).orElseThrow();
        NoonPullTaskRecord running = foundationService.markRunning(task.getId(), "noon-report-puller");
        running.setStartedAt(LocalDateTime.of(2026, 5, 21, 20, 0));
        repository.updateTask(running);

        scheduler().runDuePlans();

        NoonPullTaskRecord recovered = repository.selectTask(task.getId());
        assertEquals(NoonPullTaskStatus.FAILED, recovered.getStatus());
        assertEquals("timeout", recovered.getFailureType());
        assertEquals("RETRY", recovered.getRetryAction());
        assertEquals(Boolean.TRUE, recovered.getRetryable());
        assertEquals(Boolean.FALSE, recovered.getRequiresManualAction());
        assertNotNull(recovered.getFinishedAt());
        assertTrue(recovered.getDiagnosticSummary().contains("stale running task"));
        NoonPullTaskRecord reopened = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("orders:2026-05-01..2026-05-05")
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 5))
                .build()).orElseThrow();
        assertEquals(2, repository.listTasks().size());
        assertEquals(NoonPullTaskStatus.QUEUED, reopened.getStatus());
        assertEquals("orders:2026-05-01..2026-05-05", reopened.getTargetIdentity());
    }

    @Test
    void shouldLeaveFreshRunningAndTerminalTasksUntouchedDuringStaleRecovery() {
        NoonPullPlanRecord plan = createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.GAP_BACKFILL,
                "backfill:2026-05-01..2026-05-10;maxDays=5;maxWindows=2");
        NoonPullTaskRecord fresh = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("orders:2026-05-01..2026-05-05")
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 5))
                .build()).orElseThrow();
        fresh = foundationService.markRunning(fresh.getId(), "noon-report-puller");
        fresh.setStartedAt(LocalDateTime.of(2026, 5, 22, 8, 30));
        repository.updateTask(fresh);
        NoonPullTaskRecord succeeded = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("orders:2026-05-06..2026-05-10")
                .targetDateFrom(LocalDate.of(2026, 5, 6))
                .targetDateTo(LocalDate.of(2026, 5, 10))
                .build()).orElseThrow();
        foundationService.markSucceeded(succeeded.getId(), "batch-order-1", "done");

        int recovered = foundationService.recoverStaleRunningTasks(Duration.ofHours(2));

        assertEquals(0, recovered);
        assertEquals(NoonPullTaskStatus.RUNNING, repository.selectTask(fresh.getId()).getStatus());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, repository.selectTask(succeeded.getId()).getStatus());
    }

    @Test
    void shouldUseConfiguredStaleRunningThreshold() {
        NoonPullPlanRecord plan = createPlan(NoonPullType.REPORT, NoonPullDataDomain.ORDER, NoonPullTriggerMode.GAP_BACKFILL,
                "backfill:2026-05-01..2026-05-05;maxDays=5;maxWindows=1");
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("orders:2026-05-01..2026-05-05")
                .targetDateFrom(LocalDate.of(2026, 5, 1))
                .targetDateTo(LocalDate.of(2026, 5, 5))
                .build()).orElseThrow();
        NoonPullTaskRecord running = foundationService.markRunning(task.getId(), "noon-report-puller");
        running.setStartedAt(LocalDateTime.of(2026, 5, 22, 7, 30));
        repository.updateTask(running);
        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                clock,
                new NoonOrderReportSchedulePolicy(clock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(clock),
                (candidate) -> true,
                Duration.ofMinutes(60)
        );

        scheduler.runDuePlans();

        assertEquals(NoonPullTaskStatus.FAILED, repository.selectTask(task.getId()).getStatus());
    }

    private NoonPullScheduler scheduler() {
        return new NoonPullScheduler(
                foundationService,
                clock,
                new NoonOrderReportSchedulePolicy(clock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(clock),
                (plan) -> true
        );
    }

    private NoonPullPlanRecord createPlan(
            NoonPullType pullType,
            NoonPullDataDomain dataDomain,
            NoonPullTriggerMode triggerMode,
            String scheduleExpression
    ) {
        return createPlan(10002L, "STR245027-NAE", "AE", pullType, dataDomain, triggerMode, scheduleExpression);
    }

    private NoonPullPlanRecord createPlan(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            NoonPullType pullType,
            NoonPullDataDomain dataDomain,
            NoonPullTriggerMode triggerMode,
            String scheduleExpression
    ) {
        return foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .pullType(pullType)
                .dataDomain(dataDomain)
                .triggerMode(triggerMode)
                .scheduleExpression(scheduleExpression)
                .build());
    }
}
