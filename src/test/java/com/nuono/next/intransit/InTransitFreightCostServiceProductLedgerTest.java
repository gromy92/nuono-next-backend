package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.InTransitFreightCostMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightBillRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightComponentRow;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitFreightCostServiceProductLedgerTest {

    @Mock
    private InTransitGoodsMapper goodsMapper;

    @Mock
    private InTransitFreightCostMapper freightCostMapper;

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    private InTransitFreightCostService service;

    @BeforeEach
    void setUp() {
        service = new InTransitFreightCostService(
                goodsMapper,
                freightCostMapper,
                batchService,
                accessScopeService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldStoreActualFreightBillWithoutWritingProductLedger() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .businessOwnerUserId(307L)
                .sessionUserId(90001L)
                .build();
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(90001L);
        command.setAccessContext(context);
        command.setSourceSystem("YITE");

        ActualFreightBillCommand bill = new ActualFreightBillCommand();
        bill.setBatchReferenceNo("YT20260630001");
        bill.setBillNo("YITE-BILL-001");
        bill.setCurrencyCode("CNY");
        bill.setExchangeRateToCny(BigDecimal.ONE);
        bill.setOriginalTotalAmount(new BigDecimal("88.50"));
        bill.setCnyTotalAmount(new BigDecimal("88.50"));

        ActualFreightComponentCommand component = new ActualFreightComponentCommand();
        component.setBoxNo("BOX-001");
        component.setPsku("PSKU-001");
        component.setRawFeeName("头程运费");
        component.setStandardFeeType("headhaul");
        component.setChargeQuantity(new BigDecimal("1.500000"));
        component.setChargeUnit("KG");
        component.setCurrencyCode("CNY");
        component.setExchangeRateToCny(BigDecimal.ONE);
        component.setOriginalAmount(new BigDecimal("88.50"));
        component.setCnyAmount(new BigDecimal("88.50"));
        bill.setComponents(List.of(component));
        command.setBills(List.of(bill));

        BatchRow batch = new BatchRow();
        batch.setId(53069L);
        batch.setOwnerUserId(307L);
        batch.setBatchReferenceNo("YT20260630001");
        batch.setStandardForwarderId(30700003L);
        batch.setStandardForwarderCode("YITE");
        batch.setStandardForwarderName("义特");
        batch.setTransportMode("SEA");
        batch.setTargetStoreCode("DXB");
        batch.setTargetSiteCode("AE");
        BatchView batchView = BatchView.from(batch);

        when(goodsMapper.selectBatchByReferenceNo(307L, "YT20260630001")).thenReturn(batch);
        when(batchService.getBatch(307L, 53069L)).thenReturn(batchView);
        when(freightCostMapper.selectActualBillBySource(307L, "YITE", "YITE-BILL-001", 53069L)).thenReturn(null);
        when(freightCostMapper.nextActualBillId()).thenReturn(59001L);
        when(freightCostMapper.nextActualComponentId()).thenReturn(60001L);
        LineRow lineScope = new LineRow();
        lineScope.setId(54001L);
        lineScope.setStoreCode("STR108065-NSA");
        lineScope.setSiteCode("AE");
        when(freightCostMapper.selectActualComponentLineScopes(
                307L,
                53069L,
                null,
                "BOX-001",
                null,
                "PSKU-001"
        )).thenReturn(List.of(lineScope));

        service.syncActualCosts(command);

        ArgumentCaptor<ActualFreightBillRow> billRowCaptor = ArgumentCaptor.forClass(ActualFreightBillRow.class);
        ArgumentCaptor<ActualFreightComponentRow> componentRowCaptor = ArgumentCaptor.forClass(ActualFreightComponentRow.class);
        verify(freightCostMapper).insertActualBill(billRowCaptor.capture());
        verify(freightCostMapper).insertActualComponent(componentRowCaptor.capture());

        ActualFreightBillRow insertedBill = billRowCaptor.getValue();
        ActualFreightComponentRow insertedComponent = componentRowCaptor.getValue();
        assertEquals(59001L, insertedBill.getId());
        assertEquals("YT20260630001", insertedBill.getBatchReferenceNo());
        assertEquals("DXB", insertedBill.getDestinationCode());
        assertEquals("AE", insertedBill.getTargetSiteCode());
        assertEquals(60001L, insertedComponent.getId());
        assertEquals(insertedBill.getId(), insertedComponent.getActualBillId());
        assertEquals("DXB", insertedComponent.getDestinationCode());
        assertEquals("STR108065-NSA", insertedComponent.getStoreCode());
        assertEquals("AE", insertedComponent.getTargetSiteCode());
        assertEquals("PSKU-001", insertedComponent.getPsku());
        assertEquals("HEADHAUL", insertedComponent.getStandardFeeType());
        assertEquals("SEA", insertedComponent.getTransportMode());
        verify(accessScopeService).requireBatchAccess(same(context), same(batchView));
    }
}
