package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Ali1688HistoricalOrderWeeklySyncSchedulerTest {

    @Mock
    private Ali1688HistoricalOrderMapper mapper;

    @Mock
    private LocalDbAli1688HistoricalOrderService service;

    @Test
    void disabledSchedulerDoesNothing() {
        Ali1688HistoricalOrderWeeklySyncScheduler scheduler = scheduler(false);

        int submitted = scheduler.runOnce();

        assertThat(submitted).isZero();
        verifyNoInteractions(mapper, service);
    }

    @Test
    void runsDueOpenApiAuthorizationsAsScheduledWeekly() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization(91004L, 307L);
        Ali1688HistoricalOrderWeeklySyncScheduler scheduler = scheduler(true);

        when(mapper.listScheduledOpenApiAuthorizations("ALI1688_OPEN_API", 10)).thenReturn(List.of(authorization));
        when(mapper.countRecentRunningSyncTasks(307L, 91004L, 120)).thenReturn(0);
        when(mapper.countRecentSyncTasksByType(307L, 91004L, "scheduled_weekly", 6)).thenReturn(0);
        when(service.runScheduledWeekly(307L, 91004L, 10003L)).thenReturn(new Ali1688HistoricalOrderSyncTaskRow());

        int submitted = scheduler.runOnce();

        assertThat(submitted).isEqualTo(1);
        verify(service).runScheduledWeekly(307L, 91004L, 10003L);
    }

    @Test
    void skipsRunningOrRecentlyScheduledAuthorizations() {
        Ali1688HistoricalOrderAuthorizationRow running = authorization(91004L, 307L);
        Ali1688HistoricalOrderAuthorizationRow recent = authorization(91005L, 408L);
        Ali1688HistoricalOrderWeeklySyncScheduler scheduler = scheduler(true);

        when(mapper.listScheduledOpenApiAuthorizations("ALI1688_OPEN_API", 10)).thenReturn(List.of(running, recent));
        when(mapper.countRecentRunningSyncTasks(307L, 91004L, 120)).thenReturn(1);
        when(mapper.countRecentRunningSyncTasks(408L, 91005L, 120)).thenReturn(0);
        when(mapper.countRecentSyncTasksByType(408L, 91005L, "scheduled_weekly", 6)).thenReturn(1);

        int submitted = scheduler.runOnce();

        assertThat(submitted).isZero();
        verify(service, never()).runScheduledWeekly(307L, 91004L, 10003L);
        verify(service, never()).runScheduledWeekly(408L, 91005L, 10003L);
    }

    private Ali1688HistoricalOrderWeeklySyncScheduler scheduler(boolean enabled) {
        return new Ali1688HistoricalOrderWeeklySyncScheduler(
                mapper,
                service,
                enabled,
                10,
                6,
                120,
                10003L
        );
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization(Long id, Long ownerUserId) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(id);
        row.setOwnerUserId(ownerUserId);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setStatus("authorized");
        return row;
    }
}
