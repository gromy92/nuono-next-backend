package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseAsnListPullService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class NoonPullRetryExecutorTest {

    @Test
    @SuppressWarnings("unchecked")
    void recordsSuccessfulScheduledAsnExecution() {
        NoonPullFoundationService foundationService = mock(NoonPullFoundationService.class);
        NoonPullRetryCoordinator coordinator = mock(NoonPullRetryCoordinator.class);
        OfficialWarehouseAsnListPullService asnService = mock(OfficialWarehouseAsnListPullService.class);
        ObjectProvider<OfficialWarehouseAsnListPullService> provider = mock(ObjectProvider.class);
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(42L);
        when(provider.getIfAvailable()).thenReturn(asnService);
        when(asnService.executeScheduled(task)).thenReturn(NoonPullTaskStatus.SUCCEEDED);
        NoonPullRetryExecutor executor = new NoonPullRetryExecutor(
                foundationService,
                coordinator,
                provider
        );
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();

        executor.executeAsn(task, result);

        assertThat(result.getExecutedTaskCount()).isEqualTo(1);
        assertThat(result.getFailedTaskCount()).isZero();
    }
}
