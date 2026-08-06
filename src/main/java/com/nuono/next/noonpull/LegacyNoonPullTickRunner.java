package com.nuono.next.noonpull;

import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Selects, orders and caps executable legacy tasks for one scheduled tick. */
final class LegacyNoonPullTickRunner {
    private final NoonPullFoundationService foundationService;
    private final LegacyNoonPullTaskDispatcher dispatcher;
    private final int salesReportLimit;
    private final int productInterfaceLimit;
    private final Clock clock;

    LegacyNoonPullTickRunner(
            NoonPullFoundationService foundationService,
            LegacyNoonPullTaskDispatcher dispatcher,
            int salesReportLimit,
            int productInterfaceLimit
    ) {
        this(
                foundationService, dispatcher, salesReportLimit,
                productInterfaceLimit, Clock.systemUTC()
        );
    }

    LegacyNoonPullTickRunner(
            NoonPullFoundationService foundationService,
            LegacyNoonPullTaskDispatcher dispatcher,
            int salesReportLimit,
            int productInterfaceLimit,
            Clock clock
    ) {
        this.foundationService = foundationService;
        this.dispatcher = dispatcher;
        this.salesReportLimit = Math.max(1, salesReportLimit);
        this.productInterfaceLimit = Math.max(1, productInterfaceLimit);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    void execute(
            NoonPullSchedulerResult schedulerResult,
            StoreSiteMaintenanceGate maintenanceGate,
            NoonPullScheduledExecutionResult result
    ) {
        int salesReports = 0;
        int productInterfaces = 0;
        for (NoonPullTaskRecord task : executableTasks(schedulerResult)) {
            if (scheduledMaintenance(task, maintenanceGate)) {
                result.skipped();
                continue;
            }
            if (isSalesReport(task)) {
                if (salesReports >= salesReportLimit) {
                    result.skipped();
                    continue;
                }
                salesReports++;
            }
            if (isProductInterface(task)) {
                if (productInterfaces >= productInterfaceLimit) {
                    result.skipped();
                    continue;
                }
                productInterfaces++;
            }
            dispatcher.dispatch(task, result);
        }
    }

    private List<NoonPullTaskRecord> executableTasks(
            NoonPullSchedulerResult schedulerResult
    ) {
        Map<Long, NoonPullTaskRecord> tasksById = new LinkedHashMap<>();
        if (schedulerResult != null) {
            for (NoonPullTaskRecord task : schedulerResult.getCreatedTasks()) {
                addExecutable(tasksById, task);
            }
        }
        for (NoonPullTaskRecord task : foundationService.listActiveTasks()) {
            addExecutable(tasksById, task);
        }
        return tasksById.values().stream()
                .sorted(Comparator.comparingInt(this::priority)
                        .thenComparing(NoonPullTaskRecord::getId))
                .collect(Collectors.toList());
    }

    private void addExecutable(
            Map<Long, NoonPullTaskRecord> tasksById,
            NoonPullTaskRecord task
    ) {
        if (task == null || task.getId() == null || !hasExecutableStatus(task)) {
            return;
        }
        if (!foundationService.isTaskPlanActive(task)
                || !NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task)) {
            return;
        }
        if (task.getStatus() == NoonPullTaskStatus.RUNNING && !reportPollDue(task)) {
            return;
        }
        tasksById.putIfAbsent(task.getId(), task);
    }

    private boolean hasExecutableStatus(NoonPullTaskRecord task) {
        if (task.getStatus() == NoonPullTaskStatus.QUEUED) {
            return true;
        }
        if (task.getStatus() != NoonPullTaskStatus.RUNNING
                || task.getPullType() != NoonPullType.REPORT) {
            return false;
        }
        return task.getReportExportId() != null
                || (Boolean.TRUE.equals(task.getRetryable())
                    && task.getReportNextPollAt() != null);
    }

    private boolean reportPollDue(NoonPullTaskRecord task) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return task.getReportNextPollAt() == null
                || !task.getReportNextPollAt().isAfter(now);
    }

    private boolean scheduledMaintenance(
            NoonPullTaskRecord task,
            StoreSiteMaintenanceGate gate
    ) {
        StoreSiteMaintenanceGate effectiveGate = gate == null
                ? StoreSiteMaintenanceGate.allowAll() : gate;
        return task != null
                && task.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY
                && effectiveGate.isUnderMaintenance(
                        task.getOwnerUserId(), task.getStoreCode(), task.getSiteCode()
                );
    }

    private int priority(NoonPullTaskRecord task) {
        if (isTask(task, NoonPullDataDomain.SALES, NoonPullType.PAGE_QUERY)) {
            return 0;
        }
        if (isProductInterface(task)) {
            return 1;
        }
        if (isSalesReport(task)) {
            return 2;
        }
        if (isTask(task, NoonPullDataDomain.ORDER, NoonPullType.REPORT)) {
            return 3;
        }
        if (isTask(task, NoonPullDataDomain.FINANCE_TRANSACTION, NoonPullType.REPORT)) {
            return 4;
        }
        if (isTask(task, NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                NoonPullType.INTERFACE)) {
            return 5;
        }
        if (isTask(task, NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                NoonPullType.REPORT)) {
            return 6;
        }
        if (isTask(task, NoonPullDataDomain.NOON_ADVERTISING, NoonPullType.REPORT)) {
            return 7;
        }
        return 10;
    }

    private boolean isSalesReport(NoonPullTaskRecord task) {
        return isTask(task, NoonPullDataDomain.SALES, NoonPullType.REPORT);
    }

    private boolean isProductInterface(NoonPullTaskRecord task) {
        return isTask(task, NoonPullDataDomain.PRODUCT, NoonPullType.INTERFACE);
    }

    private boolean isTask(
            NoonPullTaskRecord task,
            NoonPullDataDomain domain,
            NoonPullType pullType
    ) {
        return task != null
                && task.getDataDomain() == domain
                && task.getPullType() == pullType;
    }
}
