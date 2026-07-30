package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class NoonPullSchedulerProviderAvailabilityWiringTest {

    @Test
    void productionConstructorUsesInjectedAvailabilityBeforeTaskCreation() {
        NoonPullFoundationService foundationService = mock(NoonPullFoundationService.class);
        NoonPullPlanRecord plan = new NoonPullPlanRecord();
        plan.setId(120001L);
        plan.setOwnerUserId(307L);
        plan.setStoreCode("STR108065-NSA");
        plan.setSiteCode("SA");
        plan.setPullType(NoonPullType.INTERFACE);
        plan.setDataDomain(NoonPullDataDomain.PRODUCT);
        plan.setTriggerMode(NoonPullTriggerMode.SCHEDULED_DAILY);
        plan.setEnabled(true);
        plan.setPaused(false);
        when(foundationService.listPlans()).thenReturn(List.of(plan));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("providerAvailability", (NoonProviderAvailability) candidate -> false);
        NoonPullScheduler scheduler = new NoonPullScheduler(
                foundationService,
                120,
                30,
                beans.getBeanProvider(NoonProviderAvailability.class)
        );

        NoonPullSchedulerResult result = scheduler.runDuePlans();

        assertEquals(0, result.getCreatedTaskCount());
        assertEquals(1, result.getSkippedPlanCount());
        verify(foundationService, never()).createTaskForPlan(anyLong(), any(NoonPullTaskDraft.class));
    }
}
