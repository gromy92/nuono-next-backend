package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NoonPullAuthWaitingTaskHandlerTest {
    private final NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
    private final NoonPullAuthWaitingTaskHandler handler = new NoonPullAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 12, 20);

    @Test
    void recoveredProjectRequeuesOnlyTheBlockedSourceTaskUnderTheRecoveryFence() {
        NoonAuthRecoveryItemRecord item = item("ORDERS");
        when(mapper.requeueBlockedTaskAfterRecoveryCas(
                71001L, 991L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 8L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 8L, "lease", now)
        );
    }

    @Test
    void failedAuthorizationMovesOnlyTheBlockedSourceTaskToManualReview() {
        NoonAuthRecoveryItemRecord item = item("NOON_PULL");
        when(mapper.failBlockedTaskAfterRecovery(
                71001L,
                991L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                8L,
                "lease",
                "OTP_INVALID",
                "otp invalid",
                now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.MANUAL_REVIEW,
                handler.fail(
                        item,
                        NoonAuthRecoveryStatus.RECOVERING_PULLS,
                        8L,
                        "lease",
                        "OTP_INVALID",
                        "otp invalid",
                        now
                )
        );
        verify(mapper).failBlockedTaskAfterRecovery(
                71001L,
                991L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                8L,
                "lease",
                "OTP_INVALID",
                "otp invalid",
                now
        );
    }

    private NoonAuthRecoveryItemRecord item(String sourceDomain) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(601L);
        item.setRecoveryId(991L);
        item.setSourceDomain(sourceDomain);
        item.setSourceTaskId(71001L);
        return item;
    }
}
