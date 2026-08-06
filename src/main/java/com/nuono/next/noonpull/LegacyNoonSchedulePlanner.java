package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Converts one eligible legacy plan into deterministic task drafts without persistence. */
final class LegacyNoonSchedulePlanner {
    private final LegacyNoonScheduleCalendar calendar;
    private final NoonOrderReportSchedulePolicy orderPolicy;
    private final NoonOrderBackfillPlanner orderBackfillPlanner;
    private final NoonSalesRetentionPolicy salesRetentionPolicy;

    LegacyNoonSchedulePlanner(
            LegacyNoonScheduleCalendar calendar,
            NoonOrderReportSchedulePolicy orderPolicy,
            NoonOrderBackfillPlanner orderBackfillPlanner,
            NoonSalesRetentionPolicy salesRetentionPolicy
    ) {
        this.calendar = calendar;
        this.orderPolicy = orderPolicy;
        this.orderBackfillPlanner = orderBackfillPlanner;
        this.salesRetentionPolicy = salesRetentionPolicy;
    }

    List<NoonPullTaskDraft> tasksFor(NoonPullPlanRecord plan) {
        List<NoonPullTaskDraft> tasks = new ArrayList<>();
        if (scheduled(plan, NoonPullDataDomain.PRODUCT, null)) {
            LocalDate date = calendar.currentDate();
            tasks.add(draft(plan, "product-list:" + date + ".." + date, date, date));
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.SALES, null)) {
            addScheduledSales(plan, tasks);
            return tasks;
        }
        if (correction(plan, NoonPullDataDomain.SALES)) {
            addSalesCorrection(plan, tasks);
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.ORDER, null)) {
            NoonOrderDailyPullPlan daily = orderPolicy.dailyPlan(
                    plan.getOwnerUserId(), plan.getStoreCode(), plan.getSiteCode()
            );
            if (daily.isDue()) {
                LocalDate date = daily.getTargetDate();
                tasks.add(draft(plan, "orders:" + date + ".." + date, date, date));
            }
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.FINANCE_TRANSACTION, NoonPullType.REPORT)) {
            addRollingReport(plan, tasks, calendar.financeReady(),
                    "finance-transactions:", 6);
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.NOON_ADVERTISING, NoonPullType.REPORT)) {
            addDailyReport(plan, tasks, calendar.advertisingReady(), "ads:");
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                NoonPullType.INTERFACE)) {
            if (calendar.inventoryReady()) {
                LocalDate date = calendar.currentDate();
                tasks.add(draft(plan, "official-warehouse-inventory:" + date, date, date));
            }
            return tasks;
        }
        if (scheduled(plan, NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                NoonPullType.REPORT)) {
            addDailyReport(plan, tasks, calendar.fbnReceivedReady(),
                    "official-warehouse-fbn-received:");
            return tasks;
        }
        if (backfill(plan, NoonPullDataDomain.ORDER)) {
            addOrderBackfill(plan, tasks);
        }
        return tasks;
    }

    private void addScheduledSales(
            NoonPullPlanRecord plan,
            List<NoonPullTaskDraft> tasks
    ) {
        if (!calendar.salesReady()) {
            return;
        }
        LocalDate date = calendar.latestAvailableDate();
        if (plan.getPullType() == NoonPullType.PAGE_QUERY) {
            tasks.add(draft(plan, "sales-page-query:" + date + ".." + date, date, date));
        } else if (plan.getPullType() == NoonPullType.REPORT
                && calendar.salesLatestDayReady()) {
            LocalDate from = date.minusDays(29);
            tasks.add(draft(plan, "sales:" + from + ".." + date, from, date));
        }
    }

    private void addSalesCorrection(
            NoonPullPlanRecord plan,
            List<NoonPullTaskDraft> tasks
    ) {
        if (!calendar.salesReady()) {
            return;
        }
        LocalDate to = calendar.latestAvailableDate();
        LocalDate from = salesRetentionPolicy.weeklyCorrection(to).getDateFrom();
        tasks.add(draft(plan, "sales-correction:" + from + ".." + to, from, to));
    }

    private void addRollingReport(
            NoonPullPlanRecord plan,
            List<NoonPullTaskDraft> tasks,
            boolean ready,
            String prefix,
            int priorDays
    ) {
        if (!ready) {
            return;
        }
        LocalDate to = calendar.latestAvailableDate();
        LocalDate from = to.minusDays(priorDays);
        tasks.add(draft(plan, prefix + from + ".." + to, from, to));
    }

    private void addDailyReport(
            NoonPullPlanRecord plan,
            List<NoonPullTaskDraft> tasks,
            boolean ready,
            String prefix
    ) {
        if (!ready) {
            return;
        }
        LocalDate date = calendar.latestAvailableDate();
        tasks.add(draft(plan, prefix + date + ".." + date, date, date));
    }

    private void addOrderBackfill(
            NoonPullPlanRecord plan,
            List<NoonPullTaskDraft> tasks
    ) {
        BackfillSchedule schedule = BackfillSchedule.parse(plan.getScheduleExpression());
        if (schedule == null) {
            return;
        }
        NoonOrderBackfillPlan backfill = orderBackfillPlanner.plan(
                schedule.dateFrom, schedule.dateTo,
                schedule.maxDaysPerWindow, schedule.maxWindowsPerRun
        );
        for (NoonOrderBackfillPlan.Window window : backfill.getWindows()) {
            tasks.add(draft(plan,
                    "orders:" + window.getDateFrom() + ".." + window.getDateTo(),
                    window.getDateFrom(), window.getDateTo()));
        }
    }

    private NoonPullTaskDraft draft(
            NoonPullPlanRecord plan,
            String identity,
            LocalDate from,
            LocalDate to
    ) {
        return NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .pullType(plan.getPullType())
                .dataDomain(plan.getDataDomain())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity(identity)
                .targetDateFrom(from)
                .targetDateTo(to)
                .build();
    }

    private boolean scheduled(
            NoonPullPlanRecord plan,
            NoonPullDataDomain domain,
            NoonPullType type
    ) {
        return plan.getDataDomain() == domain
                && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY
                && (type == null || plan.getPullType() == type);
    }

    private boolean correction(NoonPullPlanRecord plan, NoonPullDataDomain domain) {
        return plan.getDataDomain() == domain
                && plan.getTriggerMode() == NoonPullTriggerMode.LOW_FREQUENCY_CORRECTION;
    }

    private boolean backfill(NoonPullPlanRecord plan, NoonPullDataDomain domain) {
        return plan.getDataDomain() == domain
                && plan.getTriggerMode() == NoonPullTriggerMode.GAP_BACKFILL;
    }

    private static final class BackfillSchedule {
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final int maxDaysPerWindow;
        private final int maxWindowsPerRun;

        private BackfillSchedule(
                LocalDate dateFrom,
                LocalDate dateTo,
                int maxDaysPerWindow,
                int maxWindowsPerRun
        ) {
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
            for (int index = 1; index < parts.length; index++) {
                String[] pair = parts[index].split("=", 2);
                if (pair.length == 2) {
                    options.put(pair[0], pair[1]);
                }
            }
            return new BackfillSchedule(
                    LocalDate.parse(range[0]), LocalDate.parse(range[1]),
                    intOption(options, "maxDays", 7),
                    intOption(options, "maxWindows", 1)
            );
        }

        private static int intOption(
                Map<String, String> options,
                String key,
                int fallback
        ) {
            try {
                return Integer.parseInt(options.getOrDefault(key, String.valueOf(fallback)));
            } catch (NumberFormatException invalid) {
                return fallback;
            }
        }
    }
}
