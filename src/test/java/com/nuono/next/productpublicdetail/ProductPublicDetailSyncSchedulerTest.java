package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublicDetailSyncSchedulerTest {
    @Mock
    private ProductPublicDetailMapper mapper;

    @Mock
    private ProductPublicDetailSyncService syncService;

    @Test
    void disabledSchedulerDoesNotSubmitScopes() {
        ProductPublicDetailSyncScheduler scheduler = new ProductPublicDetailSyncScheduler(mapper, syncService, false, 50, 1, 12);

        assertEquals(0, scheduler.runOnce());

        verifyNoInteractions(mapper, syncService);
    }

    @Test
    void enabledSchedulerSubmitsDueScopes() {
        ProductPublicDetailSyncScheduler scheduler = new ProductPublicDetailSyncScheduler(mapper, syncService, true, 50, 1, 12);
        ProductPublicDetailScope scope = new ProductPublicDetailScope();
        scope.setOwnerUserId(501L);
        scope.setStoreCode("CANMAN");
        scope.setSiteCode("SA");
        when(mapper.listDueScopes(50, 1, 12)).thenReturn(List.of(scope));

        assertEquals(1, scheduler.runOnce());

        verify(syncService).submitScheduled(501L, "CANMAN", "SA");
    }
}
