package com.nuono.next.noonpull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Conditional legacy rollback facade; planning and eligibility live in deep internal modules. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class NoonPullScheduler {
    private static final Duration STALE_RUNNING_TASK_MAX_AGE = Duration.ofHours(2);
    private static final Duration STALE_QUEUED_TASK_MAX_AGE = Duration.ofMinutes(30);

    private final NoonPullFoundationService foundationService;
    private final LegacyNoonSchedulePlanner planner;
    private final LegacyNoonScheduleEligibility eligibility;
    private final Duration staleRunningTaskMaxAge;
    private final Duration staleQueuedTaskMaxAge;
    private StoreSiteMaintenanceGate maintenanceGate = StoreSiteMaintenanceGate.allowAll();

    @Autowired
    public NoonPullScheduler(
            NoonPullFoundationService foundationService,
            @Value("${nuono.noon.pull.scheduler.stale-running-task-max-age-minutes:120}")
            long staleRunningTaskMaxAgeMinutes,
            @Value("${nuono.noon.pull.scheduler.stale-queued-task-max-age-minutes:30}")
            long staleQueuedTaskMaxAgeMinutes,
            ObjectProvider<NoonProviderAvailability> providerAvailability
    ) {
        this(
                foundationService,
                Clock.system(LegacyNoonScheduleCalendar.SHANGHAI),
                new NoonOrderReportSchedulePolicy(),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(
                        Clock.system(LegacyNoonScheduleCalendar.SHANGHAI)
                ),
                providerAvailability.getIfAvailable(() -> plan -> true),
                Duration.ofMinutes(staleRunningTaskMaxAgeMinutes),
                Duration.ofMinutes(staleQueuedTaskMaxAgeMinutes)
        );
    }

    public NoonPullScheduler(
            NoonPullFoundationService foundationService,
            Clock clock,
            NoonOrderReportSchedulePolicy orderSchedulePolicy,
            NoonOrderBackfillPlanner orderBackfillPlanner,
            NoonSalesRetentionPolicy salesRetentionPolicy,
            NoonProviderAvailability providerAvailability
    ) {
        this(
                foundationService, clock, orderSchedulePolicy, orderBackfillPlanner,
                salesRetentionPolicy, providerAvailability,
                STALE_RUNNING_TASK_MAX_AGE, STALE_QUEUED_TASK_MAX_AGE
        );
    }

    public NoonPullScheduler(
            NoonPullFoundationService foundationService,
            Clock clock,
            NoonOrderReportSchedulePolicy orderSchedulePolicy,
            NoonOrderBackfillPlanner orderBackfillPlanner,
            NoonSalesRetentionPolicy salesRetentionPolicy,
            NoonProviderAvailability providerAvailability,
            Duration staleRunningTaskMaxAge
    ) {
        this(
                foundationService, clock, orderSchedulePolicy, orderBackfillPlanner,
                salesRetentionPolicy, providerAvailability,
                staleRunningTaskMaxAge, STALE_QUEUED_TASK_MAX_AGE
        );
    }

    public NoonPullScheduler(
            NoonPullFoundationService foundationService,
            Clock clock,
            NoonOrderReportSchedulePolicy orderSchedulePolicy,
            NoonOrderBackfillPlanner orderBackfillPlanner,
            NoonSalesRetentionPolicy salesRetentionPolicy,
            NoonProviderAvailability providerAvailability,
            Duration staleRunningTaskMaxAge,
            Duration staleQueuedTaskMaxAge
    ) {
        this.foundationService = foundationService;
        LegacyNoonScheduleCalendar calendar = new LegacyNoonScheduleCalendar(clock);
        this.planner = new LegacyNoonSchedulePlanner(
                calendar, orderSchedulePolicy, orderBackfillPlanner, salesRetentionPolicy
        );
        this.eligibility = new LegacyNoonScheduleEligibility(
                foundationService, calendar, providerAvailability
        );
        this.staleRunningTaskMaxAge = safeMaxAge(
                staleRunningTaskMaxAge, STALE_RUNNING_TASK_MAX_AGE
        );
        this.staleQueuedTaskMaxAge = safeMaxAge(
                staleQueuedTaskMaxAge, STALE_QUEUED_TASK_MAX_AGE
        );
    }

    @Autowired(required = false)
    void setMaintenanceGate(StoreSiteMaintenanceGate maintenanceGate) {
        this.maintenanceGate = maintenanceGate == null
                ? StoreSiteMaintenanceGate.allowAll() : maintenanceGate;
    }

    public NoonPullSchedulerResult runDuePlans() {
        NoonPullSchedulerResult result = new NoonPullSchedulerResult();
        foundationService.recoverStaleRunningTasks(staleRunningTaskMaxAge);
        foundationService.recoverStaleQueuedTasks(staleQueuedTaskMaxAge);
        for (NoonPullPlanRecord plan : foundationService.listPlans()) {
            result.scanned();
            if (scheduledMaintenance(plan)) {
                result.maintenanceSkipped();
                continue;
            }
            if (!eligibility.isRunnable(plan)) {
                result.skipped();
                continue;
            }
            List<NoonPullTaskRecord> created = createAndIdentifyNewTasks(plan);
            if (created.isEmpty()) {
                result.skipped();
            } else {
                created.forEach(result::created);
            }
        }
        return result;
    }

    private List<NoonPullTaskRecord> createAndIdentifyNewTasks(NoonPullPlanRecord plan) {
        Set<Long> beforeTaskIds = foundationService.listTasks().stream()
                .map(NoonPullTaskRecord::getId)
                .collect(Collectors.toCollection(HashSet::new));
        for (NoonPullTaskDraft draft : planner.tasksFor(plan)) {
            foundationService.createTaskForPlan(plan.getId(), draft);
        }
        return foundationService.listTasks().stream()
                .filter(task -> !beforeTaskIds.contains(task.getId()))
                .collect(Collectors.toList());
    }

    private boolean scheduledMaintenance(NoonPullPlanRecord plan) {
        return plan != null
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY
                && maintenanceGate.isUnderMaintenance(
                        plan.getOwnerUserId(), plan.getStoreCode(), plan.getSiteCode()
                );
    }

    private Duration safeMaxAge(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
