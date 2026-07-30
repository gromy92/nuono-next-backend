package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonpull.NoonProviderAvailability;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullTaskDraft;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class NoonGapPatrolProviderAvailabilityWiringTest {

    @Test
    void productionConstructorUsesInjectedAvailabilityBeforeTaskCreation() {
        NoonDataCompletenessRepository completenessRepository = mock(NoonDataCompletenessRepository.class);
        NoonPullFoundationService foundationService = mock(NoonPullFoundationService.class);
        NoonDataGapWindowRecord gap = gap();
        when(completenessRepository.listGapWindows(any(NoonDataGapQuery.class))).thenReturn(List.of(gap));
        when(foundationService.listPlans()).thenReturn(List.of());
        when(foundationService.createPlan(any())).thenReturn(plan());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("providerAvailability", (NoonProviderAvailability) candidate -> false);
        NoonGapPatrolPlanner planner = new NoonGapPatrolPlanner(
                completenessRepository,
                foundationService,
                beans.getBeanProvider(NoonProviderAvailability.class)
        );

        NoonGapPatrolPlanner.Result result = planner.planDueGaps(new NoonDataGapQuery(), 10);

        assertEquals(0, result.getPlannedTasks().size());
        assertEquals(1, result.getSkippedCount());
        verify(foundationService, never()).createTaskForPlan(anyLong(), any(NoonPullTaskDraft.class));
    }

    private NoonDataGapWindowRecord gap() {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(1L);
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NSA");
        gap.setSiteCode("SA");
        gap.setCategory(NoonDataCategory.SALES_ORDER);
        gap.setWindowType(NoonDataGapWindowType.LATEST_DAILY);
        gap.setStatus(NoonDataGapStatus.PENDING);
        gap.setDateFrom(LocalDate.parse("2026-07-29"));
        gap.setDateTo(LocalDate.parse("2026-07-29"));
        return gap;
    }

    private NoonPullPlanRecord plan() {
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setId(120001L);
        plan.setOwnerUserId(307L);
        plan.setStoreCode("STR108065-NSA");
        plan.setSiteCode("SA");
        plan.setPullType(NoonPullType.REPORT);
        plan.setDataDomain(NoonPullDataDomain.ORDER);
        plan.setTriggerMode(NoonPullTriggerMode.SCHEDULED_DAILY);
        plan.setEnabled(true);
        plan.setPaused(false);
        return plan;
    }
}
