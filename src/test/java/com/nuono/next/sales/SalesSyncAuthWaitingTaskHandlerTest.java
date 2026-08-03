package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.SalesSyncTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SalesSyncAuthWaitingTaskHandlerTest {
    private final SalesSyncTaskMapper mapper = mock(SalesSyncTaskMapper.class);
    private final SalesSyncAuthWaitingTaskHandler handler = new SalesSyncAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 14, 40);

    @Test
    void authorizationRecoveryQueuesTheExactSalesTask() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.resumeAfterAuthorization(
                621L, 995L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 12L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 12L, "lease", now)
        );
    }

    @Test
    void staleFenceDoesNotQueueAnotherSalesTask() {
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item(), NoonAuthRecoveryStatus.RECOVERING_PULLS, 12L, "lost", now)
        );
    }

    private NoonAuthRecoveryItemRecord item() {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(621L);
        item.setRecoveryId(995L);
        item.setSourceDomain("SALES_SYNC");
        item.setSourceTaskId(9001L);
        return item;
    }
}
