package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullScheduler {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalTime SALES_READY_AFTER = LocalTime.of(8, 0);
    private static final LocalTime SALES_LATEST_DAY_READY_AFTER = LocalTime.of(20, 0);
    private static final LocalTime ADS_READY_AFTER = LocalTime.of(8, 0);
    private static final LocalTime FINANCE_TRANSACTION_READY_AFTER = LocalTime.of(22, 30);
    private static final LocalTime OFFICIAL_WAREHOUSE_INVENTORY_READY_AFTER = LocalTime.of(23, 0);
    private static final LocalTime OFFICIAL_WAREHOUSE_FBN_RECEIVED_READY_AFTER = LocalTime.of(23, 30);
    private static final Duration STALE_RUNNING_TASK_MAX_AGE = Duration.ofHours(2);
    private static final Duration STALE_QUEUED_TASK_MAX_AGE = Duration.ofMinutes(30);

    private final NoonPullFoundationService foundationService;
    private final Clock clock;
    private final NoonOrderReportSchedulePolicy orderSchedulePolicy;
    private final NoonOrderBackfillPlanner orderBackfillPlanner;
    private final NoonSalesRetentionPolicy salesRetentionPolicy;
    private final NoonProviderAvailability providerAvailability;
    private final Duration staleRunningTaskMaxAge;
    private final Duration staleQueuedTaskMaxAge;

    @Autowired
    public NoonPullScheduler(
            NoonPullFoundationService foundationService,
            @Value("${nuono.noon.pull.scheduler.stale-running-task-max-age-minutes:120}")
            long staleRunningTaskMaxAgeMinutes,
            @Value("${nuono.noon.pull.scheduler.stale-queued-task-max-age-minutes:30}")
            long staleQueuedTaskMaxAgeMinutes
    ) {
        this(
                foundationService,
                Clock.system(SHANGHAI),
                new NoonOrderReportSchedulePolicy(),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(Clock.system(SHANGHAI)),
                (plan) -> true,
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
                foundationService,
                clock,
                orderSchedulePolicy,
                orderBackfillPlanner,
                salesRetentionPolicy,
                providerAvailability,
                STALE_RUNNING_TASK_MAX_AGE,
                STALE_QUEUED_TASK_MAX_AGE
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
                foundationService,
                clock,
                orderSchedulePolicy,
                orderBackfillPlanner,
                salesRetentionPolicy,
                providerAvailability,
                staleRunningTaskMaxAge,
                STALE_QUEUED_TASK_MAX_AGE
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
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
        this.orderSchedulePolicy = orderSchedulePolicy;
        this.orderBackfillPlanner = orderBackfillPlanner;
        this.salesRetentionPolicy = salesRetentionPolicy;
        this.providerAvailability = providerAvailability == null ? (plan) -> true : providerAvailability;
        this.staleRunningTaskMaxAge = safeMaxAge(staleRunningTaskMaxAge, STALE_RUNNING_TASK_MAX_AGE);
        this.staleQueuedTaskMaxAge = safeMaxAge(staleQueuedTaskMaxAge, STALE_QUEUED_TASK_MAX_AGE);
    }

    public NoonPullSchedulerResult runDuePlans() {
        NoonPullSchedulerResult result = new NoonPullSchedulerResult();
        foundationService.recoverStaleRunningTasks(staleRunningTaskMaxAge);
        foundationService.recoverStaleQueuedTasks(staleQueuedTaskMaxAge);
        for (NoonPullPlanRecord plan : foundationService.listPlans()) {
            result.scanned();
            if (!isRunnable(plan)) {
                result.skipped();
                continue;
            }
            Set<Long> beforeTaskIds = foundationService.listTasks().stream()
                    .map(NoonPullTaskRecord::getId)
                    .collect(Collectors.toCollection(HashSet::new));
            createTasksForPlan(plan);
            List<NoonPullTaskRecord> createdTasks = foundationService.listTasks().stream()
                    .filter((task) -> !beforeTaskIds.contains(task.getId()))
                    .collect(Collectors.toList());
            if (!createdTasks.isEmpty()) {
                for (NoonPullTaskRecord task : createdTasks) {
                    result.created(task);
                }
            } else {
                result.skipped();
            }
        }
        return result;
    }

    private void createTasksForPlan(NoonPullPlanRecord plan) {
        if (plan.getDataDomain() == NoonPullDataDomain.PRODUCT
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            LocalDate targetDate = LocalDate.now(clock);
            createTask(plan, "product-list:" + targetDate + ".." + targetDate, targetDate, targetDate);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.SALES
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            if (!isSalesReportReadyWindow()) {
                return;
            }
            LocalDate targetDate = latestAvailableDate();
            if (plan.getPullType() == NoonPullType.PAGE_QUERY) {
                createTask(plan, "sales-page-query:" + targetDate + ".." + targetDate, targetDate, targetDate);
                return;
            }
            if (plan.getPullType() == NoonPullType.REPORT) {
                if (!isSalesLatestDayReportReadyWindow()) {
                    return;
                }
                LocalDate targetFrom = targetDate.minusDays(29);
                createTask(plan, "sales:" + targetFrom + ".." + targetDate, targetFrom, targetDate);
            }
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.SALES
                && plan.getTriggerMode() == NoonPullTriggerMode.LOW_FREQUENCY_CORRECTION) {
            if (!isSalesReportReadyWindow()) {
                return;
            }
            LocalDate targetTo = latestAvailableDate();
            LocalDate targetFrom = salesRetentionPolicy.weeklyCorrection(targetTo).getDateFrom();
            createTask(plan, "sales-correction:" + targetFrom + ".." + targetTo, targetFrom, targetTo);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.ORDER
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            NoonOrderDailyPullPlan dailyPlan = orderSchedulePolicy.dailyPlan(
                    plan.getOwnerUserId(),
                    plan.getStoreCode(),
                    plan.getSiteCode()
            );
            if (!dailyPlan.isDue()) {
                return;
            }
            LocalDate targetDate = dailyPlan.getTargetDate();
            createTask(plan, "orders:" + targetDate + ".." + targetDate, targetDate, targetDate);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.FINANCE_TRANSACTION
                && plan.getPullType() == NoonPullType.REPORT
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            if (!isFinanceTransactionReportReadyWindow()) {
                return;
            }
            LocalDate targetTo = latestAvailableDate();
            LocalDate targetFrom = targetTo.minusDays(6);
            createTask(plan, "finance-transactions:" + targetFrom + ".." + targetTo, targetFrom, targetTo);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.NOON_ADVERTISING
                && plan.getPullType() == NoonPullType.REPORT
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            if (!isAdsReportReadyWindow()) {
                return;
            }
            LocalDate targetDate = latestAvailableDate();
            createTask(plan, "ads:" + targetDate + ".." + targetDate, targetDate, targetDate);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY
                && plan.getPullType() == NoonPullType.INTERFACE
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            if (!isOfficialWarehouseInventoryReadyWindow()) {
                return;
            }
            LocalDate targetDate = LocalDate.now(clock);
            createTask(plan, "official-warehouse-inventory:" + targetDate, targetDate, targetDate);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED
                && plan.getPullType() == NoonPullType.REPORT
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY) {
            if (!isOfficialWarehouseFbnReceivedReadyWindow()) {
                return;
            }
            LocalDate targetDate = latestAvailableDate();
            createTask(plan, "official-warehouse-fbn-received:" + targetDate + ".." + targetDate, targetDate, targetDate);
            return;
        }
        if (plan.getDataDomain() == NoonPullDataDomain.ORDER
                && plan.getTriggerMode() == NoonPullTriggerMode.GAP_BACKFILL) {
            BackfillSchedule schedule = BackfillSchedule.parse(plan.getScheduleExpression());
            if (schedule == null) {
                return;
            }
            NoonOrderBackfillPlan backfillPlan = orderBackfillPlanner.plan(
                    schedule.dateFrom,
                    schedule.dateTo,
                    schedule.maxDaysPerWindow,
                    schedule.maxWindowsPerRun
            );
            for (NoonOrderBackfillPlan.Window window : backfillPlan.getWindows()) {
                createTask(plan, "orders:" + window.getDateFrom() + ".." + window.getDateTo(),
                        window.getDateFrom(), window.getDateTo());
            }
        }
    }

    private boolean isRunnable(NoonPullPlanRecord plan) {
        if (plan == null || !plan.isEnabled() || plan.isPaused()) {
            return false;
        }
        // Pull task timestamps are stored as UTC LocalDateTime values in gmt_* columns.
        // Business schedule dates stay on Asia/Shanghai, but retry/cooldown timestamps must not.
        LocalDateTime persistedNow = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (plan.getNextRetryAt() != null && plan.getNextRetryAt().isAfter(persistedNow)) {
            return false;
        }
        if (plan.getLatestSuccessAt() != null && plan.getCooldownSeconds() != null && plan.getCooldownSeconds() > 0) {
            if (plan.getLatestSuccessAt().plusSeconds(plan.getCooldownSeconds()).isAfter(persistedNow)) {
                return false;
            }
        }
        return providerAvailability.isAvailable(plan);
    }

    private Optional<NoonPullTaskRecord> createTask(
            NoonPullPlanRecord plan,
            String targetIdentity,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity(targetIdentity)
                .targetDateFrom(dateFrom)
                .targetDateTo(dateTo)
                .build());
    }

    private LocalDate latestAvailableDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    private boolean isSalesReportReadyWindow() {
        return !LocalTime.now(clock).isBefore(SALES_READY_AFTER);
    }

    private boolean isSalesLatestDayReportReadyWindow() {
        return !LocalTime.now(clock).isBefore(SALES_LATEST_DAY_READY_AFTER);
    }

    private boolean isFinanceTransactionReportReadyWindow() {
        return !LocalTime.now(clock).isBefore(FINANCE_TRANSACTION_READY_AFTER);
    }

    private boolean isAdsReportReadyWindow() {
        return !LocalTime.now(clock).isBefore(ADS_READY_AFTER);
    }

    private boolean isOfficialWarehouseInventoryReadyWindow() {
        return !LocalTime.now(clock).isBefore(OFFICIAL_WAREHOUSE_INVENTORY_READY_AFTER);
    }

    private boolean isOfficialWarehouseFbnReceivedReadyWindow() {
        return !LocalTime.now(clock).isBefore(OFFICIAL_WAREHOUSE_FBN_RECEIVED_READY_AFTER);
    }

    private Duration safeMaxAge(Duration maxAge, Duration fallback) {
        return maxAge == null || maxAge.isNegative() || maxAge.isZero()
                ? fallback
                : maxAge;
    }

    private static final class BackfillSchedule {
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final int maxDaysPerWindow;
        private final int maxWindowsPerRun;

        private BackfillSchedule(LocalDate dateFrom, LocalDate dateTo, int maxDaysPerWindow, int maxWindowsPerRun) {
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.maxDaysPerWindow = maxDaysPerWindow;
            this.maxWindowsPerRun = maxWindowsPerRun;
        }

        private static BackfillSchedule parse(String expression) {
            if (!StringUtils.hasText(expression) || !expression.startsWith("backfill:")) {
                return null;
            }
            String[] parts = expression.substring("backfill:".length()).split(";", -1);
            String[] range = parts[0].split("\\.\\.", -1);
            if (range.length != 2) {
                return null;
            }
            Map<String, String> options = new LinkedHashMap<>();
            for (int i = 1; i < parts.length; i++) {
                String[] pair = parts[i].split("=", 2);
                if (pair.length == 2) {
                    options.put(pair[0], pair[1]);
                }
            }
            return new BackfillSchedule(
                    LocalDate.parse(range[0]),
                    LocalDate.parse(range[1]),
                    intOption(options, "maxDays", 7),
                    intOption(options, "maxWindows", 1)
            );
        }

        private static int intOption(Map<String, String> options, String key, int fallback) {
            try {
                return Integer.parseInt(options.getOrDefault(key, String.valueOf(fallback)));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}
