package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingAuthRecoveryMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductListingAuthWaitingTaskHandlerTest {
    private final ProductListingAuthRecoveryMapper mapper = mock(ProductListingAuthRecoveryMapper.class);
    private final ProductListingAuthWaitingTaskHandler handler =
            new ProductListingAuthWaitingTaskHandler(mapper);
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 12, 20);

    @Test
    void authorizationRecoveryMarksTheExactRealRunForSafeContinuation() {
        NoonAuthRecoveryItemRecord item = item();
        when(mapper.markTaskAuthorizationRecovered(
                602L, 992L, NoonAuthRecoveryStatus.RECOVERING_PULLS, 9L, "lease", now
        )).thenReturn(1);

        assertEquals(
                NoonAuthWaitingTaskOutcome.RESUMED,
                handler.resume(item, NoonAuthRecoveryStatus.RECOVERING_PULLS, 9L, "lease", now)
        );
    }

    @Test
    void staleListingFenceDoesNotResumeAnotherTask() {
        assertEquals(
                NoonAuthWaitingTaskOutcome.STALE,
                handler.resume(item(), NoonAuthRecoveryStatus.RECOVERING_PULLS, 9L, "lost", now)
        );
    }

    private NoonAuthRecoveryItemRecord item() {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(602L);
        item.setRecoveryId(992L);
        item.setSourceDomain("PRODUCT_LISTING");
        item.setSourceTaskId(88003L);
        return item;
    }
}

