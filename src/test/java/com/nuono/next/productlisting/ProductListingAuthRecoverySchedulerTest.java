package com.nuono.next.productlisting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductListingAuthRecoverySchedulerTest {
    @Test
    void schedulerChecksAuthBlockedTasksBeforeRunningTheNormalQueue() {
        ProductListingService service = mock(ProductListingService.class);
        ProductListingAuthRecoveryCoordinator coordinator =
                mock(ProductListingAuthRecoveryCoordinator.class);
        ProductListingRealRunTaskScheduler scheduler =
                new ProductListingRealRunTaskScheduler(service);
        scheduler.setAuthRecoveryCoordinator(coordinator);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        ReflectionTestUtils.setField(scheduler, "maxItemsPerTick", 2);
        ReflectionTestUtils.setField(scheduler, "staleRunningMinutes", 30L);
        when(service.recoverStaleRunningRealRunTasks(any(Duration.class))).thenReturn(0);
        when(coordinator.resumePendingTasks(2)).thenReturn(1);
        when(service.executeRunnableRealRunTasks(2)).thenReturn(List.of());

        scheduler.runRealRunTaskScheduler();

        verify(coordinator).resumePendingTasks(2);
        verify(service).executeRunnableRealRunTasks(2);
    }
}
