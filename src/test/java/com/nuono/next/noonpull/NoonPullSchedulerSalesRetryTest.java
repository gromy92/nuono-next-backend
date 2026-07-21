package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullSchedulerSalesRetryTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant FAILURE_INSTANT = Instant.parse("2026-07-20T12:02:00Z");

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonPullPlanRecord plan;

    @BeforeEach
    void setUp() {
        Clock failureClock = Clock.fixed(FAILURE_INSTANT, ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(
                repository, failureClock, new NoonPullFailurePolicy(failureClock));
        plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR69486-NSA")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
    }

    @Test
    void shouldRetryAtPolicyBackoffBeforeSixHourCircuit() {
        fail(createTask(), 1);

        NoonPullSchedulerResult result = scheduler(Duration.ofMinutes(8)).runDuePlans();

        assertEquals(1, result.getCreatedTaskCount());
        assertEquals(2, repository.listTasks().size());
        assertEquals("sales:2026-06-20..2026-07-19", repository.listTasks().get(1).getTargetIdentity());
    }

    @Test
    void shouldKeepSixHourCircuitAfterTwoProviderFailuresForSameTarget() {
        NoonPullTaskRecord first = createTask();
        fail(first, 1);
        fail(foundationService.retryTask(first.getId()), 2);

        NoonPullSchedulerResult result = scheduler(Duration.ofMinutes(21)).runDuePlans();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(2, repository.listTasks().size());
    }

    private NoonPullTaskRecord createTask() {
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity("sales:2026-06-20..2026-07-19")
                .targetDateFrom(LocalDate.of(2026, 6, 20))
                .targetDateTo(LocalDate.of(2026, 7, 19))
                .build()).orElseThrow();
    }

    private void fail(NoonPullTaskRecord task, int attempt) {
        foundationService.markFailedWithPolicy(task.getId(), "provider unavailable: EOF", attempt);
    }

    private NoonPullScheduler scheduler(Duration afterFailure) {
        Clock retryClock = Clock.fixed(FAILURE_INSTANT.plus(afterFailure), SHANGHAI);
        return new NoonPullScheduler(
                foundationService,
                retryClock,
                new NoonOrderReportSchedulePolicy(retryClock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(retryClock),
                candidate -> true
        );
    }
}
