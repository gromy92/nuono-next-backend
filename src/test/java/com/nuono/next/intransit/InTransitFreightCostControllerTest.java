package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardVersionCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.BatchFreightCostView;
import com.nuono.next.intransit.InTransitFreightCostRecords.ForwarderFreightComparisonView;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.intransit.InTransitFreightCostRecords.SkuFreightCostHistoryView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitFreightCostControllerTest {

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitFreightCostService freightCostService;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    private InTransitFreightCostController controller;

    @BeforeEach
    void setUp() {
        controller = new InTransitFreightCostController(
                batchService,
                freightCostService,
                accessScopeService
        );
    }

    @Test
    void shouldReadBatchFreightCostsAfterBatchAccessCheck() {
        BusinessAccessContext context = context();
        BatchView scopedBatch = scopedBatch();
        BatchFreightCostView costView = new BatchFreightCostView();

        when(batchService.getBatch(10002L, 53001L)).thenReturn(scopedBatch);
        when(freightCostService.batchActualCosts(10002L, 53001L)).thenReturn(costView);

        BatchFreightCostView result = controller.batchFreightCosts(53001L, context);

        assertEquals(costView, result);
        verify(accessScopeService).requireBatchAccess(context, scopedBatch);
    }

    @Test
    void shouldReadFreightStatisticsUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        FreightStatisticsView statisticsView = new FreightStatisticsView();

        when(freightCostService.statistics(eq(10002L), eq(null), eq(null), eq(30700002L)))
                .thenReturn(statisticsView);

        FreightStatisticsView result = controller.freightStatistics(null, null, 30700002L, context);

        assertEquals(statisticsView, result);
    }

    @Test
    void shouldReadSkuFreightHistoryUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        SkuFreightCostHistoryView historyView = new SkuFreightCostHistoryView();

        when(freightCostService.skuHistory(eq(10002L), eq("SGGRB148"), eq("SA"), eq(null), eq(null)))
                .thenReturn(historyView);

        SkuFreightCostHistoryView result = controller.skuFreightHistory("SGGRB148", "SA", null, null, context);

        assertEquals(historyView, result);
    }

    @Test
    void shouldReadForwarderComparisonUsingBackendOwnerContext() {
        BusinessAccessContext context = context();
        ForwarderFreightComparisonView comparisonView = new ForwarderFreightComparisonView();

        when(freightCostService.forwarderComparison(eq(10002L), eq("SGGRB148"), eq("SA"), eq("SEA"), eq("RUH")))
                .thenReturn(comparisonView);

        ForwarderFreightComparisonView result = controller.forwarderFreightComparison(
                "SGGRB148",
                "SA",
                "SEA",
                "RUH",
                context
        );

        assertEquals(comparisonView, result);
    }

    @Test
    void shouldOverwriteOwnerOperatorWhenSavingRateCardVersion() {
        BusinessAccessContext context = context();
        SaveRateCardVersionCommand command = new SaveRateCardVersionCommand();
        command.setOwnerUserId(1L);
        command.setOperatorUserId(2L);
        command.setForwarderCode("YITONG");
        RateCardVersionView saved = new RateCardVersionView();
        saved.setRateCardVersionId(64001L);

        ArgumentCaptor<SaveRateCardVersionCommand> captor = ArgumentCaptor.forClass(SaveRateCardVersionCommand.class);
        when(freightCostService.saveRateCardVersion(captor.capture())).thenReturn(saved);

        RateCardVersionView result = controller.saveFreightRateCardVersion(command, context);

        assertEquals(64001L, result.getRateCardVersionId());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(90001L, captor.getValue().getOperatorUserId());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .menuPaths(Set.of("/purchase/in-transit-goods"))
                .build();
    }

    private BatchView scopedBatch() {
        BatchView batch = new BatchView();
        batch.setBatchId(53001L);
        batch.setTargetStoreCode("STR245027-NAE");
        batch.setTargetSiteCode("AE");
        return batch;
    }
}
