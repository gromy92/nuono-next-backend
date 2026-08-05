package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyNoonPullTickRunnerTest {

    @Test
    void keepsLegacyPriorityAndPerTickDomainCaps() {
        NoonPullFoundationService foundation = new NoonPullFoundationService(
                new InMemoryNoonPullRepository(),
                Clock.fixed(Instant.parse("2026-08-04T16:00:00Z"), ZoneOffset.UTC)
        );
        List<String> executions = new ArrayList<>();
        LegacyNoonTaskExecutor recorder = new LegacyNoonTaskExecutor() {
            @Override
            public boolean accepts(NoonPullTaskRecord task) {
                return true;
            }

            @Override
            public void execute(
                    NoonPullTaskRecord task,
                    NoonPullScheduledExecutionResult result
            ) {
                executions.add(task.getDataDomain() + ":" + task.getId());
                result.executed();
            }
        };
        LegacyNoonPullTickRunner runner = new LegacyNoonPullTickRunner(
                foundation,
                new LegacyNoonPullTaskDispatcher(List.of(recorder), () -> null),
                2,
                1
        );
        NoonPullPlanRecord sales = createPlan(
                foundation, NoonPullDataDomain.SALES, NoonPullType.REPORT
        );
        NoonPullPlanRecord products = createPlan(
                foundation, NoonPullDataDomain.PRODUCT, NoonPullType.INTERFACE
        );
        createTask(foundation, sales, "sales:2026-08-01..2026-08-01", 1);
        createTask(foundation, sales, "sales:2026-08-02..2026-08-02", 2);
        createTask(foundation, sales, "sales:2026-08-03..2026-08-03", 3);
        createTask(foundation, products,
                "product-list:2026-08-03..2026-08-03", 3);
        createTask(foundation, products,
                "product-list:2026-08-04..2026-08-04", 4);
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();

        runner.execute(new NoonPullSchedulerResult(),
                StoreSiteMaintenanceGate.allowAll(), result);

        assertEquals(3, result.getExecutedTaskCount());
        assertEquals(2, result.getSkippedTaskCount());
        assertEquals(NoonPullDataDomain.PRODUCT + ":" + 1005L, executions.get(0));
        assertEquals(NoonPullDataDomain.SALES + ":" + 1002L, executions.get(1));
        assertEquals(NoonPullDataDomain.SALES + ":" + 1003L, executions.get(2));
    }

    private NoonPullPlanRecord createPlan(
            NoonPullFoundationService foundation,
            NoonPullDataDomain domain,
            NoonPullType pullType
    ) {
        return foundation.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(domain)
                .pullType(pullType)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .build());
    }

    private void createTask(
            NoonPullFoundationService foundation,
            NoonPullPlanRecord plan,
            String identity,
            int day
    ) {
        LocalDate date = LocalDate.of(2026, 8, day);
        foundation.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(plan.getOwnerUserId())
                .storeCode(plan.getStoreCode())
                .siteCode(plan.getSiteCode())
                .dataDomain(plan.getDataDomain())
                .pullType(plan.getPullType())
                .triggerMode(plan.getTriggerMode())
                .targetIdentity(identity)
                .targetDateFrom(date)
                .targetDateTo(date)
                .build()).orElseThrow();
    }
}
