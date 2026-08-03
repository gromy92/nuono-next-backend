package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthProductTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductAuthWaitingTaskHandlerTest {

    private final NoonAuthProductTaskMapper mapper = mock(NoonAuthProductTaskMapper.class);
    private final ProductAuthWaitingTaskHandler handler = new ProductAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 11, 30);

    @Test
    void safeDeleteCheckpointResumesTheSameTask() {
        NoonAuthRecoveryItemRecord item = item(NoonAuthResumePolicy.AUTO_RESUME);
        when(mapper.resumeSafeProductTask(
                601L, 991L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 8L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(
                        item,
                        NoonAuthRecoveryStatus.RECOVERING_PULLS,
                        8L,
                        "lease",
                        now
                )
        );
    }

    @Test
    void uncertainDeleteCheckpointRequiresReadbackAndNeverReplays() {
        NoonAuthRecoveryItemRecord item = item(NoonAuthResumePolicy.READBACK_REQUIRED);

        assertEquals(
                NoonAuthWaitingTaskOutcome.MANUAL_REVIEW,
                handler.resume(
                        item,
                        NoonAuthRecoveryStatus.RECOVERING_PULLS,
                        8L,
                        "lease",
                        now
                )
        );
        verify(mapper, never()).resumeSafeProductTask(
                601L, 991L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 8L, "lease", now
        );
    }

    private NoonAuthRecoveryItemRecord item(NoonAuthResumePolicy policy) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(601L);
        item.setRecoveryId(991L);
        item.setSourceDomain("PRODUCT_DELETE");
        item.setSourceTaskId(77001L);
        item.setSourceCheckpoint("retry_scheduled");
        item.setResumePolicy(policy);
        return item;
    }
}
