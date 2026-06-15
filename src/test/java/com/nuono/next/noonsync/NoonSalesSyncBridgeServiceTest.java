package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.sales.SalesListingCoverageMode;
import com.nuono.next.sales.SalesSyncTaskCommand;
import com.nuono.next.sales.SalesSyncTaskRecord;
import com.nuono.next.sales.SalesSyncTaskService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonSalesSyncBridgeServiceTest {

    @Test
    void noDataBackfillUsesConfirmedEmptySiteCoverageMode() {
        SalesSyncTaskService salesSyncTaskService = mock(SalesSyncTaskService.class);
        when(salesSyncTaskService.triggerAndRun(any()))
                .thenReturn(emptySalesTask());
        NoonSalesSyncBridgeService service = new NoonSalesSyncBridgeService(
                new NoonSyncFoundationService(),
                salesSyncTaskService
        );

        service.runHistoricalBackfill(new NoonSalesBackfillSyncCommand(
                NoonSyncScope.of(307L, 245027L, "STR245027-NSA", "SA"),
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 24),
                NoonSalesBackfillReason.NO_DATA_SCOPE,
                10003L
        ));

        ArgumentCaptor<SalesSyncTaskCommand> captor = ArgumentCaptor.forClass(SalesSyncTaskCommand.class);
        verify(salesSyncTaskService).triggerAndRun(captor.capture());
        assertEquals(SalesListingCoverageMode.CONFIRMED_EMPTY_SITE, captor.getValue().getListingCoverageMode());
        assertEquals("no_data_backfill", captor.getValue().getTriggerType());
    }

    private static SalesSyncTaskRecord emptySalesTask() {
        return new SalesSyncTaskRecord(
                9001L,
                307L,
                245027L,
                "STR245027-NSA",
                "SA",
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 24),
                10003L,
                "no_data_backfill",
                "empty",
                10012L,
                0,
                0,
                0,
                null,
                "empty"
        );
    }
}
