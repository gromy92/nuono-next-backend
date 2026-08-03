package com.nuono.next.officialwarehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthOfficialWarehouseTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAuthWaitingTaskHandlerTest {
    private final NoonAuthOfficialWarehouseTaskMapper mapper = mock(NoonAuthOfficialWarehouseTaskMapper.class);
    private final OfficialWarehouseAuthWaitingTaskHandler handler =
            new OfficialWarehouseAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 12, 20);

    @Test
    void authorizationRecoveryMakesOnlyTheWaitingAppointmentDue() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.resumeAfterAuthorization(
                603L, 993L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 10L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 10L, "lease", now)
        );
    }

    @Test
    void failedAuthorizationLeavesTheAppointmentForManualReview() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.failAuthorizationRecovery(
                603L,
                993L,
                NoonAuthRecoveryStatus.RECOVERING_PULLS,
                10L,
                "lease",
                "otp invalid",
                now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.MANUAL_REVIEW,
                handler.fail(
                        item,
                        NoonAuthRecoveryStatus.RECOVERING_PULLS,
                        10L,
                        "lease",
                        "OTP_INVALID",
                        "otp invalid",
                        now
                )
        );
    }

    private NoonAuthRecoveryItemRecord item() {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(603L);
        item.setRecoveryId(993L);
        item.setSourceDomain("OFFICIAL_WAREHOUSE_APPOINTMENT");
        item.setSourceTaskId(611402L);
        return item;
    }
}
