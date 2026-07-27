package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
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
        when(mapper.listDueScopes(Integer.MAX_VALUE, 1, 0)).thenReturn(List.of(scope));

        assertEquals(1, scheduler.runOnce());

        verify(syncService).submitScheduled(501L, "CANMAN", "SA");
    }

    @Test
    void enabledSchedulerSkipsMaintenanceScopes() {
        ProductPublicDetailSyncScheduler scheduler = new ProductPublicDetailSyncScheduler(mapper, syncService, true, 50, 1, 12);
        scheduler.setMaintenanceGate((ownerUserId, storeCode, siteCode) -> "MAINTAINED".equals(storeCode));
        ProductPublicDetailScope maintained = new ProductPublicDetailScope();
        maintained.setOwnerUserId(501L);
        maintained.setStoreCode("MAINTAINED");
        maintained.setSiteCode("SA");
        ProductPublicDetailScope active = new ProductPublicDetailScope();
        active.setOwnerUserId(501L);
        active.setStoreCode("ACTIVE");
        active.setSiteCode("AE");
        when(mapper.listDueScopes(Integer.MAX_VALUE, 1, 0)).thenReturn(List.of(maintained, active));

        assertEquals(1, scheduler.runOnce());

        verify(syncService, never()).submitScheduled(501L, "MAINTAINED", "SA");
        verify(syncService).submitScheduled(501L, "ACTIVE", "AE");
    }
}
