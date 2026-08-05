package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyNoonSchedulePlannerTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void preservesDailyWindowsAndTaskIdentitiesAcrossDomains() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T15:45:00Z"), SHANGHAI);
        LegacyNoonSchedulePlanner planner = planner(clock);

        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.PRODUCT,
                        NoonPullType.INTERFACE, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "product-list:2026-08-04..2026-08-04",
                "2026-08-04", "2026-08-04"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.SALES,
                        NoonPullType.PAGE_QUERY, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "sales-page-query:2026-08-03..2026-08-03",
                "2026-08-03", "2026-08-03"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.SALES,
                        NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "sales:2026-07-05..2026-08-03",
                "2026-07-05", "2026-08-03"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.FINANCE_TRANSACTION,
                        NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "finance-transactions:2026-07-28..2026-08-03",
                "2026-07-28", "2026-08-03"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.NOON_ADVERTISING,
                        NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "ads:2026-08-03..2026-08-03",
                "2026-08-03", "2026-08-03"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                        NoonPullType.INTERFACE, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "official-warehouse-inventory:2026-08-04",
                "2026-08-04", "2026-08-04"
        );
        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                        NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)),
                "official-warehouse-fbn-received:2026-08-03..2026-08-03",
                "2026-08-03", "2026-08-03"
        );
    }

    @Test
    void preservesSalesCorrectionAndBoundedOrderBackfill() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-04T15:45:00Z"), SHANGHAI);
        LegacyNoonSchedulePlanner planner = planner(clock);

        assertDraft(
                planner.tasksFor(plan(NoonPullDataDomain.SALES,
                        NoonPullType.REPORT,
                        NoonPullTriggerMode.LOW_FREQUENCY_CORRECTION)),
                "sales-correction:2026-06-20..2026-08-03",
                "2026-06-20", "2026-08-03"
        );
        NoonPullPlanRecord backfill = plan(
                NoonPullDataDomain.ORDER, NoonPullType.REPORT,
                NoonPullTriggerMode.GAP_BACKFILL
        );
        backfill.setScheduleExpression(
                "backfill:2026-07-01..2026-07-20;maxDays=7;maxWindows=2"
        );
        List<NoonPullTaskDraft> drafts = planner.tasksFor(backfill);

        assertEquals(2, drafts.size());
        assertEquals("orders:2026-07-01..2026-07-07",
                drafts.get(0).getTargetIdentity());
        assertEquals("orders:2026-07-08..2026-07-14",
                drafts.get(1).getTargetIdentity());
    }

    @Test
    void respectsLateDailyReadinessWindows() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T23:15:00Z"), SHANGHAI);
        LegacyNoonSchedulePlanner planner = planner(clock);

        assertTrue(planner.tasksFor(plan(NoonPullDataDomain.SALES,
                NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)).isEmpty());
        assertTrue(planner.tasksFor(plan(NoonPullDataDomain.FINANCE_TRANSACTION,
                NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)).isEmpty());
        assertTrue(planner.tasksFor(plan(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                NoonPullType.INTERFACE, NoonPullTriggerMode.SCHEDULED_DAILY)).isEmpty());
        assertTrue(planner.tasksFor(plan(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                NoonPullType.REPORT, NoonPullTriggerMode.SCHEDULED_DAILY)).isEmpty());
    }

    private LegacyNoonSchedulePlanner planner(Clock clock) {
        return new LegacyNoonSchedulePlanner(
                new LegacyNoonScheduleCalendar(clock),
                new NoonOrderReportSchedulePolicy(clock),
                new NoonOrderBackfillPlanner(),
                new NoonSalesRetentionPolicy(clock)
        );
    }

    private NoonPullPlanRecord plan(
            NoonPullDataDomain domain,
            NoonPullType type,
            NoonPullTriggerMode triggerMode
    ) {
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setId(1L);
        plan.setOwnerUserId(307L);
        plan.setStoreCode("STR108065-NSA");
        plan.setSiteCode("SA");
        plan.setDataDomain(domain);
        plan.setPullType(type);
        plan.setTriggerMode(triggerMode);
        plan.setEnabled(true);
        return plan;
    }

    private void assertDraft(
            List<NoonPullTaskDraft> drafts,
            String identity,
            String dateFrom,
            String dateTo
    ) {
        assertEquals(1, drafts.size());
        NoonPullTaskDraft draft = drafts.get(0);
        assertEquals(identity, draft.getTargetIdentity());
        assertEquals(dateFrom, draft.getTargetDateFrom().toString());
        assertEquals(dateTo, draft.getTargetDateTo().toString());
    }
}
