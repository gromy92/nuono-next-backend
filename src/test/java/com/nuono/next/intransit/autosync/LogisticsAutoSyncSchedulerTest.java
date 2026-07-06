package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncSchedulerTest {

    @Mock
    private LogisticsAutoSyncMapper mapper;
    @Mock
    private LogisticsAutoSyncService service;

    @Test
    void disabledSchedulerReturnsZeroAndDoesNotLoadAccounts() {
        LogisticsAutoSyncScheduler scheduler = scheduler(false, 5);

        int count = scheduler.runOnce();

        assertThat(count).isZero();
        verify(mapper, never()).listDueAccounts(5);
    }

    @Test
    void enabledSchedulerSkipsWhenAlreadyRunning() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 5);
        markRunning(scheduler);

        int count = scheduler.runOnce();

        assertThat(count).isZero();
        verify(mapper, never()).listDueAccounts(5);
    }

    @Test
    void enabledSchedulerRunsAtMostConfiguredAccounts() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 2);
        LogisticsAutoSyncAccount first = account(180001L);
        LogisticsAutoSyncAccount second = account(180002L);
        LogisticsAutoSyncAccount third = account(180003L);
        when(mapper.listDueAccounts(2)).thenReturn(List.of(first, second, third));

        int count = scheduler.runOnce();

        assertThat(count).isEqualTo(2);
        verify(service).runAccount(first);
        verify(service).runAccount(second);
        verify(service, never()).runAccount(third);
    }

    @Test
    void schedulerSkipsAccountsOutsideWindowOrCooldown() {
        LogisticsAutoSyncScheduler scheduler = scheduler(true, 10);
        LogisticsAutoSyncAccount outsideWindow = account(180001L);
        outsideWindow.setScheduleWindowStart(LocalTime.of(10, 0));
        outsideWindow.setScheduleWindowEnd(LocalTime.of(11, 0));
        LogisticsAutoSyncAccount coolingDown = account(180002L);
        coolingDown.setCooldownUntil(LocalDateTime.of(2026, 7, 6, 13, 0));
        LogisticsAutoSyncAccount runnable = account(180003L);
        when(mapper.listDueAccounts(10)).thenReturn(List.of(outsideWindow, coolingDown, runnable));

        int count = scheduler.runOnce();

        assertThat(count).isEqualTo(1);
        verify(service, never()).runAccount(outsideWindow);
        verify(service, never()).runAccount(coolingDown);
        verify(service).runAccount(runnable);
    }

    @Test
    void runOnceIsPackagePrivateNotPublicApi() throws Exception {
        assertThat(Modifier.isPublic(LogisticsAutoSyncScheduler.class.getDeclaredMethod("runOnce").getModifiers()))
                .isFalse();
    }

    private LogisticsAutoSyncScheduler scheduler(boolean enabled, int maxAccountsPerTick) {
        LogisticsAutoSyncProperties properties = new LogisticsAutoSyncProperties();
        properties.getScheduler().setEnabled(enabled);
        properties.getScheduler().setMaxAccountsPerTick(maxAccountsPerTick);
        return new LogisticsAutoSyncScheduler(
                properties,
                mapper,
                service,
                Clock.fixed(Instant.parse("2026-07-06T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
    }

    private static LogisticsAutoSyncAccount account(Long id) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(id);
        account.setEnabled(true);
        account.setScheduleEnabled(true);
        return account;
    }

    private static void markRunning(LogisticsAutoSyncScheduler scheduler) {
        try {
            java.lang.reflect.Field field = LogisticsAutoSyncScheduler.class.getDeclaredField("running");
            field.setAccessible(true);
            ((AtomicBoolean) field.get(scheduler)).set(true);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
