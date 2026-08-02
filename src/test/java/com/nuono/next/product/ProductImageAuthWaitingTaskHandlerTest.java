package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductImageAuthWaitingTaskHandlerTest {
    private final ProductImageProfileMapper mapper = mock(ProductImageProfileMapper.class);
    private final ProductImageAuthWaitingTaskHandler handler =
            new ProductImageAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 12, 20);

    @Test
    void recoveredAuthorizationNeverBlindlyReplaysAnImagePublish() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.markSuiteAuthorizationRecovered(
                604L, 994L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.MANUAL_REVIEW,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lease", now)
        );
    }

    @Test
    void staleImageAttemptCannotChangeTheCurrentSuite() {
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item(), NoonAuthRecoveryStatus.RECOVERING_PULLS, 11L, "lost", now)
        );
    }

    private NoonAuthRecoveryItemRecord item() {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(604L);
        item.setRecoveryId(994L);
        item.setSourceDomain("PRODUCT_IMAGE_SUITE");
        item.setSourceTaskId(72004L);
        item.setSourceCheckpoint("attempt-01");
        return item;
    }
}
