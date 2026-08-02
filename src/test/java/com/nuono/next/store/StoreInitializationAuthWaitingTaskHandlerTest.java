package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreInitializationSnapshotMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StoreInitializationAuthWaitingTaskHandlerTest {
    private final StoreInitializationSnapshotMapper mapper =
            mock(StoreInitializationSnapshotMapper.class);
    private final StoreInitializationAuthWaitingTaskHandler handler =
            new StoreInitializationAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 14, 30);

    @Test
    void authorizationRecoveryQueuesTheExactInitializationSnapshot() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.resumeAfterAuthorization(
                620L, 994L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lease", now)
        );
    }

    @Test
    void staleFenceDoesNotQueueAnotherInitialization() {
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item(), NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lost", now)
        );
    }

    private NoonAuthRecoveryItemRecord item() {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(620L);
        item.setRecoveryId(994L);
        item.setSourceDomain("STORE_INITIALIZATION");
        item.setSourceTaskId(40001L);
        return item;
    }
}
