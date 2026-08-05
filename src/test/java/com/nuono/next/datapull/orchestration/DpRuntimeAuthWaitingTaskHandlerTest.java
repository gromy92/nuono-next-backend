package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.DataPullAuthWaitingTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DpRuntimeAuthWaitingTaskHandlerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 5);
    private static final NoonAuthRecoveryStatus RECOVERY_STATUS =
            NoonAuthRecoveryStatus.RECOVERING_PULLS;

    @Test
    void ownsOnlyTheDpRuntimeSourceDomain() {
        DpRuntimeAuthWaitingTaskHandler handler = new DpRuntimeAuthWaitingTaskHandler(
                mock(DataPullAuthWaitingTaskMapper.class)
        );

        assertTrue(handler.supports("DP_RUNTIME"));
        assertTrue(handler.supports("dp_runtime"));
        assertFalse(handler.supports("SALES_SYNC"));
        assertFalse(handler.supports(null));
    }

    @Test
    void resumesOnlyWhenTheSingleJoinCasChangesTheTask() {
        DataPullAuthWaitingTaskMapper mapper = mock(DataPullAuthWaitingTaskMapper.class);
        DpRuntimeAuthWaitingTaskHandler handler = new DpRuntimeAuthWaitingTaskHandler(mapper);
        when(mapper.resumeAfterAuthorization(
                51L, 414L, 1L, 8L, "8", RECOVERY_STATUS, 12L, "lease-1", NOW
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        RECOVERY_STATUS, 12L, "lease-1", NOW)
        );
        verify(mapper).resumeAfterAuthorization(
                51L, 414L, 1L, 8L, "8", RECOVERY_STATUS, 12L, "lease-1", NOW
        );
    }

    @Test
    void returnsStaleWhenTheTaskVersionJoinCasDoesNotMatch() {
        DataPullAuthWaitingTaskMapper mapper = mock(DataPullAuthWaitingTaskMapper.class);
        DpRuntimeAuthWaitingTaskHandler handler = new DpRuntimeAuthWaitingTaskHandler(mapper);

        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        RECOVERY_STATUS, 12L, "lease-1", NOW)
        );
    }

    @Test
    void terminalRecoveryFailureLeavesTheOriginalTaskWaitingForAuthorization() {
        DataPullAuthWaitingTaskMapper mapper = mock(DataPullAuthWaitingTaskMapper.class);
        DpRuntimeAuthWaitingTaskHandler handler = new DpRuntimeAuthWaitingTaskHandler(mapper);
        NoonAuthRecoveryStatus failureStatus = NoonAuthRecoveryStatus.APPLYING_PROJECTS;
        when(mapper.holdAuthorizationManualReview(
                51L, 414L, 1L, 8L, "8", failureStatus, 12L, "lease-1",
                "AUTH_MANUAL_REVIEW", NOW
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.MANUAL_REVIEW,
                handler.fail(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        failureStatus, 12L, "lease-1", "OTP_FAILED", "sensitive", NOW)
        );
        verify(mapper).holdAuthorizationManualReview(
                51L, 414L, 1L, 8L, "8", failureStatus, 12L, "lease-1",
                "AUTH_MANUAL_REVIEW", NOW
        );
    }

    @Test
    void rejectsNonCanonicalOrNonAutoResumeCheckpointsBeforeSql() {
        DataPullAuthWaitingTaskMapper mapper = mock(DataPullAuthWaitingTaskMapper.class);
        DpRuntimeAuthWaitingTaskHandler handler = new DpRuntimeAuthWaitingTaskHandler(mapper);

        for (String checkpoint : new String[]{null, "", "+8", "08", "-1", "8 ",
                "9223372036854775808"}) {
            assertEquals(
                    NoonAuthWaitingTaskOutcome.STALE,
                    handler.resume(item(checkpoint, NoonAuthResumePolicy.AUTO_RESUME),
                            RECOVERY_STATUS, 12L, "lease-1", NOW)
            );
        }
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item("8", NoonAuthResumePolicy.READBACK_REQUIRED),
                        RECOVERY_STATUS, 12L, "lease-1", NOW)
        );
        NoonAuthRecoveryItemRecord wrongDomain = item("8", NoonAuthResumePolicy.AUTO_RESUME);
        wrongDomain.setSourceDomain("dp_runtime");
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(wrongDomain, RECOVERY_STATUS, 12L, "lease-1", NOW)
        );
        NoonAuthRecoveryItemRecord missingSite = item("8", NoonAuthResumePolicy.AUTO_RESUME);
        missingSite.setSiteCode(null);
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(missingSite, RECOVERY_STATUS, 12L, "lease-1", NOW)
        );
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        NoonAuthRecoveryStatus.APPLYING_PROJECTS, 12L, "lease-1", NOW)
        );
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.fail(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        NoonAuthRecoveryStatus.COMPLETED, 12L, "lease-1",
                        "OTP_FAILED", "safe", NOW)
        );
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.fail(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        null, 12L, "lease-1", "OTP_FAILED", "safe", NOW)
        );
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.fail(item("8", NoonAuthResumePolicy.AUTO_RESUME),
                        NoonAuthRecoveryStatus.APPLYING_PROJECTS, -1L, "lease-1",
                        "OTP_FAILED", "safe", NOW)
        );
        verifyNoInteractions(mapper);
    }

    private NoonAuthRecoveryItemRecord item(
            String checkpoint,
            NoonAuthResumePolicy resumePolicy
    ) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(51L);
        item.setRecoveryId(414L);
        item.setOwnerUserId(307L);
        item.setProjectCode("PRJ108065");
        item.setStoreCode("STR108065-NSA");
        item.setSiteCode("SA");
        item.setSourceDomain("DP_RUNTIME");
        item.setSourceTaskId(1L);
        item.setSourceCheckpoint(checkpoint);
        item.setResumePolicy(resumePolicy);
        return item;
    }
}
