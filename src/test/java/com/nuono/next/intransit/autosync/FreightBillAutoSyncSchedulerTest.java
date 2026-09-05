package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreightBillAutoSyncSchedulerTest {
    @Mock private LogisticsAutoSyncMapper mapper;
    @Mock private FreightBillAutoSyncService service;

    @Test
    void schedulerDefaultsOffAndDoesNotQueryAccounts() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        FreightBillAutoSyncScheduler scheduler = scheduler(properties);

        assertThat(scheduler.runOnce()).isZero();
        verify(mapper, never()).listDueFreightBillAccounts(5);
    }

    @Test
    void schedulerRunsOnlyAccountWithIndependentCostScheduleSwitch() {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.getFreightBillScheduler().setEnabled(true);
        LogisticsAutoSyncAccount disabled = account(false);
        LogisticsAutoSyncAccount enabled = account(true);
        when(mapper.listDueFreightBillAccounts(5)).thenReturn(List.of(disabled, enabled));

        int executed = scheduler(properties).runOnce();

        assertThat(executed).isEqualTo(1);
        verify(service, never()).runAccount(disabled);
        verify(service).runAccount(enabled);
    }

    private FreightBillAutoSyncScheduler scheduler(LogisticsAutoSyncProperties properties) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        return new FreightBillAutoSyncScheduler(properties, mapper, service, clock);
    }

    private static LogisticsAutoSyncAccount account(boolean costScheduleEnabled) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(costScheduleEnabled ? 180002L : 180001L);
        account.setEnabled(true);
        account.setScheduleEnabled(false);
        account.setFreightBillScheduleEnabled(costScheduleEnabled);
        account.setScheduleWindowStart(LocalTime.of(0, 0));
        account.setScheduleWindowEnd(LocalTime.of(23, 59));
        return account;
    }
}
